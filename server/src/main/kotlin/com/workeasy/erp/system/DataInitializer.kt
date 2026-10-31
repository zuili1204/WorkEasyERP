package com.workeasy.erp.system

import com.workeasy.erp.tenant.TenantContext
import com.workeasy.erp.tenant.TenantRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 演示账号初始化：仅 dev 环境、首次开通时执行（users 表为空时创建 6 个账号，密码 123456）。
 * 生产环境通过租户开通接口创建管理员，不再自动灌演示数据。
 */
@Component
@Profile("dev")
@Order(2)
class DataInitializer(
    private val jdbc: JdbcTemplate,
    private val tenants: TenantRegistry,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        val defaultTenant = tenants.findByCode(TenantContext.DEFAULT_CODE) ?: run {
            log.warn("未找到 default 租户，跳过演示账号初始化")
            return
        }
        TenantContext.withTenant(defaultTenant.toInfo()) { initDemoAccounts() }
    }

    private fun initDemoAccounts() {
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
