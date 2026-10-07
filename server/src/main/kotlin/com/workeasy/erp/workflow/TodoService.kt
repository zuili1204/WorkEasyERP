package com.workeasy.erp.workflow

import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.LoginUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

data class TodoDto(
    val id: String,
    val title: String? = null,
    val bizType: String? = null,
    val bizId: String? = null,
    val nodeName: String? = null,
    val status: String = "pending",
    val createdAt: String? = null,
    /** true=可操作任务（待我办），false=只读记录（我提交的/已办结/抄送我） */
    val actionable: Boolean = false,
)

/** 待办中心四视图：我提交的 / 待我办 / 已办结 / 抄送我 */
@Service
class TodoService(private val jdbc: JdbcTemplate) {

    fun list(view: String, me: LoginUser, page: Int, size: Int): PageResult<TodoDto> {
        val uid = me.userId.toString()
        return when (view) {
            "submitted" -> pageQuery(
                """
                SELECT i.id AS id, i.title, i.biz_type, i.biz_id::text AS biz_id, NULL AS node_name,
                       i.status, i.created_at, false AS actionable
                FROM workflow_instance i WHERE i.initiator_id = ?::uuid
                """.trimIndent(),
                listOf(uid), page, size, "i.created_at DESC",
            )

            "pending" -> pageQuery(
                """
                SELECT t.id AS id, i.title, i.biz_type, i.biz_id::text AS biz_id, n.name AS node_name,
                       t.status, t.created_at, true AS actionable
                FROM workflow_task t
                JOIN workflow_instance i ON i.id = t.instance_id
                LEFT JOIN workflow_node n ON n.id = t.node_id
                WHERE t.assignee_id = ?::uuid AND t.status = 'pending'
                """.trimIndent(),
                listOf(uid), page, size, "t.created_at DESC",
            )

            "done" -> pageQuery(
                """
                SELECT t.id AS id, i.title, i.biz_type, i.biz_id::text AS biz_id, n.name AS node_name,
                       t.status, t.acted_at AS created_at, false AS actionable
                FROM workflow_task t
                JOIN workflow_instance i ON i.id = t.instance_id
                LEFT JOIN workflow_node n ON n.id = t.node_id
                WHERE t.assignee_id = ?::uuid AND t.status IN ('approved','rejected','transferred','skipped')
                """.trimIndent(),
                listOf(uid), page, size, "t.acted_at DESC",
            )

            "cc" -> pageQuery(
                """
                SELECT n.id AS id, n.title, n.biz_type, NULL AS biz_id, '抄送' AS node_name,
                       nr.status, n.created_at, false AS actionable
                FROM notification_recipient nr
                JOIN notification n ON n.id = nr.notification_id
                WHERE nr.user_id = ?::uuid AND n.type = 'cc'
                """.trimIndent(),
                listOf(uid), page, size, "n.created_at DESC",
            )

            else -> list("pending", me, page, size)
        }
    }

    private fun pageQuery(
        sql: String,
        args: List<Any>,
        page: Int,
        size: Int,
        order: String,
    ): PageResult<TodoDto> {
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM ($sql) x", Long::class.java, *args.toTypedArray()
        ) ?: 0
        val rows = jdbc.queryForList(
            "$sql ORDER BY $order LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(
            list = rows.map {
                TodoDto(
                    id = it["id"].toString(),
                    title = it["title"]?.toString(),
                    bizType = it["biz_type"]?.toString(),
                    bizId = it["biz_id"]?.toString(),
                    nodeName = it["node_name"]?.toString(),
                    status = it["status"]?.toString() ?: "pending",
                    createdAt = it["created_at"]?.toString(),
                    actionable = (it["actionable"] as? Boolean) ?: false,
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }
}
