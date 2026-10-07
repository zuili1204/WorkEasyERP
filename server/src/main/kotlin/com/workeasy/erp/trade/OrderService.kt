package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.FieldPolicy
import com.workeasy.erp.common.Paging
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import com.workeasy.erp.workflow.StartRequest
import com.workeasy.erp.workflow.WorkflowFinishedEvent
import com.workeasy.erp.workflow.WorkflowService
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class OrderItemRequest(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal = BigDecimal.ZERO,
    val taxRate: BigDecimal = BigDecimal("0.13"),
)

data class OrderCreateRequest(
    /** 统一字段；也接受 supplierId / customerId，便于按业务命名调用 */
    val partyId: String = "",
    val supplierId: String? = null,
    val customerId: String? = null,
    val warehouseId: String? = null,
    val remark: String? = null,
    val creditDays: Int? = null,
    val items: List<OrderItemRequest> = emptyList(),
)

/**
 * 订单层（订单与履约分离）：
 * - 采购订单 / 销售订单只表达"买什么、卖什么、多少钱"
 * - 实际收发由 `purchase_inbound` / `sales_outbound` 执行，并回写 `received_qty` / `shipped_qty`
 * - 销售订单下单时按当前加权成本冻结 `cost_price`，据此计算毛利（利润口径见文档 §8）
 */
