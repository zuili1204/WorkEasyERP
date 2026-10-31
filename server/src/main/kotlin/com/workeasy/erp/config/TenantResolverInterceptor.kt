package com.workeasy.erp.config

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.tenant.TenantContext
import com.workeasy.erp.tenant.TenantRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 租户解析（先于鉴权执行）：
 * 1) 已登录请求以 JWT 的 `tid` 为准（客户端传的 tenantCode 不能改写身份）
 * 2) 未登录请求走 `X-Tenant-Code`（登录、支付回调等）
 * 3) 都没有时落 default 租户，保持单租户部署兼容
 * `requestedTenantCode` 留给 AuthInterceptor 做跨租户校验。
 */
@Component
class TenantResolverInterceptor(
    private val jwt: JwtProvider,
    private val tenants: TenantRegistry,
) : HandlerInterceptor {

    companion object {
        const val HEADER = "X-Tenant-Code"
        const val REQUESTED_ATTR = "workeasy.requestedTenantCode"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val requested = request.getHeader(HEADER)?.trim()?.takeIf { it.isNotBlank() }
        request.setAttribute(REQUESTED_ATTR, requested)

        val claims = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.let { runCatching { jwt.parse(it.substring(7).trim()) }.getOrNull() }

        val code = claims?.tenantId?.let { id -> tenants.findById(id)?.code }
            ?: requested
            ?: TenantContext.DEFAULT_CODE

        val record = tenants.findByCode(code)
            ?: throw BizException(ErrorCode.BIZ_PARAM, "租户不存在：$code")
        if (!record.active) throw BizException(ErrorCode.AUTH_FORBIDDEN, "租户已停用：$code")

        TenantContext.set(record.toInfo())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        TenantContext.clear()
    }
}
