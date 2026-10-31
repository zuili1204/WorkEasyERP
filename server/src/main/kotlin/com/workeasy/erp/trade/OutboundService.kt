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
    private val creditService: CreditService,
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
            val p = jdbc.queryForMap(
                "SELECT name, unit, aux_unit, convert_rate FROM product WHERE id = ?::uuid",
                line.productId,
            )
            // 录入单位 → 主单位；出库扣减与流水一律主单位口径
            val u = UnitConversion.resolve(
                line.qty, line.price, line.unit,
                p["unit"]?.toString(), p["aux_unit"]?.toString(), bd(p["convert_rate"]),
            )
            val amount = u.qty * u.price * (BigDecimal.ONE + line.taxRate)
            totalQty += u.qty
            totalAmount += amount
            jdbc.update(
                """
                INSERT INTO sales_outbound_item(outbound_id, product_id, product_name, unit, qty, price,
                                                aux_unit, aux_qty, convert_rate, tax_rate, amount, batch_no, location)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, p["name"].toString(), u.unit, u.qty, u.price,
                u.auxUnit, u.auxQty, u.convertRate, line.taxRate,
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

        // 关联销售订单时，订单须已审批通过才能发货；渠道决定财务处理口径
        val orderId = head["order_id"]?.toString()
        var mallChannel = false
        if (orderId != null) {
            val so = jdbc.queryForMap(
                "SELECT status, channel FROM sales_order WHERE id = ?::uuid", orderId
            )
            val st = so["status"]?.toString()
            if (st != "approved") {
                throw BizException(ErrorCode.BIZ_CONFLICT, "销售订单尚未审批通过（当前：$st），不能发货")
            }
            mallChannel = (so["channel"]?.toString() ?: "erp") == "mall"
        }

        // 授信校验：与下单预览/订单确认同一口径；超限按策略拦截或审批放行。
        // 商城订单的应收与授信占用已在下单/支付时完成，此处不重复校验与占用。
        val cust = jdbc.queryForMap(
            "SELECT payment_terms FROM customer WHERE id = ?::uuid",
            head["customer_id"].toString(),
        )
        if (!mallChannel) {
            val chk = creditService.check(head["customer_id"].toString(), amount, orderId)
            if (chk.action == "block") {
                throw BizException(ErrorCode.BIZ_CONFLICT, chk.message ?: "超出客户授信额度")
            }
            if (chk.action == "approval") {
                audit.log(
                    "sales",
                    "出库单 $no 超出授信，按经理审批放行（可用 ${chk.available}）",
                    "sales_outbound",
                    id.toString(),
                )
            }
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
                    releaseLocked = orderId != null,
                    auxUnit = it["aux_unit"]?.toString(),
                    auxQty = it["aux_qty"]?.let { v -> bd(v) },
                    convertRate = it["convert_rate"]?.let { v -> bd(v) },
                )
            )
        }

        jdbc.update("UPDATE sales_outbound SET status = 'done' WHERE id = ?::uuid", id.toString())

        // 回写销售订单已发数量并推进订单状态
        if (orderId != null) {
            orderService.applyOutbound(UUID.fromString(orderId), productQty)
        }

        if (mallChannel) {
            // 商城订单的应收/核销在支付或账期确认时已生成，出库只同步发货状态
            val soStatus = jdbc.queryForList(
                "SELECT status FROM sales_order WHERE id = ?::uuid", String::class.java, orderId
            ).firstOrNull()
            if (soStatus == "shipped") {
                jdbc.update(
                    """
                    UPDATE mall_order SET status = 'shipped', shipped_at = COALESCE(shipped_at, now()), updated_at = now()
                    WHERE sales_order_id = ?::uuid AND status = 'pending_ship'
                    """.trimIndent(),
                    orderId,
                )
            }
            audit.log("sales", "审核商城出库单 $no，同步商城发货状态", "sales_outbound", id.toString())
            return
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
