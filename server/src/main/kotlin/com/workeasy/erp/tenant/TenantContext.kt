package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import java.util.UUID

/** 当前请求所属租户（只暴露运行期必要字段） */
data class TenantInfo(
    val id: UUID,
    val code: String,
    val name: String,
    val status: String,
)

/**
 * 租户上下文：由 TenantResolverInterceptor 写入、请求结束清理；
 * 数据源路由与附件目录均以此为准，禁止把 tenantCode 直接当查询条件绕过路由。
 */
object TenantContext {
    const val DEFAULT_CODE = "default"

    private val tl = ThreadLocal<TenantInfo>()

    fun set(t: TenantInfo) = tl.set(t)

    fun getOrNull(): TenantInfo? = tl.get()

    fun require(): TenantInfo = tl.get() ?: throw BizException(ErrorCode.SYS_ERROR, "租户上下文缺失")

    fun codeOrDefault(): String = tl.get()?.code ?: DEFAULT_CODE

    fun clear() = tl.remove()

    /** 临时切换租户（开通、定时任务、初始化等非请求线程场景），执行完恢复原上下文 */
    fun <T> withTenant(t: TenantInfo, block: () -> T): T {
        val prev = tl.get()
        tl.set(t)
        try {
            return block()
        } finally {
            if (prev != null) tl.set(prev) else tl.remove()
        }
    }
}