@Service
class OrderService(
    private val jdbc: JdbcTemplate,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    // ---------- 采购订单 ----------

    @Transactional(readOnly = true)
    fun poList(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        orderList("purchase", q, status, null, page, size)

    @Transactional
    fun createPo(req: OrderCreateRequest): Map<String, Any?> = createOrder("purchase", req)

    // ---------- 销售订单 ----------

    @Transactional(readOnly = true)
    fun soList(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        orderList("sales", q, status, scope, page, size)

    @Transactional
    fun createSo(req: OrderCreateRequest): Map<String, Any?> = createOrder("sales", req)

    // ---------- 订单审批 ----------

    /** 提交审批：订单从 draft → approving，并写入流程实例 */
    @Transactional
    fun submit(type: String, id: UUID): Map<String, Any?> {
        val isSales = type == "sales"
        val table = if (isSales) "sales_order" else "purchase_order"
        val head = jdbc.queryForMap("SELECT * FROM $table WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = head["status"]?.toString()
        if (status != "draft" && status != "rejected") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅草稿或已驳回的订单可提交审批（当前：$status）")
        }
        val no = head["order_no"]?.toString() ?: id.toString()
        val bizType = if (isSales) "sales_order" else "purchase_order"
        val instId = workflowService.start(
            StartRequest(
                bizType = bizType,
                bizId = id,
                title = "${if (isSales) "销售" else "采购"}订单 $no",
                formData = mapOf("orderNo" to no, "amount" to bd(head["total_amount"]).toPlainString()),
            ),
            UserContext.get(),
        )
        jdbc.update(
            "UPDATE $table SET status = 'approving', workflow_instance_id = ?::uuid, updated_at = now() WHERE id = ?::uuid",
            instId.toString(),
            id.toString(),
        )
        audit.log("order", "提交订单审批 $no", table, id.toString())
        return mapOf("id" to id.toString(), "status" to "approving", "instanceId" to instId.toString())
    }

    /** 流程结束 → 回写订单状态（审批通过后才允许履约） */
    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        if (e.bizType != "purchase_order" && e.bizType != "sales_order") return
        val table = if (e.bizType == "sales_order") "sales_order" else "purchase_order"
        val approved = e.result == "approved"
        val status = if (approved) "approved" else "rejected"
        jdbc.update(
            "UPDATE $table SET status = ?, updated_at = now() WHERE id = ?::uuid",
            status,
            e.bizId.toString(),
        )
        audit.log("order", "订单审批${if (approved) "通过" else "驳回"}", table, e.bizId.toString())
    }

    /** 供履约单校验：订单是否已审批通过 */
    fun statusOf(bizType: String, orderId: String): String? = runCatching {
        jdbc.queryForObject(
            "SELECT status FROM ${if (bizType == "sales_order") "sales_order" else "purchase_order"} WHERE id = ?::uuid",
            String::class.java,
            orderId,
        )
    }.getOrNull()

    // ---------- 履约回写 ----------

    /** 采购入库审核后回写订单行已收数量，并推进订单状态 */
    @Transactional
    fun applyInbound(orderId: UUID?, productQty: Map<String, BigDecimal>) {
        if (orderId == null) return
        jdbc.queryForList(
            "SELECT id, product_id, qty, received_qty FROM purchase_order_item WHERE order_id = ?::uuid",
            orderId.toString(),
        ).forEach { row ->
            val pid = row["product_id"]?.toString() ?: return@forEach
            val add = productQty[pid] ?: return@forEach
            val received = bd(row["received_qty"]) + add
            jdbc.update(
                "UPDATE purchase_order_item SET received_qty = ? WHERE id = ?::uuid",
                received,
                row["id"].toString(),
            )
        }
        val rows = jdbc.queryForList(
            "SELECT qty, received_qty FROM purchase_order_item WHERE order_id = ?::uuid",
            orderId.toString(),
        )
        val all = rows.all { bd(it["received_qty"]) >= bd(it["qty"]) }
        val any = rows.any { bd(it["received_qty"]) > BigDecimal.ZERO }
        val status = if (all) "received" else if (any) "partial" else "approved"
        jdbc.update("UPDATE purchase_order SET status = ?, updated_at = now() WHERE id = ?::uuid", status, orderId.toString())
    }

    /** 销售出库审核后回写订单行已发数量，并推进订单状态 */
    @Transactional
    fun applyOutbound(orderId: UUID?, productQty: Map<String, BigDecimal>) {
        if (orderId == null) return
        jdbc.queryForList(
            "SELECT id, product_id, qty, shipped_qty FROM sales_order_item WHERE order_id = ?::uuid",
            orderId.toString(),
        ).forEach { row ->
            val pid = row["product_id"]?.toString() ?: return@forEach
            val add = productQty[pid] ?: return@forEach
            val shipped = bd(row["shipped_qty"]) + add
            val qty = bd(row["qty"])
            jdbc.update(
                """
                UPDATE sales_order_item SET shipped_qty = ?, unship_qty = ? - ? WHERE id = ?::uuid
                """.trimIndent(),
                shipped, qty, shipped, row["id"].toString(),
            )
        }
        val rows = jdbc.queryForList(
            "SELECT qty, shipped_qty FROM sales_order_item WHERE order_id = ?::uuid",
            orderId.toString(),
        )
        val all = rows.all { bd(it["shipped_qty"]) >= bd(it["qty"]) }
        val any = rows.any { bd(it["shipped_qty"]) > BigDecimal.ZERO }
        val status = if (all) "shipped" else if (any) "partial" else "approved"
        jdbc.update("UPDATE sales_order SET status = ?, updated_at = now() WHERE id = ?::uuid", status, orderId.toString())
    }

    // ---------- 内部实现 ----------

    private fun createOrder(type: String, req: OrderCreateRequest): Map<String, Any?> {
        val isSales = type == "sales"
        val partyId = if (req.partyId.isNotBlank()) {
            req.partyId
        } else {
            (if (isSales) req.customerId else req.supplierId) ?: ""
        }
        if (partyId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, if (isSales) "客户不能为空" else "供应商不能为空")
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行不能为空")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next(if (isSales) "SO" else "PO")
        val partyTable = if (isSales) "customer" else "supplier"
        val partyName = jdbc.queryForObject(
            "SELECT name FROM $partyTable WHERE id = ?::uuid", String::class.java, partyId
        )
        val partyCol = if (isSales) "customer_id" else "supplier_id"
        val nameCol = if (isSales) "customer_name" else "supplier_name"

        var totalQty = BigDecimal.ZERO
        var totalNet = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO
        var totalCost = BigDecimal.ZERO

        // 销售订单有 credit_days（账期）；采购订单对应字段为 pay_terms，建单时不写
        if (isSales) {
            jdbc.update(
                """
                INSERT INTO sales_order(id, order_no, customer_id, customer_name, dept_id, owner_id, status, credit_days, remark, created_by)
                VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?::uuid, 'draft', ?, ?, ?::uuid)
                """.trimIndent(),
                id.toString(), no, partyId, partyName, me.deptId?.toString(),
                me.employeeId?.toString(), req.creditDays ?: 0, req.remark, me.userId.toString(),
            )
        } else {
            jdbc.update(
                """
                INSERT INTO purchase_order(id, order_no, supplier_id, supplier_name, dept_id, owner_id, status, remark, created_by)
                VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?::uuid, 'draft', ?, ?::uuid)
                """.trimIndent(),
                id.toString(), no, partyId, partyName, me.deptId?.toString(),
                me.employeeId?.toString(), req.remark, me.userId.toString(),
            )
        }

        req.items.forEachIndexed { idx, line ->
            val p = jdbc.queryForMap(
                "SELECT name, spec, unit, COALESCE(sale_price,0) AS sale_price FROM product WHERE id = ?::uuid",
                line.productId,
            )
            val net = line.qty * line.price
            val tax = net * line.taxRate
            // 销售订单冻结当前加权成本（来自 inventory），用于计算毛利
            val costPrice = if (isSales) {
                bd(
                    jdbc.queryForObject(
                        "SELECT COALESCE(MAX(avg_cost),0) FROM inventory WHERE product_id = ?::uuid",
                        BigDecimal::class.java,
                        line.productId,
                    )
                )
            } else {
                BigDecimal.ZERO
            }
            val costAmount = if (isSales) costPrice * line.qty else BigDecimal.ZERO

            totalQty += line.qty
            totalNet += net
            totalTax += tax
            totalCost += costAmount

            if (isSales) {
                val profit = net - costAmount
                val rate = if (net > BigDecimal.ZERO) profit.divide(net, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
                jdbc.update(
                    """
                    INSERT INTO sales_order_item(order_id, line_no, product_id, product_name, spec, unit,
                                                 qty, price, tax_rate, tax_amount, net_amount, amount,
                                                 cost_price, cost_amount, profit, profit_rate, shipped_qty, unship_qty, warehouse_id)
                    VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?::uuid)
                    """.trimIndent(),
                    id.toString(), idx + 1, line.productId, p["name"].toString(), p["spec"]?.toString(),
                    p["unit"]?.toString(), line.qty, line.price, line.taxRate,
                    tax.setScale(2, RoundingMode.HALF_UP), net.setScale(2, RoundingMode.HALF_UP),
                    (net + tax).setScale(2, RoundingMode.HALF_UP), costPrice,
                    costAmount.setScale(2, RoundingMode.HALF_UP), profit.setScale(2, RoundingMode.HALF_UP),
                    rate, line.qty, req.warehouseId,
                )
            } else {
                jdbc.update(
                    """
                    INSERT INTO purchase_order_item(order_id, line_no, product_id, product_name, spec, unit,
                                                    qty, price, tax_rate, tax_amount, net_amount, amount,
                                                    received_qty, warehouse_id)
                    VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?::uuid)
                    """.trimIndent(),
                    id.toString(), idx + 1, line.productId, p["name"].toString(), p["spec"]?.toString(),
                    p["unit"]?.toString(), line.qty, line.price, line.taxRate,
                    tax.setScale(2, RoundingMode.HALF_UP), net.setScale(2, RoundingMode.HALF_UP),
                    (net + tax).setScale(2, RoundingMode.HALF_UP), req.warehouseId,
                )
            }
        }

        val totalAmount = totalNet + totalTax
        if (isSales) {
            val profit = totalNet - totalCost
            val rate = if (totalNet > BigDecimal.ZERO) profit.divide(totalNet, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            jdbc.update(
                """
                UPDATE sales_order SET total_qty=?, total_net=?, total_tax=?, total_amount=?,
                       total_cost=?, total_profit=?, profit_rate=?, receivable=? WHERE id=?::uuid
                """.trimIndent(),
                totalQty, totalNet.setScale(2, RoundingMode.HALF_UP), totalTax.setScale(2, RoundingMode.HALF_UP),
                totalAmount.setScale(2, RoundingMode.HALF_UP), totalCost.setScale(2, RoundingMode.HALF_UP),
                profit.setScale(2, RoundingMode.HALF_UP), rate, totalAmount.setScale(2, RoundingMode.HALF_UP),
                id.toString(),
            )
        } else {
            jdbc.update(
                "UPDATE purchase_order SET total_qty=?, total_net=?, total_tax=?, total_amount=? WHERE id=?::uuid",
                totalQty, totalNet.setScale(2, RoundingMode.HALF_UP), totalTax.setScale(2, RoundingMode.HALF_UP),
                totalAmount.setScale(2, RoundingMode.HALF_UP), id.toString(),
            )
        }

        audit.log("order", "创建${if (isSales) "销售" else "采购"}订单 $no", if (isSales) "sales_order" else "purchase_order", id.toString())
        return mapOf("id" to id.toString(), "orderNo" to no, "status" to "draft")
    }

    private fun orderList(
        type: String,
        q: String?,
        status: String?,
        scope: String?,
        page: Int,
        size: Int,
    ): PageResult<Map<String, Any?>> {
        val isSales = type == "sales"
        val table = if (isSales) "sales_order" else "purchase_order"
        val nameCol = if (isSales) "customer_name" else "supplier_name"
        val extra = if (isSales) ", o.total_profit, o.profit_rate, o.receivable, o.received" else ""

        val me = UserContext.get()
        val eff = if (isSales) scopeResolver.resolve(scope, me, "sales_order") else "all"
        // 入参钳制：异常分页与超长关键词在此统一收敛
        val (pg, pgSize) = Paging.clamp(page, size)
        val kw = Paging.keyword(q)

        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE o.deleted_at IS NULL ")
        if (isSales) {
            when (eff) {
                "mine" -> {
                    cond.append("AND o.owner_id = ?::uuid ")
                    args.add(me.employeeId?.toString() ?: ZERO)
                }
                "dept" -> {
                    cond.append("AND o.dept_id = ?::uuid ")
                    args.add(me.deptId?.toString() ?: ZERO)
                }
            }
        }
        if (kw != null) {
            cond.append("AND (o.order_no ILIKE ? OR o.$nameCol ILIKE ?) ")
            args.add("%$kw%")
            args.add("%$kw%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND o.status = ? ")
            args.add(status)
        }

        val from = "FROM $table o $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT o.id, o.order_no, o.$nameCol AS party_name, o.status, o.order_date,
                   o.total_qty, o.total_net, o.total_tax, o.total_amount $extra
            $from
            ORDER BY o.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        )
        // 字段级权限：成本与毛利对销售等角色脱敏（服务端过滤，前端隐藏不算数）
        val list = FieldPolicy.mask(
            rows.map { it.mapValues { (_, v) -> v?.toString() } },
            UserContext.get().roles,
        )
        return PageResult.of(list, total, pg, pgSize)
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
