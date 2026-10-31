package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.JwtProvider
import com.workeasy.erp.config.PlatformDb
import com.workeasy.erp.config.TenantResolverInterceptor
import com.workeasy.erp.tenant.TenantContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 商城双 realm 鉴权：
 * - `typ=member` 平台会员令牌 → 只设置为平台身份（选租户/加入/换 token）
 * - `typ=mall` 租户域令牌 → 校验 tid 与请求租户一致、绑定仍 active，再写入租户客户身份
 * 注意：商城员工后台走 mall-admin 路径，仍由员工 AuthInterceptor 处理。
 */
@Component
class MallAuthInterceptor(
    private val jwt: JwtProvider,
    private val platformDb: PlatformDb,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ", ignoreCase = true)) {
            throw BizException(ErrorCode.AUTH_EXPIRED)
        }
        val claims = runCatching { jwt.parse(header.substring(7).trim()) }
            .getOrElse { throw BizException(ErrorCode.AUTH_EXPIRED) }
        val memberId = claims.memberId ?: throw BizException(ErrorCode.AUTH_EXPIRED)

        when (claims.type) {
            "member" -> MallContext.set(MallUser(memberId, null, null, "platform"))
            "mall" -> {
                val tenant = TenantContext.getOrNull() ?: throw BizException(ErrorCode.AUTH_EXPIRED)
                if (claims.tenantId == null || claims.tenantId != tenant.id) {
                    throw BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该租户")
                }
                val requested = request.getAttribute(TenantResolverInterceptor.REQUESTED_ATTR) as? String
                if (requested != null && requested != tenant.code) {
                    throw BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该租户")
                }
                val binding = platformDb.jdbc.queryForList(
                    """
                    SELECT id, customer_id FROM member_tenant_binding
                    WHERE member_id = ?::uuid AND tenant_id = ?::uuid AND status = 'active'
                    """.trimIndent(),
                    memberId.toString(), tenant.id.toString(),
                ).firstOrNull() ?: throw BizException(ErrorCode.AUTH_FORBIDDEN, "尚未加入该商户或绑定未通过审核")

                val cid = binding["customer_id"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: throw BizException(ErrorCode.AUTH_FORBIDDEN, "绑定关系缺少客户档案")
                MallContext.set(MallUser(memberId, java.util.UUID.fromString(cid), tenant.id, "tenant"))
            }
            else -> throw BizException(ErrorCode.AUTH_EXPIRED)
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        MallContext.clear()
    }
}
