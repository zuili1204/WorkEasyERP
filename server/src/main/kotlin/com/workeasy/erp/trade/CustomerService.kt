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
import java.util.UUID

data class CustomerCreateRequest(
    val name: String = "",
    val shortName: String? = null,
    val level: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val address: String? = null,
    val creditLimit: java.math.BigDecimal? = null,
    val paymentTerms: Int? = null,
    val ownerId: String? = null,
    val remark: String? = null,
)

/**
 * 客户管理：以 owner_id / dept_id 支撑数据范围（我的 / 本部门 / 全部）。
 * 销售看自己负责的客户，主管看本部门，老板与财务看全部。
 */
@Service
class CustomerService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, scope: String?, level: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "customer")

        val cond = StringBuilder("WHERE c.deleted_at IS NULL ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND c.owner_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: ZERO)
            }
            "dept" -> {
                cond.append("AND c.dept_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: ZERO)
            }
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (c.name ILIKE ? OR c.code ILIKE ? OR c.contact_name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!level.isNullOrBlank()) {
            cond.append("AND c.level = ? ")
            args.add(level)
        }

        val from = "FROM customer c LEFT JOIN employee e ON e.id = c.owner_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT c.id, c.code, c.name, c.level, c.contact_name, c.contact_phone,
                   c.credit_limit, c.credit_used, c.payment_terms, c.status,
                   e.real_name AS owner_name, c.owner_id, c.created_at
            $from
            ORDER BY c.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(req: CustomerCreateRequest): Map<String, Any?> {
        val me = UserContext.get()
        val name = req.name.trim()
        if (name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "客户名称不能为空")

        val id = UUID.randomUUID()
        val code = noGenerator.next("C")
        val ownerId = req.ownerId ?: me.employeeId?.toString()
        val deptId = if (ownerId != null) {
            jdbc.queryForObject(
                "SELECT department_id FROM employee WHERE id = ?::uuid", String::class.java, ownerId
            )
        } else {
            me.deptId?.toString()
        }

        jdbc.update(
            """
            INSERT INTO customer(id, code, name, short_name, level, contact_name, contact_phone,
                                 address, credit_limit, payment_terms, owner_id, dept_id, remark)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?::uuid, ?)
            """.trimIndent(),
            id.toString(),
            code,
            name,
            req.shortName,
            req.level,
            req.contactName,
            req.contactPhone,
            req.address,
            req.creditLimit ?: java.math.BigDecimal.ZERO,
            req.paymentTerms ?: 0,
            ownerId,
            deptId,
            req.remark,
        )
        audit.log("customer", "新增客户 $name（$code）", "customer", id.toString())
        return mapOf("id" to id.toString(), "code" to code, "status" to "active")
    }

    private companion object {
        const val ZERO = "00000000-0000-0000-0000-000000000000"
    }
}
