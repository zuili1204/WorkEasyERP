package com.workeasy.erp.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 平台库专用入口：只读写 `tenant` / `platform_member` / `member_tenant_binding` / `tenant_channel`。
 * 不参与租户路由，避免与业务 JdbcTemplate（走 TenantContext）混用。
 */
@Component
class PlatformDb(platformDataSource: HikariDataSource) {
    val jdbc: JdbcTemplate = JdbcTemplate(platformDataSource)
}
