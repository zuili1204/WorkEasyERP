package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import java.util.UUID

/**
 * 商城请求身份（由 [MallAuthInterceptor] 写入）：
 * - realm=platform：平台会员，仅能访问平台层接口（选租户/加入）
 * - realm=tenant：租户域会员，`tid` 已由 JWT 决定，`cid` 为绑定的租户客户
 */
data class MallUser(
    val memberId: UUID,
    val customerId: UUID?,
    val tenantId: UUID?,
    val realm: String,
)

object MallContext {
    private val tl = ThreadLocal<MallUser>()

    fun set(u: MallUser) = tl.set(u)

    fun getOrNull(): MallUser? = tl.get()

    fun get(): MallUser = tl.get() ?: throw BizException(ErrorCode.AUTH_EXPIRED)

    /** 租户域接口必须持有绑定的客户身份；未绑定一律拒绝，禁止回退到「无客户」下单 */
    fun requireCustomer(): MallUser {
        val u = get()
        if (u.realm != "tenant" || u.customerId == null) {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "尚未绑定商户客户，请先加入商户")
        }
        return u
    }

    fun clear() = tl.remove()
}
