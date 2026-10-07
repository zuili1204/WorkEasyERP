package com.workeasy.erp.workflow

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.LoginUser
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import com.workeasy.erp.workflow.entity.LeaveRequest
import com.workeasy.erp.workflow.repository.LeaveRepository
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class LeaveSubmitRequest(
    val leaveType: String = "annual",
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val duration: BigDecimal? = null,
    val reason: String? = null,
)

data class LeaveDto(
    val id: String,
    val orderNo: String? = null,
    val employeeName: String? = null,
    val leaveType: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val duration: String? = null,
    val reason: String? = null,
    val status: String = "pending",
    val createdAt: String? = null,
)

/** 请假：M0 审批闭环的业务载体 —— 提交即发起流程，流程结束通过事件回写状态 */
@Service
class LeaveService(
    private val leaveRepo: LeaveRepository,
    private val workflowService: WorkflowService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
    private val jdbc: JdbcTemplate,
) {

    @Transactional
    fun submit(req: LeaveSubmitRequest): LeaveDto {
        val me = UserContext.get()
        val empId = me.employeeId
            ?: throw BizException(ErrorCode.BIZ_PARAM, "当前账号未关联员工档案，无法提交请假")
        val days = req.duration ?: BigDecimal.ZERO
        val no = noGenerator.next("LV")

        val entity = leaveRepo.save(
            LeaveRequest(
                orderNo = no,
                employeeId = empId,
                leaveType = req.leaveType,
                startAt = req.startAt,
                endAt = req.endAt,
                duration = days,
                reason = req.reason,
                status = "pending",
                createdAt = Instant.now(),
            )
        )

        val instId = workflowService.start(
            StartRequest(
                bizType = "leave",
                bizId = entity.id!!,
                title = "请假申请 · ${me.displayName} · ${days}天",
                formData = mapOf(
                    "orderNo" to no,
                    "days" to days.toPlainString(),
                    "type" to req.leaveType,
                ),
            ),
            me,
        )
        entity.workflowInstanceId = instId
        leaveRepo.save(entity)

        audit.log("leave", "提交请假单 $no", "leave_request", entity.id.toString())
        return LeaveDto(
            id = entity.id.toString(),
            orderNo = no,
            employeeName = me.displayName,
            leaveType = req.leaveType,
            startAt = req.startAt?.toString(),
            endAt = req.endAt?.toString(),
            duration = days.toPlainString(),
            reason = req.reason,
            status = "pending",
        )
    }

    @Transactional(readOnly = true)
    fun list(q: String?, scope: String?, status: String?, page: Int, size: Int): PageResult<LeaveDto> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "leave")

        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        when (eff) {
            "mine" -> {
                cond.append("AND l.employee_id = ?::uuid ")
                args.add(me.employeeId?.toString() ?: UUID(0, 0).toString())
            }
            "dept" -> {
                cond.append("AND e.department_id = ?::uuid ")
                args.add(me.deptId?.toString() ?: UUID(0, 0).toString())
            }
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (l.order_no ILIKE ? OR e.real_name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND l.status = ? ")
            args.add(status)
        }

        val from = "FROM leave_request l LEFT JOIN employee e ON e.id = l.employee_id $cond"
        val total = jdbc.queryForObject(
            "SELECT count(*) $from", Long::class.java, *args.toTypedArray()
        ) ?: 0

        val rows = jdbc.queryForList(
            """
            SELECT l.id, l.order_no, e.real_name, l.leave_type, l.start_at, l.end_at,
                   l.duration, l.reason, l.status, l.created_at
            $from
            ORDER BY l.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )

        return PageResult.of(
            list = rows.map {
                LeaveDto(
                    id = it["id"].toString(),
                    orderNo = it["order_no"]?.toString(),
                    employeeName = it["real_name"]?.toString(),
                    leaveType = it["leave_type"]?.toString(),
                    startAt = it["start_at"]?.toString(),
                    endAt = it["end_at"]?.toString(),
                    duration = it["duration"]?.toString(),
                    reason = it["reason"]?.toString(),
                    status = it["status"]?.toString() ?: "pending",
                    createdAt = it["created_at"]?.toString(),
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    /** 驳回 / 撤销后重新提交：复用原单据，重置状态并重新发起审批 */
    @Transactional
    fun resubmit(id: UUID, req: LeaveSubmitRequest): LeaveDto {
        val me = UserContext.get()
        val leave = leaveRepo.findById(id).orElseThrow { BizException(ErrorCode.BIZ_NOT_FOUND, "请假单不存在") }
        if (leave.status != "rejected" && leave.status != "canceled") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅被驳回或已撤销的单据可重新提交")
        }
        if (leave.employeeId != me.employeeId) throw BizException(ErrorCode.AUTH_FORBIDDEN, "只能重新提交本人的单据")

        val days = req.duration ?: BigDecimal.ZERO
        leave.leaveType = req.leaveType
        leave.startAt = req.startAt
        leave.endAt = req.endAt
        leave.duration = days
        leave.reason = req.reason
        leave.status = "pending"
        leaveRepo.save(leave)

        val instId = workflowService.start(
            StartRequest(
                bizType = "leave",
                bizId = leave.id!!,
                title = "请假申请 · ${me.displayName} · ${days}天",
                formData = mapOf(
                    "orderNo" to (leave.orderNo ?: ""),
                    "days" to days.toPlainString(),
                    "type" to req.leaveType,
                ),
            ),
            me,
        )
        leave.workflowInstanceId = instId
        leaveRepo.save(leave)

        audit.log("leave", "重新提交请假单 ${leave.orderNo}", "leave_request", id.toString())
        return toDto(leave, me.displayName)
    }

    /** 撤销：进行中的流程一并结束，单据归档为 canceled */
    @Transactional
    fun cancel(id: UUID) {
        val me = UserContext.get()
        val leave = leaveRepo.findById(id).orElseThrow { BizException(ErrorCode.BIZ_NOT_FOUND, "请假单不存在") }
        if (leave.status == "canceled") throw BizException(ErrorCode.BIZ_CONFLICT, "单据已撤销")
        if (leave.employeeId != me.employeeId) throw BizException(ErrorCode.AUTH_FORBIDDEN, "只能撤销本人的单据")

        if (leave.status == "pending") workflowService.cancelInstance(leave.workflowInstanceId, me)
        leave.status = "canceled"
        leaveRepo.save(leave)
        audit.log("leave", "撤销请假单 ${leave.orderNo}", "leave_request", id.toString())
    }

    private fun toDto(l: LeaveRequest, name: String? = null) = LeaveDto(
        id = l.id.toString(),
        orderNo = l.orderNo,
        employeeName = name,
        leaveType = l.leaveType,
        startAt = l.startAt?.toString(),
        endAt = l.endAt?.toString(),
        duration = l.duration?.toPlainString(),
        reason = l.reason,
        status = l.status,
    )

    /** 流程结束 → 回写请假单状态（事件驱动，引擎与业务解耦） */
    @EventListener
    fun onFinished(e: WorkflowFinishedEvent) {
        if (e.bizType != "leave") return
        val leave = leaveRepo.findById(e.bizId).orElse(null) ?: return
        val approved = e.result == "approved"
        leave.status = if (approved) "approved" else "rejected"
        leaveRepo.save(leave)
        audit.log(
            "leave",
            "请假单 ${leave.orderNo} 审批${if (approved) "通过" else "驳回"}",
            "leave_request",
            e.bizId.toString(),
        )
    }
}
