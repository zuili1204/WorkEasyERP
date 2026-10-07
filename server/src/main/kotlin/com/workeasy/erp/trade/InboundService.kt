package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class LineItem(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal = BigDecimal.ZERO,
    val taxRate: BigDecimal = BigDecimal("0.13"),
    val batchNo: String? = null,
    val location: String? = null,
)

data class InboundCreateRequest(
    val orderId: String? = null,
    val supplierId: String = "",
    val warehouseId: String = "",
    val remark: String? = null,
    val items: List<LineItem> = emptyList(),
)

/**
 * 采购入库：审核时调 [InventoryService.inbound] 写库存流水并按移动加权更新成本，
 * 同时按价税合计生成应付台账 `ap_ledger`（单据 → 库存 → 财务）。
 */
@Service
class InboundService(
    private val jdbc: JdbcTemplate,
    private val inventoryService: InventoryService,
    private val orderService: OrderService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (h.inbound_no ILIKE ? OR h.supplier_name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND h.status = ? ")
            args.add(status)
        }
        val from = """
            FROM purchase_inbound h
            LEFT JOIN supplier s ON s.id = h.supplier_id
            LEFT JOIN warehouse w ON w.id = h.warehouse_id
            $cond
        """.trimIndent()
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT h.id, h.inbound_no, h.supplier_name, w.name AS warehouse_name, h.inbound_date,
                   h.status, h.total_qty, h.total_amount, h.created_at
            $from
            ORDER BY h.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(req: InboundCreateRequest): Map<String, Any?> {
        if (req.supplierId.isBlank() || req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "供应商与仓库不能为空")
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行不能为空")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("PIN")
        val supplierName = jdbc.queryForObject(
            "SELECT name FROM supplier WHERE id = ?::uuid", String::class.java, req.supplierId
        )

        var totalQty = BigDecimal.ZERO
        var totalAmount = BigDecimal.ZERO
        jdbc.update(
            """
            INSERT INTO purchase_inbound(id, inbound_no, order_id, supplier_id, supplier_name, warehouse_id, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?, ?::uuid, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.orderId, req.supplierId, supplierName, req.warehouseId, req.remark, me.userId.toString(),
        )
        req.items.forEach { line ->
            val name = jdbc.queryForObject(
                "SELECT name FROM product WHERE id = ?::uuid", String::class.java, line.productId
            )
            val unit = jdbc.queryForObject(
                "SELECT unit FROM product WHERE id = ?::uuid", String::class.java, line.productId
            )
            val amount = line.qty * line.price * (BigDecimal.ONE + line.taxRate)
            totalQty += line.qty
            totalAmount += amount
            jdbc.update(
                """
                INSERT INTO purchase_inbound_item(inbound_id, product_id, product_name, unit, qty, price, tax_rate, amount, batch_no, location)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, name, unit, line.qty, line.price, line.taxRate,
                amount.setScale(2, RoundingMode.HALF_UP), line.batchNo, line.location,
            )
        }
        jdbc.update(
            "UPDATE purchase_inbound SET total_qty = ?, total_amount = ? WHERE id = ?::uuid",
            totalQty, totalAmount.setScale(2, RoundingMode.HALF_UP), id.toString(),
        )
        audit.log("purchase", "创建采购入库单 $no", "purchase_inbound", id.toString())
        return mapOf("id" to id.toString(), "inboundNo" to no, "status" to "draft")
    }

    /** 审核：逐行入库（写流水 + 加权成本）→ 单据置 done → 生成应付台账 */
    @Transactional
    fun auditInbound(id: UUID) {
        val head = jdbc.queryForMap(
            "SELECT * FROM purchase_inbound WHERE id = ?::uuid FOR UPDATE", id.toString()
        )
        val status = head["status"]?.toString()
        if (status == "done" || status == "void") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "单据已审核或已作废")
        }
        val no = head["inbound_no"]?.toString() ?: id.toString()
        val warehouseId = head["warehouse_id"]?.toString()
            ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少仓库")
        val items = jdbc.queryForList(
            "SELECT * FROM purchase_inbound_item WHERE inbound_id = ?::uuid", id.toString()
        )
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行为空，无法审核")

        // 关联采购订单时，订单须已审批通过才能收货
        val orderId = head["order_id"]?.toString()
        if (orderId != null) {
            val st = jdbc.queryForObject(
                "SELECT status FROM purchase_order WHERE id = ?::uuid", String::class.java, orderId
            )
            if (st != "approved") {
                throw BizException(ErrorCode.BIZ_CONFLICT, "采购订单尚未审批通过（当前：$st），不能收货")
            }
        }

        val productQty = mutableMapOf<String, BigDecimal>()
        items.forEach { it ->
            val pid = it["product_id"].toString()
            val q = bd(it["qty"])
            productQty[pid] = (productQty[pid] ?: BigDecimal.ZERO) + q
            inventoryService.inbound(
                StockMoveRequest(
                    productId = pid,
                    warehouseId = warehouseId,
                    qty = q,
                    price = bd(it["price"]),
                    batchNo = it["batch_no"]?.toString(),
                    location = it["location"]?.toString(),
                    refType = "purchase",
                    refNo = no,
                    remark = "采购入库 $no",
                )
            )
        }

        jdbc.update("UPDATE purchase_inbound SET status = 'done' WHERE id = ?::uuid", id.toString())

        // 回写采购订单已收数量并推进订单状态（订单与履约分离）
        if (orderId != null) {
            orderService.applyInbound(UUID.fromString(orderId), productQty)
        }

        val amount = bd(head["total_amount"])
        val terms = jdbc.queryForObject(
            "SELECT COALESCE(payment_terms, 0) FROM supplier WHERE id = ?::uuid",
            Int::class.java,
            head["supplier_id"].toString(),
        ) ?: 0
        jdbc.update(
            """
            INSERT INTO ap_ledger(supplier_id, biz_type, biz_id, biz_no, amount, remain_amount, due_date, status)
            VALUES (?::uuid, 'purchase_inbound', ?::uuid, ?, ?, ?, CURRENT_DATE + ? , 'open')
            """.trimIndent(),
            head["supplier_id"].toString(),
            id.toString(),
            no,
            amount,
            amount,
            terms,
        )
        audit.log("purchase", "审核采购入库单 $no，生成应付 $amount", "purchase_inbound", id.toString())
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
