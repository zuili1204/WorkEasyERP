package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.Paging
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

data class LeadRequest(
    val name: String = "",
    val source: String = "other",
    val contactName: String? = null,
    val contactPhone: String? = null,
    val remark: String? = null,
)

data class OpportunityRequest(
    val name: String = "",
    val customerId: String? = null,
    val stage: String = "contact",
    val amount: BigDecimal = BigDecimal.ZERO,
    val expectCloseDate: String? = null,
    val remark: String? = null,
)

data class ContractRequest(
    val name: String = "",
    val customerId: String = "",
    val opportunityId: String? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val signDate: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val paymentTerms: Int = 0,
    val status: String = "effective",
    val remark: String? = null,
)

/**
 * CRM：线索 → 客户 → 商机 → 合同。
 * 三个对象都以 owner_id / dept_id 支撑「我的 / 本部门 / 全部」行级过滤。
 */
@Service
class CrmService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    // ---------- 线索 ----------

    @Transactional(readOnly = true)
    fun leads(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        paged("lead", "l", "owner_id", q, status, scope, page, size,
            listOf("l.id", "l.no", "l.name", "l.source", "l.contact_name", "l.contact_phone", "l.status"),
            listOf("id", "no", "name", "source", "contact_name", "contact_phone", "status"))

    @Transactional
    fun createLead(req: LeadRequest): Map<String, Any?> {
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "线索名称不能为空")
        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("LD")
        jdbc.update(
            """
            INSERT INTO lead(id, no, name, source, contact_name, contact_phone, owner_id, dept_id, status, remark, created_by)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?::uuid, ?::uuid, 'following', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.name, req.source, req.contactName, req.contactPhone,
            me.employeeId?.toString(), me.deptId?.toString(), req.remark, me.userId.toString(),
        )
        audit.log("crm", "新建线索 $no（${req.name}）", "lead", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to "following")
    }

    /** 线索转客户：生成客户并回写线索状态 */
    @Transactional
    fun convertLead(id: UUID, level: String?, creditLimit: BigDecimal?, terms: Int?): Map<String, Any?> {
        val row = jdbc.queryForMap("SELECT * FROM lead WHERE id = ?::uuid FOR UPDATE", id.toString())
        if (row["status"]?.toString() == "converted") throw BizException(ErrorCode.BIZ_CONFLICT, "该线索已转化")

        val me = UserContext.get()
        val custId = UUID.randomUUID()
        val code = noGenerator.next("C")
        jdbc.update(
            """
            INSERT INTO customer(id, code, name, level, credit_limit, payment_terms, status, owner_id, dept_id)
            VALUES (?::uuid, ?, ?, ?, ?, ?, 'normal', ?::uuid, ?::uuid)
            """.trimIndent(),
            custId.toString(), code, row["name"]?.toString(), level ?: "C",
            creditLimit ?: BigDecimal.ZERO, terms ?: 0,
            row["owner_id"]?.toString() ?: me.employeeId?.toString(),
            row["dept_id"]?.toString() ?: me.deptId?.toString(),
        )
        jdbc.update(
            "UPDATE lead SET status='converted', converted_customer_id=?::uuid, updated_at=now() WHERE id=?::uuid",
            custId.toString(), id.toString(),
        )
        audit.log("crm", "线索 ${row["no"]} 转为客户 $code", "lead", id.toString())
        return mapOf("customerId" to custId.toString(), "customerCode" to code, "status" to "converted")
    }

    // ---------- 商机 ----------

    @Transactional(readOnly = true)
    fun opportunities(
        q: String?, status: String?, scope: String?, page: Int, size: Int,
    ): PageResult<Map<String, Any?>> = paged(
        "opportunity", "o", "owner_id", q, status, scope, page, size,
        listOf("o.id", "o.no", "o.name", "c.name AS customer_name", "o.stage", "o.amount", "o.expect_close_date", "o.status"),
        listOf("id", "no", "name", "customer_name", "stage", "amount", "expect_close_date", "status"),
        join = "LEFT JOIN customer c ON c.id = o.customer_id",
        statusCol = "o.status",
    )

    @Transactional
    fun createOpportunity(req: OpportunityRequest): Map<String, Any?> {
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商机名称不能为空")
        val me = UserContext.get()
        val custName = req.customerId?.let { nameOf("customer", it) }
        val id = UUID.randomUUID()
        val no = noGenerator.next("OP")
        jdbc.update(
            """
            INSERT INTO opportunity(id, no, name, customer_id, customer_name, owner_id, dept_id,
                                    stage, amount, expect_close_date, status, remark, created_by)
            VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::uuid, ?::uuid, ?, ?, ?::date, 'open', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.name, req.customerId, custName,
            me.employeeId?.toString(), me.deptId?.toString(),
            req.stage, req.amount, req.expectCloseDate, req.remark, me.userId.toString(),
        )
        audit.log("crm", "新建商机 $no（${req.name}）", "opportunity", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to "open")
    }

    /** 推进阶段：won/lost 同时收敛 status */
    @Transactional
    fun updateStage(id: UUID, stage: String, lostReason: String?) {
        val status = when (stage) {
            "won" -> "won"
            "lost" -> "lost"
            else -> "open"
        }
        if (stage == "lost" && lostReason.isNullOrBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "输单需填写原因")
        }
        jdbc.update(
            "UPDATE opportunity SET stage=?, status=?, lost_reason=?, updated_at=now() WHERE id=?::uuid",
            stage, status, lostReason, id.toString(),
        )
        audit.log("crm", "商机阶段更新为 $stage", "opportunity", id.toString())
    }

    /** 商机转合同 */
    @Transactional
    fun convertToContract(id: UUID): Map<String, Any?> {
        val row = jdbc.queryForMap("SELECT * FROM opportunity WHERE id = ?::uuid FOR UPDATE", id.toString())
        if (row["status"]?.toString() == "lost") throw BizException(ErrorCode.BIZ_CONFLICT, "输单商机不能转合同")
        val existed = jdbc.queryForList("SELECT no FROM biz_contract WHERE opportunity_id = ?::uuid", id.toString())
        if (existed.isNotEmpty()) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "该商机已转合同：${existed.first()["no"]}")
        }
        val no = noGenerator.next("CT")
        val cid = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO biz_contract(id, no, name, customer_id, customer_name, opportunity_id,
                                     amount, sign_date, status, owner_id, dept_id)
            VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::uuid, ?, CURRENT_DATE, 'effective', ?::uuid, ?::uuid)
            """.trimIndent(),
            cid.toString(), no, row["name"]?.toString(), row["customer_id"]?.toString(),
            row["customer_name"]?.toString(), id.toString(), row["amount"] ?: BigDecimal.ZERO,
            row["owner_id"]?.toString(), row["dept_id"]?.toString(),
        )
        jdbc.update(
            "UPDATE opportunity SET status='won', stage='won', updated_at=now() WHERE id=?::uuid", id.toString()
        )
        audit.log("crm", "商机 ${row["no"]} 转为合同 $no", "opportunity", id.toString())
        return mapOf("contractId" to cid.toString(), "contractNo" to no, "status" to "won")
    }

    // ---------- 合同 ----------

    @Transactional(readOnly = true)
    fun contracts(q: String?, status: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        paged(
            "biz_contract", "t", "owner_id", q, status, scope, page, size,
            listOf("t.id", "t.no", "t.name", "t.customer_name", "t.amount", "t.sign_date", "t.end_date", "t.payment_terms", "t.status"),
            listOf("id", "no", "name", "customer_name", "amount", "sign_date", "end_date", "payment_terms", "status"),
        )

    @Transactional
    fun createContract(req: ContractRequest): Map<String, Any?> {
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "合同名称不能为空")
        if (req.customerId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "客户不能为空")
        val me = UserContext.get()
        val custName = nameOf("customer", req.customerId)
        val id = UUID.randomUUID()
        val no = req.name.let { noGenerator.next("CT") }
        jdbc.update(
            """
            INSERT INTO biz_contract(id, no, name, customer_id, customer_name, opportunity_id, amount,
                                     sign_date, start_date, end_date, payment_terms, status,
                                     owner_id, dept_id, remark, created_by)
            VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::uuid, ?, ?::date, ?::date, ?::date, ?, ?,
                    ?::uuid, ?::uuid, ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.name, req.customerId, custName, req.opportunityId, req.amount,
            req.signDate ?: LocalDate.now().toString(), req.startDate, req.endDate,
            req.paymentTerms, req.status,
            me.employeeId?.toString(), me.deptId?.toString(), req.remark, me.userId.toString(),
        )
        audit.log("crm", "登记合同 $no（${req.name}）", "biz_contract", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to req.status)
    }

    /** 合同到期扫描：将已过期合同置为 expired（供定时任务或手动触发） */
    @Transactional
    fun markExpired(): Int = jdbc.update(
        """
        UPDATE biz_contract SET status='expired', updated_at=now()
        WHERE deleted_at IS NULL AND status IN ('effective','expiring') AND end_date < CURRENT_DATE
        """.trimIndent()
    )

    // ---------- 通用分页 ----------

    private fun paged(
        table: String,
        alias: String,
        ownerCol: String,
        q: String?,
        status: String?,
        scope: String?,
        page: Int,
        size: Int,
        selectCols: List<String>,
        outKeys: List<String>,
        join: String = "",
        statusCol: String = "$alias.status",
    ): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, table)
        val (pg, pgSize) = Paging.clamp(page, size)
        val kw = Paging.keyword(q)

        val cond = StringBuilder("WHERE $alias.deleted_at IS NULL ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND $alias.$ownerCol = ?::uuid ")
                args.add(me.employeeId?.toString() ?: ZERO_ID)
            }
            "dept" -> {
                cond.append("AND $alias.dept_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: ZERO_ID)
            }
        }
        if (kw != null) {
            cond.append("AND ($alias.no ILIKE ? OR $alias.name ILIKE ?) ")
            args.add("%$kw%")
            args.add("%$kw%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND $statusCol = ? ")
            args.add(status)
        }

        val from = "FROM $table $alias $join $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT ${selectCols.joinToString(", ")} $from ORDER BY $alias.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        )
        val list = rows.map { row ->
            outKeys.associateWith { k -> row[k]?.toString() }
        }
        return PageResult.of(list, total, pg, pgSize)
    }

    private fun nameOf(table: String, id: String): String? = runCatching {
        jdbc.queryForObject("SELECT name FROM $table WHERE id = ?::uuid", String::class.java, id)
    }.getOrNull()
}
