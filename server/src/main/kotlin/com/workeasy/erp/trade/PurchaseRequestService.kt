package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
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
import java.time.LocalDate
import java.util.UUID

private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

data class PrLine(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val remark: String? = null,
)

data class PurchaseRequestCreate(
    val supplierId: String? = null,
    val expectDate: String? = null,
    val reason: String? = null,
    val remark: String? = null,
    val items: List<PrLine> = emptyList(),
)

/**
 * 采购申请：起草 → 提交审批 → 通过后**一键转采购订单**（申请与订单联动）。
 * 数据范围按申请人所在部门 / 本人过滤。
 */
@Service
class PurchaseRequestService(
    private val jdbc: JdbcTemplate,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "purchase_request")
        val cond = StringBuilder("WHERE r.deleted_at IS NULL ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND r.applicant_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: ZERO_ID)
            }
            "dept" -> {
                cond.append("AND r.dept_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: ZERO_ID)
            }
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (r.no ILIKE ? OR r.supplier_name ILIKE ? OR r.reason ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND r.status = ? ")
            args.add(status)
        }

        val from = "FROM purchase_request r $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id, r.no, r.applicant_name, r.supplier_name, r.expect_date, r.reason, r.status,
                   (SELECT COALESCE(SUM(i.qty), 0) FROM purchase_request_item i WHERE i.request_id = r.id) AS total_qty
            $from ORDER BY r.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional(readOnly = true)
    fun items(id: String): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT i.id, i.product_id, i.product_name, i.unit, i.qty, i.remark
        FROM purchase_request_item i WHERE i.request_id = ?::uuid ORDER BY i.product_name
        """.trimIndent(),
        id,
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional
    fun create(req: PurchaseRequestCreate): Map<String, Any?> {
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "申请明细不能为空")
        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("PR")
        val supplierName = req.supplierId?.let { nameOf("supplier", it) }

        jdbc.update(
            """
            INSERT INTO purchase_request(id, no, applicant_id, applicant_name, dept_id, supplier_id,
                                         supplier_name, expect_date, reason, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?::uuid, ?, ?::date, ?, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, me.employeeId?.toString(), me.displayName, me.deptId?.toString(),
            req.supplierId, supplierName, req.expectDate, req.reason, req.remark, me.userId.toString(),
        )
        req.items.forEach { line ->
            val pName = nameOf("product", line.productId)
            val unit = runCatching {
                jdbc.queryForObject("SELECT unit FROM product WHERE id = ?::uuid", String::class.java, line.productId)
            }.getOrNull()
            jdbc.update(
                """
                INSERT INTO purchase_request_item(request_id, product_id, product_name, unit, qty, remark)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, pName, unit, line.qty, line.remark,
            )
        }
        audit.log("purchase", "起草采购申请 $no（${req.items.size} 行）", "purchase_request", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to "draft")
    }

    @Transactional
    fun submit(id: UUID): Map<String, Any?> {
        val row = jdbc.queryForMap("SELECT * FROM purchase_request WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = row["status"]?.toString()
        if (status != "draft" && status != "rejected") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅草稿或已驳回的申请可提交（当前：$status）")
        }
        val no = row["no"]?.toString() ?: id.toString()
        val totalQty = jdbc.queryForObject(
            "SELECT COALESCE(SUM(qty),0) FROM purchase_request_item WHERE request_id = ?::uuid",
            BigDecimal::class.java, id.toString(),
        ) ?: BigDecimal.ZERO
        val instId = workflowService.start(
            StartRequest(
                bizType = "purchase_request",
                bizId = id,
                title = "采购申请 $no（$totalQty 件）",
                formData = mapOf("no" to no, "amount" to "0"),
            ),
            UserContext.get(),
        )
        jdbc.update(
            "UPDATE purchase_request SET status='approving', workflow_instance_id=?::uuid, updated_at=now() WHERE id=?::uuid",
            instId.toString(), id.toString(),
        )
        audit.log("purchase", "提交采购申请审批 $no", "purchase_request", id.toString())
        return mapOf("id" to id.toString(), "status" to "approving")
    }

    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        if (e.bizType != "purchase_request") return
        val approved = e.result == "approved"
        jdbc.update(
            "UPDATE purchase_request SET status = ?, updated_at = now() WHERE id = ?::uuid",
            if (approved) "approved" else "rejected",
            e.bizId.toString(),
        )
        audit.log("purchase", "采购申请审批${if (approved) "通过" else "驳回"}", "purchase_request", e.bizId.toString())
    }

    /** 审批通过 → 一键生成采购订单（草稿，仍需提交订单审批） */
    @Transactional
    fun toOrder(id: UUID): Map<String, Any?> {
        val head = jdbc.queryForMap(
            "SELECT * FROM purchase_request WHERE id = ?::uuid FOR UPDATE", id.toString()
        )
        if (head["status"]?.toString() != "approved") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅审批通过的申请可转订单（当前：${head["status"]}）")
        }
        val lines = jdbc.queryForList(
            "SELECT * FROM purchase_request_item WHERE request_id = ?::uuid", id.toString()
        )
        if (lines.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "申请明细为空")

        val me = UserContext.get()
        val orderId = UUID.randomUUID()
        val orderNo = noGenerator.next("PO")
        val supplierId = head["supplier_id"]?.toString()
            ?: throw BizException(ErrorCode.BIZ_PARAM, "申请未指定供应商，无法转订单")

        var net = BigDecimal.ZERO
        val prepared = lines.map { l ->
            val qty = bd(l["qty"])
            val price = runCatching {
                jdbc.queryForObject(
                    "SELECT COALESCE(purchase_price, 0) FROM product WHERE id = ?::uuid",
                    BigDecimal::class.java, l["product_id"].toString(),
                )
            }.getOrNull() ?: BigDecimal.ZERO
            val amount = qty * price
            net += amount
            Triple(l, qty, Triple(price, amount, amount))
        }

        jdbc.update(
            """
            INSERT INTO purchase_order(id, order_no, supplier_id, supplier_name, dept_id, owner_id,
                                       status, remark, total_qty, total_net, total_tax, total_amount, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?::uuid, 'draft', ?, ?, ?, 0, ?, ?::uuid)
            """.trimIndent(),
            orderId.toString(), orderNo, supplierId, head["supplier_name"]?.toString(),
            head["dept_id"]?.toString(), head["applicant_id"]?.toString(),
            "由采购申请 ${head["no"]} 生成", bd(head["total_qty"] ?: 0), net, net, me.userId.toString(),
        )
        var totalQty = BigDecimal.ZERO
        prepared.forEachIndexed { i, (l, qty, money) ->
            val price = money.first
            val amount = money.second
            jdbc.update(
                """
                INSERT INTO purchase_order_item(order_id, line_no, product_id, product_name, unit,
                                                 qty, price, tax_rate, tax_amount, net_amount, amount)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, 0, 0, ?, ?)
                """.trimIndent(),
                orderId.toString(), i + 1, l["product_id"].toString(), l["product_name"]?.toString(),
                l["unit"]?.toString(), qty, price, amount, amount,
            )
            totalQty += qty
        }
        jdbc.update(
            "UPDATE purchase_order SET total_qty = ? WHERE id = ?::uuid", totalQty, orderId.toString()
        )
        jdbc.update(
            "UPDATE purchase_request SET status='converted', updated_at=now() WHERE id=?::uuid", id.toString()
        )
        audit.log("purchase", "采购申请 ${head["no"]} 转为采购订单 $orderNo", "purchase_order", orderId.toString())
        return mapOf("orderId" to orderId.toString(), "orderNo" to orderNo, "status" to "draft")
    }

    private fun nameOf(table: String, id: String): String? = runCatching {
        jdbc.queryForObject("SELECT name FROM $table WHERE id = ?::uuid", String::class.java, id)
    }.getOrNull()

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
