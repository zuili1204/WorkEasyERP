package com.workeasy.erp.config

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import java.util.UUID

/** 当前登录用户（请求线程内共享），由 AuthInterceptor 写入 */
data class LoginUser(
    val userId: UUID,
    val username: String,
    val displayName: String,
    val employeeId: UUID? = null,
    val deptId: UUID? = null,
    val deptName: String? = null,
    val roles: List<String> = emptyList(),
    val dataScope: String = "self",
)

object UserContext {
    private val tl = ThreadLocal<LoginUser>()

    fun set(u: LoginUser) = tl.set(u)

    fun get(): LoginUser = tl.get() ?: throw BizException(ErrorCode.AUTH_EXPIRED)

    fun getOrNull(): LoginUser? = tl.get()

    fun clear() = tl.remove()
}
