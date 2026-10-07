package com.workeasy.erp.workflow

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.FieldPolicy
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

data class PayrollGenerateRequest(
    val employeeId: String? = null,
    val period: String? = null,          // 2026-10
    val baseSalary: BigDecimal = BigDecimal.ZERO,
    val bonus: BigDecimal = BigDecimal.ZERO,
    val allowance: BigDecimal = BigDecimal.ZERO,
    val deduction: BigDecimal = BigDecimal.ZERO,
    val socialSecurity: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val payDate: String? = null,
    val remark: String? = null,
)

/** 薪资（按月）：列表 + 生成（net_pay 由服务端计算，前端不参与金额计算） */
@Service
class PayrollService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    @Transactional(readOnly = true)
    fun list(period: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "payroll")

        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND p.employee_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: "00000000-0000-0000-0000-000000000000")
            }
            "dept" -> {
                cond.append("AND e.department_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: "00000000-0000-0000-0000-000000000000")
            }
        }
        if (!period.isNullOrBlank()) {
            cond.append("AND p.period = ? ")
            args.add(period)
        }

        val from = "FROM payroll p LEFT JOIN employee e ON e.id = p.employee_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT p.*, e.real_name AS employee_name $from ORDER BY p.period DESC, p.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        // 字段级权限：本人记录始终可见；他人薪资仅老板 / 财务 / HR 可见
        val list = rows.map { row ->
            val plain = row.mapValues { (_, v) -> v?.toString() }
            val isSelf = row["employee_id"]?.toString() == me.employeeId?.toString()
            if (isSelf) plain else FieldPolicy.maskRow(plain, me.roles)
        }
        return PageResult.of(list, total, page, size)
    }

    @Transactional
    fun generate(req: PayrollGenerateRequest) {
        val me = UserContext.get()
        val empId = req.employeeId ?: me.employeeId?.toString()
            ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少员工")
        val period = req.period ?: LocalDate.now().toString().substring(0, 7)

        val net = req.baseSalary + req.bonus + req.allowance -
            req.deduction - req.socialSecurity - req.tax

        jdbc.update(
            """
            INSERT INTO payroll(employee_id, period, base_salary, bonus, allowance, deduction,
                                social_security, tax, net_pay, pay_date, status, remark)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, 'draft', ?)
            """.trimIndent(),
            empId,
            period,
            req.baseSalary,
            req.bonus,
            req.allowance,
            req.deduction,
            req.socialSecurity,
            req.tax,
            net,
            req.payDate,
            req.remark,
        )
        audit.log("payroll", "生成薪资 $period", "payroll", empId)
    }
}
