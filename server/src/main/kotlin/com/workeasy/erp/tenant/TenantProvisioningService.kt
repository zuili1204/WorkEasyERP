package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

data class TenantCreateRequest(
    val code: String = "",
    val name: String = "",
    val dbName: String = "",
    val adminUsername: String = "admin",
    val adminPassword: String = "123456",
    val adminRole: String = "boss",
    val plan: String = "",
    val expireAt: String = "",
)

data class TenantCreateResult(
    val id: String,
    val code: String,
    val name: String,
    val status: String,
    val message: String,
)

/**
 * 租户开通：同实例新建 database → 跑租户库迁移 → 建管理员 → 置 active。
 * 幂等：已 active 的租户直接返回；失败可重复调用，从失败步骤续跑（迁移与种子本身幂等）。
 */
@Service
class TenantProvisioningService(
    platformPool: HikariDataSource,
    private val migrator: TenantMigrator,
    private val tenants: TenantRegistry,
    private val dataSources: TenantDataSourceRegistry,
    @Value("\${spring.datasource.username:}") private val tenantDbUser: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val encoder = BCryptPasswordEncoder()
    private val platformJdbc = JdbcTemplate(platformPool)
    private val platformDataSource = platformPool

    fun provision(req: TenantCreateRequest): TenantCreateResult {
        val code = req.code.trim().lowercase()
        if (!code.matches(Regex("[a-z][a-z0-9_]{1,31}"))) {
            throw BizException(ErrorCode.BIZ_PARAM, "租户编码只能是小写字母/数字/下划线，且以字母开头（2-32 位）")
        }
        val name = req.name.trim().ifBlank { throw BizException(ErrorCode.BIZ_PARAM, "租户名称不能为空") }
        if (req.adminPassword.length < 6) throw BizException(ErrorCode.BIZ_PARAM, "管理员密码至少 6 位")

        val existing = findRow(code)
        if (existing != null && existing["status"] == "active") {
            return TenantCreateResult(
                existing["id"].toString(), code, existing["name"].toString(), "active", "租户已开通，未重复执行",
            )
        }

        val tenantId = existing?.get("id")?.let { UUID.fromString(it.toString()) } ?: UUID.randomUUID()
        val dbName = req.dbName.trim().ifBlank { "we_tenant_$code" }
        validateDbName(dbName)

        upsertTenant(tenantId, code, name, dbName, "provisioning", req)

        var step = "create_db"
        try {
            writeLog(tenantId, step, "started")
            createDatabase(dbName)
            writeLog(tenantId, step, "success")

            step = "migrate"
            writeLog(tenantId, step, "started")
            migrator.migrateTenant(tenantUrl(dbName), tenantDbUser, tenantDbPassword(), code)
            writeLog(tenantId, step, "success")

            step = "seed"
            writeLog(tenantId, step, "started")
            seedAdmin(dbName, req)
            writeLog(tenantId, step, "success")

            step = "activate"
            writeLog(tenantId, step, "started")
            platformJdbc.update(
                "UPDATE tenant SET status = 'active', updated_at = now() WHERE id = ?::uuid", tenantId.toString(),
            )
            writeLog(tenantId, step, "success")

            tenants.refresh()
            dataSources.close(code)
            log.info("租户开通完成：{}（{}）", code, dbName)
            return TenantCreateResult(tenantId.toString(), code, name, "active", "开通成功")
        } catch (e: Exception) {
            platformJdbc.update(
                "UPDATE tenant SET status = 'failed', updated_at = now() WHERE id = ?::uuid", tenantId.toString(),
            )
            writeLog(tenantId, step, "failed", e.message)
            tenants.refresh()
            log.error("租户开通失败：{}（步骤 {}）", code, step, e)
            throw BizException(ErrorCode.SYS_ERROR, "租户开通失败（$step）：${e.message}")
        }
    }

    fun list(): List<Map<String, Any?>> = platformJdbc.queryForList(
        """
        SELECT code, name, status, db_name, plan, expire_at, created_at, updated_at
        FROM tenant ORDER BY created_at
        """.trimIndent()
    )

    private fun findRow(code: String): Map<String, Any?>? =
        platformJdbc.queryForList("SELECT id, code, name, status FROM tenant WHERE code = ?", code).firstOrNull()

    private fun upsertTenant(
        tenantId: UUID,
        code: String,
        name: String,
        dbName: String,
        status: String,
        req: TenantCreateRequest,
    ) {
        val (host, port) = platformHostPort()
        platformJdbc.update(
            """
            INSERT INTO tenant(id, code, name, status, db_host, db_port, db_name, db_username,
                               db_password_ref, plan, expire_at)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, 'spring.datasource.password', ?,
                    CASE WHEN ? = '' THEN NULL ELSE ?::timestamptz END)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                status = EXCLUDED.status,
                db_host = EXCLUDED.db_host,
                db_port = EXCLUDED.db_port,
                db_name = EXCLUDED.db_name,
                db_username = EXCLUDED.db_username,
                db_password_ref = EXCLUDED.db_password_ref,
                plan = EXCLUDED.plan,
                expire_at = EXCLUDED.expire_at,
                updated_at = now()
            """.trimIndent(),
            tenantId.toString(), code, name, status, host, port, dbName,
            tenantDbUser.takeIf { it.isNotBlank() }, req.plan.takeIf { it.isNotBlank() },
            req.expireAt.trim(), req.expireAt.trim(),
        )
    }

    private fun createDatabase(dbName: String) {
        platformDataSource.connection.use { conn ->
            if (databaseExists(conn, dbName)) return
            conn.createStatement().use { st -> st.executeUpdate("CREATE DATABASE \"${dbName.replace("\"", "\"\"")}\"") }
            log.info("已创建租户库：{}", dbName)
        }
    }

    private fun databaseExists(conn: Connection, dbName: String): Boolean =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '${dbName.replace("'", "''")}'")
                .use { rs -> rs.next() }
        }

    private fun seedAdmin(dbName: String, req: TenantCreateRequest) {
        val pwd = encoder.encode(req.adminPassword)
        DriverManager.getConnection(tenantUrl(dbName), tenantDbUser, tenantDbPassword()).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO users(id, username, password_hash, employee_id, status)
                VALUES (gen_random_uuid(), ?, ?, (SELECT id FROM employee WHERE employee_no = 'E0001'), 'active')
                ON CONFLICT (username) DO NOTHING
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, req.adminUsername.trim())
                ps.setString(2, pwd)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO user_role(user_id, role_id)
                SELECT u.id, r.id FROM users u JOIN role r ON r.code = ? WHERE u.username = ?
                ON CONFLICT DO NOTHING
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, req.adminRole.trim())
                ps.setString(2, req.adminUsername.trim())
                ps.executeUpdate()
            }
        }
    }

    private fun writeLog(tenantId: UUID, step: String, status: String, message: String? = null) {
        platformJdbc.update(
            "INSERT INTO tenant_provision_log(tenant_id, step, status, message) VALUES (?::uuid, ?, ?, ?)",
            tenantId.toString(), step, status, message,
        )
    }

    private fun validateDbName(dbName: String) {
        if (!dbName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]{1,62}"))) {
            throw BizException(ErrorCode.BIZ_PARAM, "租户库名只能包含字母/数字/下划线（2-63 位）")
        }
    }

    private fun platformHostPort(): Pair<String, Int> {
        val jdbcUrl = platformUrl.takeIf { it.isNotBlank() } ?: return "localhost" to 5432
        val m = Regex("jdbc:postgresql://([^/:?]+)(?::(\\d+))?").find(jdbcUrl)
            ?: return "localhost" to 5432
        return m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 5432)
    }

    private fun tenantUrl(dbName: String): String {
        val (host, port) = platformHostPort()
        return "jdbc:postgresql://$host:$port/$dbName"
    }

    private fun tenantDbPassword(): String = tenantDbPasswordValue

    @Value("\${workeasy.platform.db.url:}")
    private lateinit var platformUrl: String

    @Value("\${spring.datasource.password:}")
    private lateinit var tenantDbPasswordValue: String
}
