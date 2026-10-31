package com.workeasy.erp.tenant

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 平台库 tenant 表的一行（连接信息 + 开通状态） */
data class TenantRecord(
    val id: UUID,
    val code: String,
    val name: String,
    val status: String,
    val dbHost: String?,
    val dbPort: Int,
    val dbName: String?,
    val dbUsername: String?,
    val dbPasswordRef: String?,
    val dbPasswordEnc: String?,
) {
    val active: Boolean get() = status == "active"

    fun toInfo(): TenantInfo = TenantInfo(id, code, name, status)
}

/**
 * 租户注册表：只读平台库，带短 TTL 缓存（开通后主动 refresh）。
 * 平台池按类型注入（HikariDataSource 在容器内唯一），避免与主路由数据源 / 业务 JdbcTemplate 混淆。
 */
@Component
class TenantRegistry(platformDataSource: HikariDataSource) {

    private val platformJdbc = JdbcTemplate(platformDataSource)

    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, TenantRecord>()

    @Volatile
    private var loadedAt = 0L

    private val ttlMs = 60_000L

    fun findByCode(code: String): TenantRecord? {
        ensureFresh()
        return cache[code]
    }

    fun findById(id: UUID): TenantRecord? {
        ensureFresh()
        return cache.values.firstOrNull { it.id == id }
    }

    fun all(): List<TenantRecord> {
        ensureFresh()
        return cache.values.sortedBy { it.code }
    }

    fun refresh() {
        val rows = platformJdbc.query(
            """
            SELECT id, code, name, status, db_host, db_port, db_name,
                   db_username, db_password_ref, db_password_enc
            FROM tenant
            """.trimIndent()
        ) { rs, _ ->
            TenantRecord(
                id = rs.getObject("id", UUID::class.java),
                code = rs.getString("code"),
                name = rs.getString("name"),
                status = rs.getString("status"),
                dbHost = rs.getString("db_host"),
                dbPort = rs.getInt("db_port"),
                dbName = rs.getString("db_name"),
                dbUsername = rs.getString("db_username"),
                dbPasswordRef = rs.getString("db_password_ref"),
                dbPasswordEnc = rs.getString("db_password_enc"),
            )
        }
        cache.clear()
        rows.forEach { cache[it.code] = it }
        loadedAt = System.currentTimeMillis()
        log.debug("租户注册表已刷新：{} 个", rows.size)
    }

    private fun ensureFresh() {
        if (cache.isEmpty() || System.currentTimeMillis() - loadedAt > ttlMs) {
            synchronized(this) {
                if (cache.isEmpty() || System.currentTimeMillis() - loadedAt > ttlMs) refresh()
            }
        }
    }
}
