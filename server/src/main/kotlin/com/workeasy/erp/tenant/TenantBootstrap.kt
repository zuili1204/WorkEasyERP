package com.workeasy.erp.tenant

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 启动引导：平台库迁移 → 登记 default 租户 → 默认租户库迁移。
 * 幂等：重复启动只会跳过已存在的库表与租户记录。
 */
@Component
@Order(1)
class TenantBootstrap(
    private val platformDataSource: HikariDataSource,
    private val defaultTenantDataSource: DefaultTenantDataSource,
    private val tenants: TenantRegistry,
    private val migrator: TenantMigrator,
    @Value("\${spring.datasource.url}") private val defaultUrl: String,
    @Value("\${spring.datasource.username:}") private val defaultUsername: String,
) : ApplicationRunner {

    private val platformJdbc = JdbcTemplate(platformDataSource)

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var initialized = false

    override fun run(args: ApplicationArguments?) = ensureInitialized()

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            migrator.migratePlatform(platformDataSource)
            registerDefaultTenant()
            migrator.migrateTenant(defaultTenantDataSource, TenantContext.DEFAULT_CODE)
            tenants.refresh()
            initialized = true
            log.info("租户引导完成，已登记租户：{}", tenants.all().joinToString { it.code })
        }
    }

    /** 把现有 `spring.datasource` 指向的库登记为 default 租户；已存在则不动（保留人工改过的状态） */
    private fun registerDefaultTenant() {
        val target = parsePgUrl(defaultUrl)
        if (target == null) {
            log.warn("无法解析 spring.datasource.url，跳过 default 租户登记：{}", defaultUrl)
            return
        }
        platformJdbc.update(
            """
            INSERT INTO tenant(code, name, status, db_host, db_port, db_name, db_username)
            VALUES (?, ?, 'active', ?, ?, ?, ?)
            ON CONFLICT (code) DO NOTHING
            """.trimIndent(),
            TenantContext.DEFAULT_CODE, "默认租户", target.host, target.port, target.database,
            defaultUsername.takeIf { it.isNotBlank() },
        )
    }

    private data class PgTarget(val host: String, val port: Int, val database: String)

    private fun parsePgUrl(url: String): PgTarget? {
        val m = Regex("jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+)").find(url) ?: return null
        val host = m.groupValues[1]
        val port = m.groupValues[2].toIntOrNull() ?: 5432
        val database = m.groupValues[3]
        return PgTarget(host, port, database)
    }
}
