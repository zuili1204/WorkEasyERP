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
 * 通用假勤申请：加班 / 补卡 / 外出 / 出差
 * 四类单据结构相近（均走审批），用配置驱动避免重复四套代码。
 */
private const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"

private data class OaConfig(
    val table: String,
    val prefix: String?,
    val title: String,
    val fields: List<String>,
) {
    val hasOrderNo: Boolean get() = prefix != null
}

private val OA_CONFIG: Map<String, OaConfig> = mapOf(
    "overtime" to OaConfig("overtime_request", "OT", "加班申请", listOf("applyDate", "startAt", "endAt", "duration", "reason")),
    "appeal" to OaConfig("punch_appeal", null, "补卡申诉", listOf("workDate", "punchType", "appealReason")),
    "outing" to OaConfig("outing_request", "OUT", "外出申请", listOf("startAt", "endAt", "destination", "reason")),
    "trip" to OaConfig("business_trip", "TRIP", "出差申请", listOf("startDate", "endDate", "destination", "reason")),
)

/** 前端字段 → (列名, 占位符) */
private val FIELD_SQL: Map<String, Pair<String, String>> = mapOf(
    "applyDate" to ("apply_date" to "?::date"),
    "workDate" to ("work_date" to "?::date"),
    "startDate" to ("start_date" to "?::date"),
    "endDate" to ("end_date" to "?::date"),
    "startAt" to ("start_at" to "?::timestamptz"),
    "endAt" to ("end_at" to "?::timestamptz"),
    "duration" to ("duration" to "?::numeric"),
    "destination" to ("destination" to "?"),
    "reason" to ("reason" to "?"),
    "appealReason" to ("appeal_reason" to "?"),
    "punchType" to ("punch_type" to "?"),
)

@Service
class OaApplyService(
    private val jdbc: JdbcTemplate,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    val types: List<String> get() = OA_CONFIG.keys.toList()

    @Transactional
    fun submit(bizType: String, payload: Map<String, Any?>): Map<String, Any?> {
        val cfg = OA_CONFIG[bizType] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的申请类型：$bizType")
        val me = UserContext.get()
        val empId = me.employeeId ?: throw BizException(ErrorCode.BIZ_PARAM, "当前账号未关联员工档案")
        val id = UUID.randomUUID()
        val no = cfg.prefix?.let { noGenerator.next(it) }

        val cols = mutableListOf("id", "employee_id", "status", "created_at")
        val phs = mutableListOf("?::uuid", "?::uuid", "?", "now()")
        val args = mutableListOf<Any?>(id.toString(), empId.toString(), "pending")
        if (no != null) {
            cols += "order_no"
            phs += "?"
            args += no
        }
        cfg.fields.forEach { f ->
            val (col, ph) = FIELD_SQL[f] ?: return@forEach
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

        val instId = workflowService.start(
            StartRequest(
                bizType = bizType,
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
        audit.log("oa", "提交${cfg.title}${no?.let { " $it" } ?: ""}", cfg.table, id.toString())
        return mapOf("id" to id.toString(), "orderNo" to no, "status" to "pending")
    }

    @Transactional(readOnly = true)
    fun list(
        bizType: String,
        q: String?,
        scope: String?,
        page: Int,
        size: Int,
    ): PageResult<Map<String, Any?>> {
        val cfg = OA_CONFIG[bizType] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的申请类型：$bizType")
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, bizType)

        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND t.employee_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: ZERO_UUID)
            }
            "dept" -> {
                cond.append("AND e.department_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: ZERO_UUID)
            }
        }
        if (!q.isNullOrBlank()) {
            if (cfg.hasOrderNo) {
                cond.append("AND (t.order_no ILIKE ? OR e.real_name ILIKE ?) ")
                args.add("%$q%")
                args.add("%$q%")
            } else {
                cond.append("AND e.real_name ILIKE ? ")
                args.add("%$q%")
            }
        }

        val from = "FROM ${cfg.table} t LEFT JOIN employee e ON e.id = t.employee_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT t.*, e.real_name AS employee_name $from ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    /** 流程结束 → 回写对应假勤单据状态 */
    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        val cfg = OA_CONFIG[e.bizType] ?: return
        val status = if (e.result == "approved") "approved" else "rejected"
        jdbc.update("UPDATE ${cfg.table} SET status = ? WHERE id = ?::uuid", status, e.bizId.toString())
        audit.log(
            "oa",
            "${cfg.title} 审批${if (e.result == "approved") "通过" else "驳回"}",
            cfg.table,
            e.bizId.toString(),
        )
    }
}
