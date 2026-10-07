package com.workeasy.erp.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.LoginUser
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.workflow.entity.WorkflowComment
import com.workeasy.erp.workflow.entity.WorkflowInstance
import com.workeasy.erp.workflow.entity.WorkflowNode
import com.workeasy.erp.workflow.entity.WorkflowTask
import com.workeasy.erp.workflow.repository.WorkflowCommentRepository
import com.workeasy.erp.workflow.repository.WorkflowDefinitionRepository
import com.workeasy.erp.workflow.repository.WorkflowInstanceRepository
import com.workeasy.erp.workflow.repository.WorkflowNodeRepository
import com.workeasy.erp.workflow.repository.WorkflowTaskRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** 流程结束事件：业务模块监听后回写自身状态（避免与引擎循环依赖） */
data class WorkflowFinishedEvent(val bizType: String, val bizId: UUID, val result: String)

data class StartRequest(
    val bizType: String,
    val bizId: UUID,
    val title: String,
    val formData: Map<String, Any?>? = null,
)

@Service
class WorkflowService(
    private val defRepo: WorkflowDefinitionRepository,
    private val nodeRepo: WorkflowNodeRepository,
    private val instRepo: WorkflowInstanceRepository,
    private val taskRepo: WorkflowTaskRepository,
    private val commentRepo: WorkflowCommentRepository,
    private val approverResolver: ApproverResolver,
    private val noticeService: NoticeService,
    private val audit: AuditService,
    private val publisher: ApplicationEventPublisher,
) {
    private val om = ObjectMapper()

    /** 发起流程：建实例 → 按条件路由走到首个可执行节点 → 生成任务并通知审批人 */
    @Transactional
    fun start(req: StartRequest, initiator: LoginUser): UUID {
        val def = defRepo.findByCodeAndStatus(req.bizType, "active")
            ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "未找到 ${req.bizType} 的流程定义")
        val nodes = nodeRepo.findByDefinitionIdOrderByOrderIdxAsc(def.id!!)
        if (nodes.isEmpty()) throw BizException(ErrorCode.BIZ_NOT_FOUND, "流程未配置任何节点")

        val inst = instRepo.save(
            WorkflowInstance(
                definitionId = def.id,
                bizType = req.bizType,
                bizId = req.bizId,
                title = req.title,
                initiatorId = initiator.userId,
                deptId = initiator.deptId,
                status = "running",
                currentNodeId = null,
                formData = om.writeValueAsString(req.formData ?: emptyMap<String, Any?>()),
                createdAt = Instant.now(),
            )
        )
        // 立即落库：业务单（部分用 JdbcTemplate 更新）会通过外键引用流程实例
        instRepo.flush()
        audit.log("workflow", "发起审批：${req.title}", "workflow_instance", inst.id?.toString())

        // 从虚拟起点推进：消化起始处的条件与抄送，落到首个审批节点
        var cursor: WorkflowNode? = null
        repeat(nodes.size + 5) {
            val next = nextNode(nodes, cursor, inst)
                ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "流程未配置审批节点")
            inst.currentNodeId = next.id
            instRepo.save(inst)
            when (next.nodeType) {
                "end" -> { finish(inst, "approved"); return inst.id!! }
                "cc" -> sendCc(inst, next, initiator)
                else -> { createTasks(inst, next, initiator); return inst.id!! }
            }
            cursor = next
        }
        throw BizException(ErrorCode.BIZ_NOT_FOUND, "流程未配置审批节点")
    }

    @Transactional
    fun approve(taskId: UUID, comment: String?, operator: LoginUser) {
        val task = pendingTask(taskId, operator)
        val now = Instant.now()
        task.status = "approved"
        task.actedAt = now
        task.comment = comment
        taskRepo.save(task)
        commentRepo.save(
            WorkflowComment(
                instanceId = task.instanceId,
                taskId = taskId,
                userId = operator.userId,
                action = "approve",
                content = comment,
                createdAt = now,
            )
        )
        audit.log(
            "workflow",
            "审批通过：节点=${task.assigneeRole ?: "-"} 实例=${task.instanceId}",
            "workflow_task",
            taskId.toString(),
        )
        advance(task.instanceId!!, operator)
    }

    @Transactional
    fun reject(taskId: UUID, comment: String?, operator: LoginUser) {
        val task = pendingTask(taskId, operator)
        val now = Instant.now()
        task.status = "rejected"
        task.actedAt = now
        task.comment = comment
        taskRepo.save(task)
        commentRepo.save(
            WorkflowComment(
                instanceId = task.instanceId,
                taskId = taskId,
                userId = operator.userId,
                action = "reject",
                content = comment,
                createdAt = now,
            )
        )
        audit.log(
            "workflow",
            "审批驳回：节点=${task.assigneeRole ?: "-"} 实例=${task.instanceId}",
            "workflow_task",
            taskId.toString(),
        )
        val inst = instRepo.findById(task.instanceId!!).orElseThrow {
            BizException(ErrorCode.BIZ_NOT_FOUND, "流程实例不存在")
        }
        finish(inst, "rejected")
    }

    /** 转交：原任务置 transferred，为接收人新建待办 */
    @Transactional
    fun transfer(taskId: UUID, targetUserId: UUID, comment: String?, operator: LoginUser) {
        val task = pendingTask(taskId, operator)
        val now = Instant.now()
        task.status = "transferred"
        task.actedAt = now
        task.comment = comment
        taskRepo.save(task)

        taskRepo.save(
            WorkflowTask(
                instanceId = task.instanceId,
                nodeId = task.nodeId,
                assigneeId = targetUserId,
                assigneeRole = task.assigneeRole,
                status = "pending",
                createdAt = now,
            )
        )
        val inst = instRepo.findById(task.instanceId!!).orElseThrow {
            BizException(ErrorCode.BIZ_NOT_FOUND, "流程实例不存在")
        }
        noticeService.send(
            type = "todo",
            title = "转交待办：${inst.title}",
            content = "${operator.displayName} 将审批转交给您",
            senderId = operator.userId,
            bizType = inst.bizType,
            bizId = inst.bizId,
            userIds = listOf(targetUserId),
        )
        audit.log("workflow", "审批转交：${inst.title}", "workflow_task", taskId.toString())
    }

    private fun pendingTask(taskId: UUID, operator: LoginUser): WorkflowTask {
        val task = taskRepo.findById(taskId).orElseThrow {
            BizException(ErrorCode.BIZ_NOT_FOUND, "审批任务不存在")
        }
        if (task.status != "pending") throw BizException(ErrorCode.BIZ_CONFLICT, "该任务已处理")
        if (task.assigneeId != operator.userId) throw BizException(ErrorCode.AUTH_FORBIDDEN, "您不是当前审批人")
        return task
    }

    /** 按节点规则生成审批任务；未匹配到审批人则跳过该节点，避免卡单 */
    private fun createTasks(inst: WorkflowInstance, node: WorkflowNode, initiator: LoginUser) {
        val assignees = approverResolver.resolve(node.approverRule, initiator)
        if (assignees.isEmpty()) {
            advance(inst.id!!, initiator)
            return
        }
        val now = Instant.now()
        assignees.forEach { uid ->
            taskRepo.save(
                WorkflowTask(
                    instanceId = inst.id,
                    nodeId = node.id,
                    assigneeId = uid,
                    assigneeRole = node.nodeKey,
                    status = "pending",
                    createdAt = now,
                )
            )
        }
        noticeService.send(
            type = "todo",
            title = "待我审批：${inst.title}",
            content = "${initiator.displayName} 提交了「${inst.title}」，请处理",
            senderId = initiator.userId,
            bizType = inst.bizType,
            bizId = inst.bizId,
            userIds = assignees,
        )
    }

    /**
     * 推进到下一节点：
     * - `condition` 条件节点按判定结果路由，本身不产生任务
     * - `cc` 抄送只发消息不阻塞
     * - `end` 直接结束流程
     * 循环上限用于防御配置错误导致的无限回路，兜底按通过结束。
     */
    private fun advance(instanceId: UUID, initiator: LoginUser) {
        val inst = instRepo.findById(instanceId).orElseThrow {
            BizException(ErrorCode.BIZ_NOT_FOUND, "流程实例不存在")
        }
        val nodes = nodeRepo.findByDefinitionIdOrderByOrderIdxAsc(inst.definitionId!!)
        var cursor = nodes.firstOrNull { it.id == inst.currentNodeId }

        repeat(nodes.size + 5) {
            val next = nextNode(nodes, cursor, inst)
            if (next == null) {
                finish(inst, "approved")
                return
            }
            inst.currentNodeId = next.id
            instRepo.save(inst)
            when (next.nodeType) {
                "end" -> { finish(inst, "approved"); return }
                "cc" -> sendCc(inst, next, initiator)
                else -> { createTasks(inst, next, initiator); return }
            }
            cursor = next
        }
        finish(inst, "approved")
    }

    /**
     * 求下一个可执行节点：沿链路连续消化 condition 节点。
     * cursor 为 null 表示「虚拟起点」，取第一个节点。
     */
    private fun nextNode(nodes: List<WorkflowNode>, cursor: WorkflowNode?, inst: WorkflowInstance): WorkflowNode? {
        val data = formMap(inst)
        var next = nodes.getOrNull(nodes.indexOfFirst { it.id == cursor?.id } + 1)
        var guard = 0
        while (next != null && next.nodeType == "condition" && guard++ < 20) {
            val pass = ConditionEvaluator.eval(next.condition, data)
            val key = if (pass) next.nextNodeKey else next.elseNodeKey
            val target = key?.takeIf { it.isNotBlank() }?.let { k -> nodes.firstOrNull { it.nodeKey == k } }
            val fallback = nodes.getOrNull(nodes.indexOfFirst { it.id == next!!.id } + 1)
            audit.log(
                "workflow",
                "条件分支「${next.name ?: next.nodeKey}」判定${if (pass) "成立" else "不成立"}" +
                    "（${ConditionEvaluator.describe(next.condition)}）→ ${target?.name ?: fallback?.name ?: "结束"}",
                "workflow_instance",
                inst.id?.toString(),
            )
            next = target ?: fallback
        }
        return next
    }

    /** 表单数据：条件求值依据（业务侧经 StartRequest.formData 传入） */
    @Suppress("UNCHECKED_CAST")
    private fun formMap(inst: WorkflowInstance): Map<String, Any?> =
        inst.formData?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching { om.readValue(json, Map::class.java) as Map<String, Any?> }.getOrDefault(emptyMap())
        } ?: emptyMap()

    /** 抄送：给规则匹配的人发 cc 消息，不生成待办任务 */
    private fun sendCc(inst: WorkflowInstance, node: WorkflowNode, initiator: LoginUser) {
        val targets = approverResolver.resolve(node.approverRule, initiator)
        if (targets.isEmpty()) return
        noticeService.send(
            type = "cc",
            title = "抄送：${inst.title}",
            content = "${initiator.displayName} 的「${inst.title}」已进入${node.name ?: "抄送"}环节",
            senderId = initiator.userId,
            bizType = inst.bizType,
            bizId = inst.bizId,
            userIds = targets,
        )
        audit.log("workflow", "抄送：${inst.title} → ${node.name}", "workflow_instance", inst.id?.toString())
    }

    /** 撤销：结束进行中的流程，未处理任务置为 skipped */
    @Transactional
    fun cancelInstance(instanceId: UUID?, operator: LoginUser) {
        if (instanceId == null) return
        val inst = instRepo.findById(instanceId).orElse(null) ?: return
        if (inst.status != "running") return
        taskRepo.findByInstanceIdAndStatus(instanceId, "pending").forEach {
            it.status = "skipped"
            it.actedAt = Instant.now()
            taskRepo.save(it)
        }
        inst.status = "canceled"
        inst.finishedAt = Instant.now()
        instRepo.save(inst)
        audit.log("workflow", "撤销流程：${inst.title}", "workflow_instance", instanceId.toString())
    }

    private fun finish(inst: WorkflowInstance, result: String) {
        inst.status = result
        inst.result = result
        inst.finishedAt = Instant.now()
        instRepo.save(inst)

        // 事件驱动回写业务单状态（LeaveService 等监听）
        if (inst.bizId != null) {
            publisher.publishEvent(WorkflowFinishedEvent(inst.bizType, inst.bizId!!, result))
        }

        inst.initiatorId?.let { uid ->
            noticeService.send(
                type = "approve",
                title = "审批结果：${inst.title}",
                content = "您的「${inst.title}」已${if (result == "approved") "通过" else "驳回"}",
                senderId = null,
                bizType = inst.bizType,
                bizId = inst.bizId,
                userIds = listOf(uid),
            )
        }
        audit.log("workflow", "流程结束：${inst.title} → $result", "workflow_instance", inst.id?.toString())
    }
}
