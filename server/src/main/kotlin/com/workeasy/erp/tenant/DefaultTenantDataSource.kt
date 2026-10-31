package com.workeasy.erp.tenant

import javax.sql.DataSource

/**
 * default 租户数据源的显式包装类型。
 * 用独立类型而不是裸 DataSource 注入，避免与 @Primary 的租户路由数据源混淆（也无需依赖 @Qualifier）。
 */
class DefaultTenantDataSource(val delegate: DataSource) : DataSource by delegate
