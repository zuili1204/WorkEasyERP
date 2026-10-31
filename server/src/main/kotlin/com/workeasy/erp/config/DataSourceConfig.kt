package com.workeasy.erp.config

import com.workeasy.erp.tenant.DefaultTenantDataSource
import com.workeasy.erp.tenant.TenantBootstrap
import com.workeasy.erp.tenant.TenantDataSourceRegistry
import com.workeasy.erp.tenant.TenantRegistry
import com.workeasy.erp.tenant.TenantRoutingDataSource
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * 双数据源：
 * - 平台库：租户注册 / 平台会员 / 渠道配置（独立 database，启动自动建库）
 * - 租户库：由 [TenantRoutingDataSource] 按 TenantContext 路由；default 租户复用 `spring.datasource`
 *
 * 注意：显式声明 @Primary DataSource 后，Spring Boot 的 DataSource 自动配置会让位，
 * 因此这里手动绑定 `spring.datasource.*`，保持既有配置项与连接数不变。
 */
@Configuration
class DataSourceConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConfigurationProperties("spring.datasource")
    fun defaultDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    @ConfigurationProperties("workeasy.platform.db")
    fun platformDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean(name = ["defaultTenantDataSource"])
    fun defaultTenantDataSource(
        defaultDataSourceProperties: DataSourceProperties,
        @Value("\${spring.datasource.hikari.maximum-pool-size:10}") poolSize: Int,
    ): DefaultTenantDataSource =
        DefaultTenantDataSource(buildHikari(defaultDataSourceProperties, "we-default", poolSize))

    @Bean(name = ["platformDataSource"])
    fun platformDataSource(
        platformDataSourceProperties: DataSourceProperties,
        @Value("\${workeasy.platform.db.pool-size:5}") poolSize: Int,
    ): HikariDataSource {
        ensureDatabase(platformDataSourceProperties)
        return buildHikari(platformDataSourceProperties, "we-platform", poolSize)
    }

    @Bean
    @Primary
    fun dataSource(
        tenantRegistry: TenantRegistry,
        tenantDataSourceRegistry: TenantDataSourceRegistry,
        tenantBootstrap: ObjectProvider<TenantBootstrap>,
    ): DataSource = TenantRoutingDataSource(tenantRegistry, tenantDataSourceRegistry, tenantBootstrap)

    private fun buildHikari(props: DataSourceProperties, poolName: String, poolSize: Int): HikariDataSource {
        val ds = props.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
        ds.poolName = poolName
        ds.maximumPoolSize = poolSize
        ds.minimumIdle = 1
        return ds
    }

    /**
     * 平台库首次启动自动创建（需要账号具备 CREATEDB 权限）。
     * 已有卷 / 手工建库时直接连通即返回，不改变任何既有数据。
     */
    private fun ensureDatabase(props: DataSourceProperties) {
        val url = props.url ?: return
        // 已存在则直接连通，避免依赖 maintenance 库（postgres）的 CONNECT 权限
        try {
            DriverManager.getConnection(url, props.username, props.password).close()
            return
        } catch (e: Exception) {
            if (e !is java.sql.SQLException || e.sqlState != "3D000") {
                log.error("平台库连接失败（{}），请确认库已创建且账号可用", url, e)
                throw e
            }
        }

        val m = Regex("jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+)").find(url) ?: return
        val host = m.groupValues[1]
        val port = m.groupValues[2].toIntOrNull() ?: 5432
        val db = m.groupValues[3]
        val maintenanceUrl = "jdbc:postgresql://$host:$port/postgres"
        try {
            DriverManager.getConnection(maintenanceUrl, props.username, props.password).use { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate("CREATE DATABASE \"${db.replace("\"", "\"\"")}\"")
                }
                log.info("已创建平台库：{}", db)
            }
        } catch (e: Exception) {
            log.error("平台库初始化失败（{}），请确认账号有 CREATEDB 权限或手工创建该库：{}", maintenanceUrl, e.message)
            throw e
        }
    }
}
