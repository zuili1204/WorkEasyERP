package com.workeasy.erp.system

import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/** 审计日志：写操作留痕（谁、何时、改了哪张表哪条记录） */
@Service
class AuditService(private val jdbc: JdbcTemplate) {

    fun log(module: String, action: String, targetTable: String? = null, targetId: String? = null) {
        val u = UserContext.getOrNull()
        jdbc.update(
            """
            INSERT INTO audit_log(user_id, user_name, module, action, target_table, target_id)
            VALUES (?::uuid, ?, ?, ?, ?, ?)
            """.trimIndent(),
            u?.userId?.toString(),
            u?.displayName,
            module,
            action,
            targetTable,
            targetId,
        )
    }
}
