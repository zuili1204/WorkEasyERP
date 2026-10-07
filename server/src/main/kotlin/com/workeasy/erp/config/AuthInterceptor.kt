package com.workeasy.erp.config

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
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
        val userId = runCatching { jwt.parseSubject(token) }
            .getOrElse { throw BizException(ErrorCode.AUTH_EXPIRED) }
        val uid = runCatching { UUID.fromString(userId) }
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
