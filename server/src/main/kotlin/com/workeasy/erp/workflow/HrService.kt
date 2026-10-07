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
import java.util.UUID

/**
 * 人事事件：配置驱动一套代码服务六类单据。
 * 转正 / 调岗 / 晋升 / 离职 走审批，通过后回写 employee；
 * 入职（HR 确认后建档）与劳动合同为确认类，不走审批。
 */
private const val ZERO = "00000000-0000-0000-0000-000000000000"

private data class HrConfig(
    val table: String,
    val prefix: String?,
    val title: String,
    val approval: Boolean,
    val fields: List<String>,
)

private val HR_CONFIG: Map<String, HrConfig> = mapOf(
    "regular" to HrConfig("hr_regularization", null, "转正申请", true,
        listOf("employeeId", "probationEnd", "applyDate", "regularDate", "evaluation", "remark")),
    "transfer" to HrConfig("hr_transfer", null, "调岗申请", true,
        listOf("employeeId", "fromDeptId", "toDeptId", "fromPosition", "toPosition", "effectiveDate", "reason")),
    "promo" to HrConfig("hr_promotion", null, "晋升申请", true,
        listOf("employeeId", "fromPosition", "toPosition", "fromLevel", "toLevel", "effectiveDate", "reason")),
    "dimission" to HrConfig("hr_dimission", null, "离职申请", true,
        listOf("employeeId", "type", "applyDate", "lastWorkDate", "reason", "handoverTo", "remark")),
    "entry" to HrConfig("hr_entry", "HR-ENT", "入职登记", false,
        listOf("candidateName", "phone", "expectedDeptId", "expectedPosition", "expectedEntryDate", "sourceChannel", "remark")),
    "contract" to HrConfig("hr_contract", "HT", "劳动合同", false,
        listOf("employeeId", "contractNo", "type", "startDate", "endDate", "signDate", "remark")),
)

private val HR_FIELD_SQL: Map<String, Pair<String, String>> = mapOf(
    "employeeId" to ("employee_id" to "?::uuid"),
    "fromDeptId" to ("from_dept_id" to "?::uuid"),
    "toDeptId" to ("to_dept_id" to "?::uuid"),
    "expectedDeptId" to ("expected_dept_id" to "?::uuid"),
    "handoverTo" to ("handover_to" to "?::uuid"),
    "probationEnd" to ("probation_end" to "?::date"),
    "applyDate" to ("apply_date" to "?::date"),
    "regularDate" to ("regular_date" to "?::date"),
    "effectiveDate" to ("effective_date" to "?::date"),
    "lastWorkDate" to ("last_work_date" to "?::date"),
    "expectedEntryDate" to ("expected_entry_date" to "?::date"),
    "startDate" to ("start_date" to "?::date"),
    "endDate" to ("end_date" to "?::date"),
    "signDate" to ("sign_date" to "?::date"),
    "fromPosition" to ("from_position" to "?"),
    "toPosition" to ("to_position" to "?"),
    "fromLevel" to ("from_level" to "?"),
    "toLevel" to ("to_level" to "?"),
    "type" to ("type" to "?"),
    "reason" to ("reason" to "?"),
    "evaluation" to ("evaluation" to "?"),
    "remark" to ("remark" to "?"),
    "candidateName" to ("candidate_name" to "?"),
    "phone" to ("phone" to "?"),
    "expectedPosition" to ("expected_position" to "?"),
    "sourceChannel" to ("source_channel" to "?"),
    "contractNo" to ("contract_no" to "?"),
)

