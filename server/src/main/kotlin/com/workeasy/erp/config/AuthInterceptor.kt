package com.workeasy.erp.config

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.tenant.TenantContext
import com.workeasy.erp.user.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

@Component
class AuthInterceptor(
    private val jwt: JwtProvider,
    private val authService: AuthService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ", ignoreCase = true)) {
            throw BizException(ErrorCode.AUTH_EXPIRED)
        }
        val token = header.substring(7).trim()
        val claims = runCatching { jwt.parse(token) }
            .getOrElse { throw BizException(ErrorCode.AUTH_EXPIRED) }
        if (claims.type != "staff") throw BizException(ErrorCode.AUTH_EXPIRED)

        // 租户归属：JWT 的 tid 为准，且客户端显式传的其他租户码一律拒绝
        val tenant = TenantContext.getOrNull() ?: throw BizException(ErrorCode.AUTH_EXPIRED)
        if (claims.tenantId == null) throw BizException(ErrorCode.AUTH_EXPIRED)
        if (claims.tenantId != tenant.id) {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该租户")
        }
        val requested = request.getAttribute(TenantResolverInterceptor.REQUESTED_ATTR) as? String
        if (requested != null && requested != tenant.code) {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该租户")
        }

        val uid = runCatching { UUID.fromString(claims.userId) }
            .getOrElse { throw BizException(ErrorCode.AUTH_EXPIRED) }
        UserContext.set(authService.loadLoginUser(uid))
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        UserContext.clear()
    }
}
