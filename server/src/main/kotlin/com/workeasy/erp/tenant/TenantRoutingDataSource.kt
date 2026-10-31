package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import javax.sql.DataSource

/**
 * 主数据源（@Primary）：按 TenantContext 决定落到哪个租户库。
 * 未显式设置租户时落 default 租户，保证单租户部署与既有代码无感兼容。
 */
class TenantRoutingDataSource(
    private val tenants: TenantRegistry,
    private val dataSources: TenantDataSourceRegistry,
    private val bootstrap: ObjectProvider<TenantBootstrap>,
) : AbstractRoutingDataSource() {

    /**
     * 目标数据源由 [determineTargetDataSource] 按租户动态解析，不使用静态 targetDataSources 映射，
     * 因此跳过父类的初始化校验（否则会要求必须配置 targetDataSources）。
     */
    override fun afterPropertiesSet() = Unit

    override fun determineCurrentLookupKey(): Any = TenantContext.codeOrDefault()

    override fun determineTargetDataSource(): DataSource {
        // 首次取连接前先完成平台迁移 + 默认租户登记，避免启动顺序依赖
        bootstrap.ifAvailable?.ensureInitialized()

        val code = TenantContext.codeOrDefault()
        val record = tenants.findByCode(code)
            ?: throw BizException(ErrorCode.BIZ_PARAM, "租户不存在：$code")
        if (!record.active) throw BizException(ErrorCode.AUTH_FORBIDDEN, "租户已停用：$code")
        return dataSources.resolve(record)
    }
}
