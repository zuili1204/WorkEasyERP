package com.workeasy.erp.tenant

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * 两套迁移入口：
 * - 平台库：`classpath:db/migration/platform`，只建租户注册/会员/渠道表
 * - 租户库：`classpath:db/migration/tenant`，V1–V23 原样搬入，每个租户库各跑一次
 */
@Component
class TenantMigrator {

    private val log = LoggerFactory.getLogger(javaClass)

    fun migratePlatform(dataSource: DataSource) {
        log.info("执行平台库迁移")
        Flyway.configure()
            .dataSource(dataSource)
            .locations(PLATFORM_LOCATION)
            .baselineOnMigrate(true)
            .validateOnMigrate(false)
            .load()
            .migrate()
    }

    fun migrateTenant(dataSource: DataSource, label: String) {
        log.info("执行租户库迁移：{}", label)
        Flyway.configure()
            .dataSource(dataSource)
            .locations(TENANT_LOCATION)
            .baselineOnMigrate(true)
            .validateOnMigrate(false)
            .load()
            .migrate()
    }

    fun migrateTenant(url: String, username: String, password: String, label: String) {
        log.info("执行租户库迁移：{}@{}", label, url)
        Flyway.configure()
            .dataSource(url, username, password)
            .locations(TENANT_LOCATION)
            .baselineOnMigrate(true)
            .validateOnMigrate(false)
            .load()
            .migrate()
    }

    private companion object {
        const val PLATFORM_LOCATION = "classpath:db/migration/platform"
        const val TENANT_LOCATION = "classpath:db/migration/tenant"
    }
}
