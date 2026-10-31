package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * 租户数据源注册表：每个租户一个独立 Hikari 池，懒创建 + LRU 上限。
 * default 租户直接复用 `spring.datasource` 配置的池，保持单租户部署的既有行为与连接数。
 */
@Component
class TenantDataSourceRegistry(
    private val defaultDataSource: DefaultTenantDataSource,
    private val credentials: TenantCredentialResolver,
    @Value("\${workeasy.tenant.max-active:50}") private val maxActive: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = Any()

    // accessOrder=true：最近使用的排到队尾，淘汰从队首开始
    private val pools = LinkedHashMap<String, HikariDataSource>(16, 0.75f, true)

    fun resolve(record: TenantRecord): DataSource {
        if (record.code == TenantContext.DEFAULT_CODE) return defaultDataSource.delegate
        synchronized(lock) {
            pools[record.code]?.let { if (it.isRunning) return it else pools.remove(record.code) }
            val ds = create(record)
            pools[record.code] = ds
            evictIfNeeded()
            return ds
        }
    }

    fun close(code: String) {
        synchronized(lock) {
            pools.remove(code)?.close()
        }
    }

    /** 当前已创建连接池的租户编码（用于运维监控只查在线租户，避免逐个拉起新池） */
    fun pooledCodes(): List<String> = synchronized(lock) { pools.keys.toList() }

    /** 连接池运行态：池数量、上限与各池连接数，供 /platform/metrics 展示与告警判断 */
    fun stats(): Map<String, Any?> = synchronized(lock) {
        val detail = pools.map { (code, ds) ->
            val bean = ds.hikariPoolMXBean
            mapOf(
                "tenantCode" to code,
                "running" to ds.isRunning,
                "active" to (bean?.activeConnections ?: 0),
                "idle" to (bean?.idleConnections ?: 0),
                "total" to (bean?.totalConnections ?: 0),
            )
        }
        mapOf(
            "activePools" to pools.size,
            "maxActive" to maxActive,
            "pools" to detail,
        )
    }

    @PreDestroy
    fun closeAll() {
        synchronized(lock) {
            pools.values.forEach { runCatching { it.close() } }
            pools.clear()
        }
    }

    private fun create(record: TenantRecord): HikariDataSource {
        val dbName = record.dbName?.takeIf { it.isNotBlank() }
            ?: throw BizException(ErrorCode.SYS_ERROR, "租户 ${record.code} 未配置库名")
        val host = record.dbHost?.takeIf { it.isNotBlank() } ?: "localhost"
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://$host:${record.dbPort}/$dbName"
            username = record.dbUsername
            password = credentials.passwordOf(record)
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 5_000
            // 租户库暂时不可达时不要拖垮整个进程，等真正取连接再报错
            initializationFailTimeout = -1
            poolName = "we-tenant-${record.code}"
        }
        log.info("创建租户数据源：{} -> {}", record.code, config.jdbcUrl)
        return HikariDataSource(config)
    }

    /** 超出上限时优先回收空闲池；仍超限则保留（活跃租户优先），并记录告警供容量规划 */
    private fun evictIfNeeded() {
        if (pools.size <= maxActive) return
        val iterator = pools.entries.iterator()
        while (pools.size > maxActive && iterator.hasNext()) {
            val entry = iterator.next()
            val active = runCatching { entry.value.hikariPoolMXBean?.activeConnections ?: 0 }.getOrDefault(0)
            if (active == 0) {
                iterator.remove()
                runCatching { entry.value.close() }
                log.info("回收空闲租户数据源：{}", entry.key)
            }
        }
        if (pools.size > maxActive) {
            log.warn("活跃租户数据源已达 {}，超过上限 {}，需评估分片或扩容", pools.size, maxActive)
        }
    }
}
