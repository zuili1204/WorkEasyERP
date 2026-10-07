package com.workeasy.erp.workflow.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workflow_definition")
class WorkflowDefinition(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var code: String = "",

    var name: String = "",

    @Column(name = "biz_type")
    var bizType: String = "",

    var version: Int = 1,

    @Column(name = "form_schema")
    @JdbcTypeCode(SqlTypes.JSON)
    var formSchema: String? = null,

    var status: String = "active",

    var remark: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "workflow_node")
class WorkflowNode(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "definition_id")
    var definitionId: UUID? = null,

    @Column(name = "node_key")
    var nodeKey: String = "",

    var name: String? = null,

    @Column(name = "node_type")
    var nodeType: String = "approve",

    @Column(name = "approver_type")
    var approverType: String? = null,

    @Column(name = "approver_rule")
    @JdbcTypeCode(SqlTypes.JSON)
    var approverRule: String = "{}",

    /** 条件节点：{"field":"amount","op":"gte","value":100000} */
    @Column(name = "condition")
    @JdbcTypeCode(SqlTypes.JSON)
    var condition: String? = null,

    /** 条件成立 → 走该 node_key；为空则顺序继续 */
    @Column(name = "next_node_key")
    var nextNodeKey: String? = null,

    /** 条件不成立 → 走该 node_key；为空则顺序继续 */
    @Column(name = "else_node_key")
    var elseNodeKey: String? = null,

    @Column(name = "can_transfer")
    var canTransfer: Boolean = true,

    @Column(name = "order_idx")
    var orderIdx: Int = 0,
)

@Entity
@Table(name = "workflow_instance")
class WorkflowInstance(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "definition_id")
    var definitionId: UUID? = null,

    @Column(name = "biz_type")
    var bizType: String = "",

    @Column(name = "biz_id")
    var bizId: UUID? = null,

    var title: String? = null,

    @Column(name = "initiator_id")
    var initiatorId: UUID? = null,

    @Column(name = "dept_id")
    var deptId: UUID? = null,

    var status: String = "running",

    @Column(name = "current_node_id")
    var currentNodeId: UUID? = null,

    @Column(name = "form_data")
    @JdbcTypeCode(SqlTypes.JSON)
    var formData: String? = null,

    var result: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
)

@Entity
@Table(name = "workflow_task")
class WorkflowTask(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "instance_id")
    var instanceId: UUID? = null,

    @Column(name = "node_id")
    var nodeId: UUID? = null,

    @Column(name = "assignee_id")
    var assigneeId: UUID? = null,

    @Column(name = "assignee_role")
    var assigneeRole: String? = null,

    var status: String = "pending",

    @Column(name = "acted_at")
    var actedAt: Instant? = null,

    var comment: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "workflow_comment")
class WorkflowComment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "instance_id")
    var instanceId: UUID? = null,

    @Column(name = "task_id")
    var taskId: UUID? = null,

    @Column(name = "user_id")
    var userId: UUID? = null,

    var action: String? = null,

    var content: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "leave_request")
class LeaveRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "order_no")
    var orderNo: String? = null,

    @Column(name = "employee_id")
    var employeeId: UUID? = null,

    @Column(name = "leave_type")
    var leaveType: String? = null,

    @Column(name = "start_at")
    var startAt: Instant? = null,

    @Column(name = "end_at")
    var endAt: Instant? = null,

    var duration: BigDecimal? = null,

    @Column(name = "half_day")
    var halfDay: Boolean = false,

    var reason: String? = null,

    var status: String = "pending",

    @Column(name = "workflow_instance_id")
    var workflowInstanceId: UUID? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "notification")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var type: String = "system",

    @Column(name = "template_code")
    var templateCode: String? = null,

    var title: String? = null,

    var content: String? = null,

    @Column(name = "sender_id")
    var senderId: UUID? = null,

    @Column(name = "biz_type")
    var bizType: String? = null,

    @Column(name = "biz_id")
    var bizId: UUID? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,
)

@Entity
@Table(name = "notification_recipient")
class NotificationRecipient(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "notification_id")
    var notificationId: UUID? = null,

    @Column(name = "user_id")
    var userId: UUID? = null,

    var channel: String = "inapp",

    var status: String = "unread",

    @Column(name = "read_at")
    var readAt: Instant? = null,
)
