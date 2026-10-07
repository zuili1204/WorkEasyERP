package com.workeasy.erp.workflow.repository

import com.workeasy.erp.workflow.entity.LeaveRequest
import com.workeasy.erp.workflow.entity.Notification
import com.workeasy.erp.workflow.entity.NotificationRecipient
import com.workeasy.erp.workflow.entity.WorkflowComment
import com.workeasy.erp.workflow.entity.WorkflowDefinition
import com.workeasy.erp.workflow.entity.WorkflowInstance
import com.workeasy.erp.workflow.entity.WorkflowNode
import com.workeasy.erp.workflow.entity.WorkflowTask
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WorkflowDefinitionRepository : JpaRepository<WorkflowDefinition, UUID> {
    fun findByCodeAndStatus(code: String, status: String): WorkflowDefinition?
}

@Repository
interface WorkflowNodeRepository : JpaRepository<WorkflowNode, UUID> {
    fun findByDefinitionIdOrderByOrderIdxAsc(definitionId: UUID): List<WorkflowNode>
}

@Repository
interface WorkflowInstanceRepository : JpaRepository<WorkflowInstance, UUID>

@Repository
interface WorkflowTaskRepository : JpaRepository<WorkflowTask, UUID> {
    fun findByInstanceIdAndStatus(instanceId: UUID, status: String): List<WorkflowTask>
}

@Repository
interface WorkflowCommentRepository : JpaRepository<WorkflowComment, UUID> {
    fun findByInstanceIdOrderByCreatedAtAsc(instanceId: UUID): List<WorkflowComment>
}

@Repository
interface LeaveRepository : JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest>

@Repository
interface NotificationRepository : JpaRepository<Notification, UUID>

@Repository
interface NotificationRecipientRepository : JpaRepository<NotificationRecipient, UUID>
