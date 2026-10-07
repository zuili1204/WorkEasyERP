package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class OutboundCreateRequest(
    val orderId: String? = null,
    val customerId: String = "",
    val warehouseId: String = "",
    val remark: String? = null,
    val items: List<LineItem> = emptyList(),
)

/**
 * 销售出库：审核时调 [InventoryService.outbound] 扣减库存（按加权成本计价），
 * 同时生成应收台账 `ar_ledger`，并占用客户授信（单据 → 库存 → 财务）。
 */
@Service
class OutboundService(
    private val jdbc: JdbcTemplate,
    private val inventoryService: InventoryService,
    private val orderService: OrderService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "sales_outbound")
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        when (eff) {
            "mine" -> {
                cond.append("AND h.customer_id IN (SELECT id FROM customer WHERE owner_id = ?::uuid) ")
                args.add(me.employeeId?.toString() ?: ZERO)
            }
            "dept" -> {
                cond.append("AND h.customer_id IN (SELECT id FROM customer WHERE dept_id = ?::uuid) ")
                args.add(me.deptId?.toString() ?: ZERO)
            }
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (h.outbound_no ILIKE ? OR h.customer_name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND h.status = ? ")
            args.add(status)
        }
        val from = """
            FROM sales_outbound h
            LEFT JOIN warehouse w ON w.id = h.warehouse_id
            $cond
        """.trimIndent()
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT h.id, h.outbound_no, h.customer_name, w.name AS warehouse_name, h.outbound_date,
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
    fun create(req: OutboundCreateRequest): Map<String, Any?> {
        if (req.customerId.isBlank() || req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "客户与仓库不能为空")
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行不能为空")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("SOUT")
        val customerName = jdbc.queryForObject(
            "SELECT name FROM customer WHERE id = ?::uuid", String::class.java, req.customerId
        )

        var totalQty = BigDecimal.ZERO
        var totalAmount = BigDecimal.ZERO
        jdbc.update(
            """
            INSERT INTO sales_outbound(id, outbound_no, order_id, customer_id, customer_name, warehouse_id, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?, ?::uuid, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.orderId, req.customerId, customerName, req.warehouseId, req.remark, me.userId.toString(),
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
                INSERT INTO sales_outbound_item(outbound_id, product_id, product_name, unit, qty, price, tax_rate, amount, batch_no, location)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, name, unit, line.qty, line.price, line.taxRate,
                amount.setScale(2, RoundingMode.HALF_UP), line.batchNo, line.location,
            )
        }
        jdbc.update(
            "UPDATE sales_outbound SET total_qty = ?, total_amount = ? WHERE id = ?::uuid",
            totalQty, totalAmount.setScale(2, RoundingMode.HALF_UP), id.toString(),
        )
        audit.log("sales", "创建销售出库单 $no", "sales_outbound", id.toString())
        return mapOf("id" to id.toString(), "outboundNo" to no, "status" to "draft")
    }

    /** 审核：授信校验 → 逐行出库 → 单据置 done → 生成应收并占用授信 */
    @Transactional
    fun auditOutbound(id: UUID) {
        val head = jdbc.queryForMap(
            "SELECT * FROM sales_outbound WHERE id = ?::uuid FOR UPDATE", id.toString()
        )
        val status = head["status"]?.toString()
        if (status == "done" || status == "void") throw BizException(ErrorCode.BIZ_CONFLICT, "单据已审核或已作废")
        val no = head["outbound_no"]?.toString() ?: id.toString()
        val warehouseId = head["warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少仓库")
        val items = jdbc.queryForList(
            "SELECT * FROM sales_outbound_item WHERE outbound_id = ?::uuid", id.toString()
        )
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行为空，无法审核")

        val amount = bd(head["total_amount"])

        // 关联销售订单时，订单须已审批通过才能发货
        val orderId = head["order_id"]?.toString()
        if (orderId != null) {
            val st = jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?::uuid", String::class.java, orderId
            )
            if (st != "approved") {
                throw BizException(ErrorCode.BIZ_CONFLICT, "销售订单尚未审批通过（当前：$st），不能发货")
            }
        }

        // 授信校验：可用授信 = 额度 - 已用
        val cust = jdbc.queryForMap(
            "SELECT credit_limit, COALESCE(credit_used,0) AS credit_used, payment_terms FROM customer WHERE id = ?::uuid",
            head["customer_id"].toString(),
        )
        val limit = bd(cust["credit_limit"])
        val used = bd(cust["credit_used"])
        if (limit > BigDecimal.ZERO && used + amount > limit) {
            throw BizException(
                ErrorCode.BIZ_CONFLICT,
                "超出客户可用授信：额度 $limit，已用 $used，本次 $amount",
            )
        }

        val productQty = mutableMapOf<String, BigDecimal>()
        items.forEach { it ->
            val pid = it["product_id"].toString()
            val q = bd(it["qty"])
            productQty[pid] = (productQty[pid] ?: BigDecimal.ZERO) + q
            inventoryService.outbound(
                StockMoveRequest(
                    productId = pid,
                    warehouseId = warehouseId,
                    qty = q,
                    batchNo = it["batch_no"]?.toString(),
                    location = it["location"]?.toString(),
                    refType = "sales",
                    refNo = no,
                    remark = "销售出库 $no",
                )
            )
        }

        jdbc.update("UPDATE sales_outbound SET status = 'done' WHERE id = ?::uuid", id.toString())

        // 回写销售订单已发数量并推进订单状态
        if (orderId != null) {
            orderService.applyOutbound(UUID.fromString(orderId), productQty)
        }

        val terms = (cust["payment_terms"] as? Number)?.toInt() ?: 0
        jdbc.update(
            """
            INSERT INTO ar_ledger(customer_id, biz_type, biz_id, biz_no, amount, remain_amount, due_date, status)
            VALUES (?::uuid, 'sales_outbound', ?::uuid, ?, ?, ?, CURRENT_DATE + ?, 'open')
            """.trimIndent(),
            head["customer_id"].toString(),
            id.toString(),
            no,
            amount,
            amount,
            terms,
        )
        // 占用授信
        jdbc.update(
            "UPDATE customer SET credit_used = COALESCE(credit_used,0) + ? WHERE id = ?::uuid",
            amount,
            head["customer_id"].toString(),
        )
        audit.log("sales", "审核销售出库单 $no，生成应收 $amount", "sales_outbound", id.toString())
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    private companion object {
        const val ZERO = "00000000-0000-0000-0000-000000000000"
    }
}
