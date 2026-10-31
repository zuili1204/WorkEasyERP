package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class TenantStatusRequest(
    val status: String = "",
    /** 到期时间（可空；不传则保持不变） */
    val expireAt: String? = null,
)

/**
 * 平台租户运营：停用/启用、调整到期日，以及上线加固所需的运维指标。
 *
 * 指标口径：
 * - 租户池：只统计已创建连接池的租户，避免监控把未访问租户逐个拉起
 * - 库存负数 / 支付回调失败：只查当前在线池的租户，跨租户汇总不做 ETL
 * - Flyway / 开通失败：读平台库 `tenant_provision_log`，不依赖租户库
 */
@Service
class TenantOpsService(
    platformPool: HikariDataSource,
    private val tenants: TenantRegistry,
    private val dataSources: TenantDataSourceRegistry,
    private val jdbc: JdbcTemplate,
) {

    private val platformJdbc = JdbcTemplate(platformPool)

    /** 停用后立即关闭该租户连接池，下次访问会直接命中 suspended 拒绝逻辑 */
    fun updateStatus(code: String, req: TenantStatusRequest) {
        val target = code.trim().lowercase()
        val status = req.status.trim().lowercase()
        if (status !in ALLOWED_STATUS) throw BizException(ErrorCode.BIZ_PARAM, "状态只能是 active / suspended")
        val exists = platformJdbc.queryForList("SELECT id FROM tenant WHERE code = ?", target).firstOrNull()
            ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "租户不存在：$target")
        val expire = req.expireAt?.trim().orEmpty()
        platformJdbc.update(
            """
            UPDATE tenant SET status = ?,
                   expire_at = CASE WHEN ? = '' THEN expire_at ELSE ?::timestamptz END,
                   updated_at = now()
            WHERE code = ?
            """.trimIndent(),
            status, expire, expire.ifBlank { null }, target,
        )
        tenants.refresh()
        if (status == "suspended") dataSources.close(target)
    }

    fun metrics(): Map<String, Any?> {
        val statusCounts = platformJdbc.queryForList(
            "SELECT status, count(*) AS n FROM tenant GROUP BY status",
        ).associate { it["status"].toString() to (it["n"] as Number).toInt() }
        val total = statusCounts.values.sum()

        val expiring = platformJdbc.queryForList(
            """
            SELECT code, name, expire_at AS "expireAt",
                   GREATEST(0, (expire_at::date - CURRENT_DATE)) AS "daysLeft"
            FROM tenant
            WHERE status = 'active' AND expire_at IS NOT NULL
              AND expire_at <= now() + interval '30 days'
            ORDER BY expire_at
            """.trimIndent(),
        )

        val failed24h = platformJdbc.queryForObject(
            """
            SELECT count(*) FROM tenant_provision_log
            WHERE status = 'failed' AND created_at > now() - interval '24 hours'
            """.trimIndent(),
            Int::class.java,
        ) ?: 0
        val migrateFailed24h = platformJdbc.queryForObject(
            """
            SELECT count(*) FROM tenant_provision_log
            WHERE step = 'migrate' AND status = 'failed' AND created_at > now() - interval '24 hours'
            """.trimIndent(),
            Int::class.java,
        ) ?: 0

        val pool = dataSources.stats()
        val activePools = (pool["activePools"] as? Int) ?: 0
        val maxActive = (pool["maxActive"] as? Int) ?: 0

        val negative = negativeInventory()
        val alerts = buildList {
            if ((statusCounts["failed"] ?: 0) > 0) {
                add(alert("critical", "有租户开通失败，请在运营页重试或查看开通日志"))
            }
            if (failed24h > 0) add(alert("critical", "近 24 小时租户开通失败 $failed24h 次"))
            if (migrateFailed24h > 0) add(alert("critical", "近 24 小时租户库迁移失败 $migrateFailed24h 次（Flyway）"))
            if (maxActive > 0 && activePools >= maxActive) {
                add(alert("critical", "租户连接池已达上限 $activePools/$maxActive，需扩容或分片"))
            } else if (maxActive > 0 && activePools >= maxActive * 0.8) {
                add(alert("warn", "租户连接池接近上限 $activePools/$maxActive，请关注容量"))
            }
            expiring.forEach {
                val days = (it["daysLeft"] as? Number)?.toInt() ?: 0
                if (days <= 7) add(alert("warn", "租户 ${it["code"]} 将在 $days 天内到期"))
            }
            negative.forEach {
                add(alert("critical", "租户 ${it["tenantCode"]} 存在负库存 ${it["count"]} 条，请核查出入库"))
            }
        }

        return mapOf(
            "generatedAt" to java.time.OffsetDateTime.now().toString(),
            "tenants" to mapOf(
                "total" to total,
                "active" to (statusCounts["active"] ?: 0),
                "suspended" to (statusCounts["suspended"] ?: 0),
                "failed" to (statusCounts["failed"] ?: 0),
                "provisioning" to (statusCounts["provisioning"] ?: 0),
            ),
            "expiring" to expiring,
            "failures" to mapOf(
                "provisionFailed24h" to failed24h,
                "migrateFailed24h" to migrateFailed24h,
            ),
            "pool" to pool,
            "negativeInventory" to negative,
            "alerts" to alerts,
        )
    }

    /** 只巡检已建池的租户：既避免拉起全部租户库，也能覆盖真实在线业务 */
    private fun negativeInventory(): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        dataSources.pooledCodes().forEach { code ->
            val rec = tenants.findByCode(code) ?: return@forEach
            if (!rec.active) return@forEach
            runCatching {
                TenantContext.withTenant(rec.toInfo()) {
                    val n = jdbc.queryForObject("SELECT count(*) FROM inventory WHERE qty < 0", Int::class.java) ?: 0
                    if (n > 0) rows += mapOf("tenantCode" to code, "count" to n)
                }
            }
        }
        return rows
    }

    private fun alert(level: String, message: String): Map<String, Any?> =
        mapOf("level" to level, "message" to message)

    private companion object {
        val ALLOWED_STATUS = setOf("active", "suspended")
    }
}
