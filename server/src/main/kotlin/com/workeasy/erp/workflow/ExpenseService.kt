package com.workeasy.erp.workflow

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ExpenseCreateRequest(
    val category: String = "other",
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String = "CNY",
    val happenDate: String? = null,
    val reason: String? = null,
    val remark: String? = null,
)

/**
 * 报销：登记 → 提交审批（主管 → 财务）→ 通过后标记已付款。
 * 数据范围：按 employee_id / dept_id 支撑 mine / dept / all。
 */
@Service
class ExpenseService(
    private val jdbc: JdbcTemplate,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "expense")

        val cond = StringBuilder("WHERE e.deleted_at IS NULL ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND e.employee_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: ZERO_ID)
            }
            "dept" -> {
                cond.append("AND e.dept_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: ZERO_ID)
            }
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (e.no ILIKE ? OR e.employee_name ILIKE ? OR e.reason ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND e.status = ? ")
            args.add(status)
        }

        val from = "FROM expense e $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT e.id, e.no, e.employee_name, e.dept_name, e.category, e.amount, e.currency,
                   e.happen_date, e.reason, e.status, e.pay_date
            $from ORDER BY e.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(req: ExpenseCreateRequest): Map<String, Any?> {
        val me = UserContext.get()
        if (req.amount <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "报销金额必须大于 0")

        val id = UUID.randomUUID()
        val no = noGenerator.next("EXP")
        jdbc.update(
            """
            INSERT INTO expense(id, no, employee_id, employee_name, dept_id, dept_name, category,
                                amount, currency, happen_date, reason, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?::date, ?, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, me.employeeId?.toString(), me.displayName,
            me.deptId?.toString(), me.deptName, req.category, req.amount,
            req.currency, req.happenDate ?: LocalDate.now().toString(), req.reason,
            req.remark, me.userId.toString(),
        )
        audit.log("expense", "登记报销 $no（${req.amount}）", "expense", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to "draft")
    }

    /** 提交审批：草稿 → 审批中 */
    @Transactional
    fun submit(id: UUID): Map<String, Any?> {
        val row = jdbc.queryForMap("SELECT * FROM expense WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = row["status"]?.toString()
        if (status != "draft" && status != "rejected") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅草稿或已驳回的报销可提交（当前：$status）")
        }
        val no = row["no"]?.toString() ?: id.toString()
        val me = UserContext.get()
        val instId = workflowService.start(
            StartRequest(
                bizType = "expense",
                bizId = id,
                title = "报销 $no（${row["amount"]}）",
                formData = mapOf("amount" to row["amount"]?.toString(), "no" to no),
            ),
            me,
        )
        jdbc.update(
            "UPDATE expense SET status='approving', workflow_instance_id=?::uuid, updated_at=now() WHERE id=?::uuid",
            instId.toString(), id.toString(),
        )
        audit.log("expense", "提交报销审批 $no", "expense", id.toString())
        return mapOf("id" to id.toString(), "status" to "approving")
    }

    /** 流程结束回写；通过后置为 approved（付款由财务在收付款环节处理） */
    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        if (e.bizType != "expense") return
        val approved = e.result == "approved"
        jdbc.update(
            "UPDATE expense SET status = ?, updated_at = now() WHERE id = ?::uuid",
            if (approved) "approved" else "rejected",
            e.bizId.toString(),
        )
        audit.log("expense", "报销审批${if (approved) "通过" else "驳回"}", "expense", e.bizId.toString())
    }

    /** 标记已付款 */
    @Transactional
    fun pay(id: UUID) {
        val row = jdbc.queryForMap("SELECT status, no FROM expense WHERE id = ?::uuid FOR UPDATE", id.toString())
        if (row["status"]?.toString() != "approved") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅审批通过的报销可付款")
        }
        jdbc.update(
            "UPDATE expense SET status='paid', pay_date=CURRENT_DATE, updated_at=now() WHERE id=?::uuid",
            id.toString(),
        )
        audit.log("expense", "报销付款 ${row["no"]}", "expense", id.toString())
    }

    companion object {
        const val ZERO_ID = "00000000-0000-0000-0000-000000000000"
    }
}
