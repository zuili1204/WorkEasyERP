package com.workeasy.erp.system

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 演示账号初始化：users 表为空时创建 6 个账号（密码统一 123456）并绑定角色。
 * 对应数据库文档 §16.2 演示账号表。
 */
@Component
class DataInitializer(private val jdbc: JdbcTemplate) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        val count = jdbc.queryForObject("SELECT count(*) FROM users", Long::class.java) ?: 0
        if (count > 0) return

        val encoder = BCryptPasswordEncoder()
        val pwd = encoder.encode("123456")

        // 账号 / 员工工号 / 角色码
        val accounts = listOf(
            Triple("boss@we", "E0001", "boss"),
            Triple("mgr@we", "E0012", "manager"),
            Triple("fin@we", "E0002", "finance"),
            Triple("sal@we", "E0021", "sales"),
            Triple("hr@we", "E0003", "hr"),
            Triple("emp@we", "E0045", "employee"),
        )

        for ((username, empNo, roleCode) in accounts) {
            val empId = jdbc.queryForObject(
                "SELECT id FROM employee WHERE employee_no = ?", String::class.java, empNo
            )
            if (empId == null) {
                log.warn("演示账号 $username 跳过：未找到工号 $empNo 的员工")
                continue
            }
            val uid = UUID.randomUUID().toString()
            jdbc.update(
                """
                INSERT INTO users(id, username, password_hash, employee_id, status)
                VALUES (?::uuid, ?, ?, ?::uuid, 'active')
                ON CONFLICT (username) DO NOTHING
                """.trimIndent(),
                uid, username, pwd, empId,
            )
            jdbc.update(
                """
                INSERT INTO user_role(user_id, role_id)
                SELECT ?::uuid, r.id FROM role r WHERE r.code = ?
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                uid, roleCode,
            )
            jdbc.update("UPDATE employee SET user_id = ?::uuid WHERE id = ?::uuid", uid, empId)
        }
        log.info("演示账号初始化完成：{} 个（密码 123456）", accounts.size)
    }
}
