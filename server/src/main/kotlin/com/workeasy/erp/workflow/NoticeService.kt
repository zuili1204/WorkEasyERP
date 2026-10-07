package com.workeasy.erp.workflow

import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.entity.Notification
import com.workeasy.erp.workflow.entity.NotificationRecipient
import com.workeasy.erp.workflow.repository.NotificationRecipientRepository
import com.workeasy.erp.workflow.repository.NotificationRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class NoticeDto(
    val id: String,
    val type: String,
    val title: String?,
    val content: String?,
    val bizType: String?,
    val bizId: String?,
    val status: String,
    val createdAt: String?,
)

@Service
class NoticeService(
    private val noticeRepo: NotificationRepository,
    private val recipientRepo: NotificationRecipientRepository,
    private val jdbc: JdbcTemplate,
) {

    /** 发送站内消息：写 notification + 每个收件人一条 recipient */
    @Transactional
    fun send(
        type: String,
        title: String,
        content: String,
        senderId: UUID?,
        bizType: String?,
        bizId: UUID?,
        userIds: List<UUID>,
    ) {
        val targets = userIds.distinct()
        if (targets.isEmpty()) return

        val n = noticeRepo.save(
            Notification(
                type = type,
                title = title,
                content = content,
                senderId = senderId,
                bizType = bizType,
                bizId = bizId,
                createdAt = Instant.now(),
            )
        )
        targets.forEach { uid ->
            recipientRepo.save(NotificationRecipient(notificationId = n.id, userId = uid))
        }
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID, onlyUnread: Boolean?, page: Int, size: Int): PageResult<NoticeDto> {
        val where = StringBuilder("WHERE nr.user_id = ?::uuid ")
        val args = mutableListOf<Any>(userId.toString())
        if (onlyUnread == true) {
            where.append("AND nr.status = 'unread' ")
        }
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM notification_recipient nr JOIN notification n ON n.id = nr.notification_id $where",
            Long::class.java,
            *args.toTypedArray(),
        ) ?: 0

        val rows = jdbc.queryForList(
            """
            SELECT n.id, n.type, n.title, n.content, n.biz_type, n.biz_id, nr.status, n.created_at
            FROM notification_recipient nr JOIN notification n ON n.id = nr.notification_id
            $where
            ORDER BY n.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )

        return PageResult.of(
            list = rows.map {
                NoticeDto(
                    id = it["id"].toString(),
                    type = it["type"]?.toString() ?: "system",
                    title = it["title"]?.toString(),
                    content = it["content"]?.toString(),
                    bizType = it["biz_type"]?.toString(),
                    bizId = it["biz_id"]?.toString(),
                    status = it["status"]?.toString() ?: "unread",
                    createdAt = it["created_at"]?.toString(),
                )
            },
            total = total,
            page = page,
            size = size,
        )
    }

    fun unreadCount(userId: UUID): Long =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_recipient WHERE user_id = ?::uuid AND status = 'unread'",
            Long::class.java,
            userId.toString(),
        ) ?: 0

    @Transactional
    fun markRead(userId: UUID, id: UUID) {
        jdbc.update(
            """
            UPDATE notification_recipient SET status='read', read_at=now()
            WHERE user_id = ?::uuid AND notification_id = ?::uuid
            """.trimIndent(),
            userId.toString(),
            id.toString(),
        )
    }

    @Transactional
    fun markAllRead(userId: UUID) {
        jdbc.update(
            "UPDATE notification_recipient SET status='read', read_at=now() WHERE user_id = ?::uuid AND status='unread'",
            userId.toString(),
        )
    }
}