@Service
class HrService(
    private val jdbc: JdbcTemplate,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    val types: List<String> get() = HR_CONFIG.keys.toList()

    @Transactional
    fun submit(kind: String, payload: Map<String, Any?>): Map<String, Any?> {
        val cfg = HR_CONFIG[kind] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的人事单据：$kind")
        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = cfg.prefix?.let { noGenerator.next(it) }

        val cols = mutableListOf("id", "status", "created_at")
        val phs = mutableListOf("?::uuid", "?", "now()")
        val args = mutableListOf<Any?>(id.toString(), "pending")

        // 员工类单据默认取当前登录员工
        if (cfg.table != "hr_entry") {
            val empId = payload["employeeId"]?.toString() ?: me.employeeId?.toString()
            cols += "employee_id"
            phs += "?::uuid"
            args += empId
        }
        if (no != null) {
            // 入职 → apply_no；合同 → contract_no（前端未填写时使用自动编号）
            val noCol = if (cfg.table == "hr_entry") "apply_no" else "contract_no"
            val provided = payload["contractNo"]?.toString()
            cols += noCol
            phs += "?"
            args += (provided?.takeIf { it.isNotBlank() } ?: no)
        }
        cfg.fields.forEach { f ->
            val (col, ph) = HR_FIELD_SQL[f] ?: return@forEach
            if (col == "employee_id" || col == "contract_no") return@forEach   // 已单独处理
            val v = payload[f]?.toString()
            if (!v.isNullOrBlank()) {
                cols += col
                phs += ph
                args += v
            }
        }

        jdbc.update(
            "INSERT INTO ${cfg.table} (${cols.joinToString(",")}) VALUES (${phs.joinToString(",")})",
            *args.toTypedArray(),
        )

        if (cfg.approval) {
            val instId = workflowService.start(
                StartRequest(
                    bizType = kind,
                    bizId = id,
                    title = "${cfg.title} · ${me.displayName}",
                    formData = payload + mapOf("orderNo" to no),
                ),
                me,
            )
            jdbc.update(
                "UPDATE ${cfg.table} SET workflow_instance_id = ?::uuid WHERE id = ?::uuid",
                instId.toString(),
                id.toString(),
            )
        }

        var resultStatus = "pending"
        if (!cfg.approval) {
            // 确认类单据：合同登记即生效、入职待 HR 确认
            resultStatus = if (cfg.table == "hr_contract") "active" else "pending"
            jdbc.update("UPDATE ${cfg.table} SET status = ? WHERE id = ?::uuid", resultStatus, id.toString())
        }

        audit.log("hr", "提交${cfg.title}${no?.let { " $it" } ?: ""}", cfg.table, id.toString())
        return mapOf("id" to id.toString(), "orderNo" to no, "status" to resultStatus)
    }

    @Transactional(readOnly = true)
    fun list(kind: String, q: String?, scope: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val cfg = HR_CONFIG[kind] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的人事单据：$kind")
        val me = UserContext.get()
        val eff = if (cfg.table == "hr_entry") "all" else scopeResolver.resolve(scope, me, "hr")

        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        if (cfg.table != "hr_entry") {
            when (eff) {
                "mine" -> {
                    cond.append("AND t.employee_id = ?::uuid ")
                    args.add(me.employeeId?.toString() ?: ZERO)
                }
                "dept" -> {
                    cond.append("AND e.department_id = ?::uuid ")
                    args.add(me.deptId?.toString() ?: ZERO)
                }
            }
        }
        if (!q.isNullOrBlank()) {
            if (cfg.table == "hr_entry") {
                cond.append("AND (t.candidate_name ILIKE ? OR t.apply_no ILIKE ?) ")
            } else {
                cond.append("AND (e.real_name ILIKE ? OR t.contract_no ILIKE ?) ")
            }
            args.add("%$q%")
            args.add("%$q%")
        }

        val join = if (cfg.table == "hr_entry") "" else "LEFT JOIN employee e ON e.id = t.employee_id"
        val select = if (cfg.table == "hr_entry") "t.*" else "t.*, e.real_name AS employee_name"
        val from = "FROM ${cfg.table} t $join $cond"

        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT $select $from ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    /** 入职确认：生成员工档案并回填 */
    @Transactional
    fun confirmEntry(id: UUID) {
        val row = jdbc.queryForMap("SELECT * FROM hr_entry WHERE id = ?::uuid", id.toString())
        if (row["status"]?.toString() == "confirmed") throw BizException(ErrorCode.BIZ_CONFLICT, "该入职单已确认")
        val me = UserContext.get()
        val empId = UUID.randomUUID()
        val empNo = noGenerator.next("E")

        jdbc.update(
            """
            INSERT INTO employee(id, employee_no, real_name, department_id, position, phone,
                                 hire_date, entry_status, status, created_at, updated_at)
            VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::date, CURRENT_DATE, 'probation', 'active', now(), now())
            """.trimIndent(),
            empId.toString(),
            empNo,
            row["candidate_name"]?.toString() ?: "新员工",
            row["expected_dept_id"]?.toString(),
            row["expected_position"]?.toString(),
            row["expected_entry_date"]?.toString(),
        )
        jdbc.update(
            """
            UPDATE hr_entry SET status='confirmed', employee_id=?::uuid, handler_id=?::uuid,
                   actual_entry_date = CURRENT_DATE
            WHERE id = ?::uuid
            """.trimIndent(),
            empId.toString(),
            me.employeeId?.toString(),
            id.toString(),
        )
        audit.log("hr", "入职确认并建档 $empNo", "hr_entry", id.toString())
    }

    /** 审批通过 → 回写员工档案 */
    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        val cfg = HR_CONFIG[e.bizType] ?: return
        val approved = e.result == "approved"
        val status = if (approved) "approved" else "rejected"
        jdbc.update("UPDATE ${cfg.table} SET status = ? WHERE id = ?::uuid", status, e.bizId.toString())
        if (!approved) {
            audit.log("hr", "${cfg.title} 审批驳回", cfg.table, e.bizId.toString())
            return
        }

        when (e.bizType) {
            "regular" -> {
                val d = jdbc.queryForMap("SELECT employee_id, regular_date FROM hr_regularization WHERE id = ?::uuid", e.bizId.toString())
                jdbc.update(
                    """
                    UPDATE employee SET entry_status='regular',
                           regular_date = COALESCE(?::date, regular_date, CURRENT_DATE)
                    WHERE id = ?::uuid
                    """.trimIndent(),
                    d["regular_date"]?.toString(),
                    d["employee_id"]?.toString(),
                )
            }
            "transfer" -> {
                val d = jdbc.queryForMap("SELECT employee_id, to_dept_id, to_position FROM hr_transfer WHERE id = ?::uuid", e.bizId.toString())
                jdbc.update(
                    "UPDATE employee SET department_id = ?::uuid, position = COALESCE(?::varchar, position) WHERE id = ?::uuid",
                    d["to_dept_id"]?.toString(),
                    d["to_position"]?.toString(),
                    d["employee_id"]?.toString(),
                )
            }
            "promo" -> {
                val d = jdbc.queryForMap("SELECT employee_id, to_position, to_level FROM hr_promotion WHERE id = ?::uuid", e.bizId.toString())
                jdbc.update(
                    "UPDATE employee SET position = COALESCE(?::varchar, position), job_title = COALESCE(?::varchar, job_title) WHERE id = ?::uuid",
                    d["to_position"]?.toString(),
                    d["to_level"]?.toString(),
                    d["employee_id"]?.toString(),
                )
            }
            "dimission" -> {
                val d = jdbc.queryForMap("SELECT employee_id, last_work_date FROM hr_dimission WHERE id = ?::uuid", e.bizId.toString())
                jdbc.update(
                    "UPDATE employee SET status='left', leave_date = COALESCE(?::date, CURRENT_DATE) WHERE id = ?::uuid",
                    d["last_work_date"]?.toString(),
                    d["employee_id"]?.toString(),
                )
            }
        }
        audit.log("hr", "${cfg.title} 审批通过并回写员工档案", cfg.table, e.bizId.toString())
    }
}
