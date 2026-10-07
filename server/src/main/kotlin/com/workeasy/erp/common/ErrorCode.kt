package com.workeasy.erp.common

/** 错误码分层：AUTH_* 鉴权 / BIZ_* 业务 / SYS_* 系统（对应规划文档 §4.1） */
enum class ErrorCode(val code: String, val msg: String) {
    AUTH_LOGIN_FAIL("AUTH_401", "账号或密码错误"),
    AUTH_EXPIRED("AUTH_401_1", "登录已过期，请重新登录"),
    AUTH_DISABLED("AUTH_401_2", "账号已停用"),
    AUTH_FORBIDDEN("AUTH_403", "无权限访问该数据"),
    BIZ_NOT_FOUND("BIZ_404", "记录不存在"),
    BIZ_PARAM("BIZ_400", "参数错误"),
    BIZ_CONFLICT("BIZ_409", "数据冲突"),
    SYS_ERROR("SYS_500", "系统异常，请稍后重试"),
}

class BizException(val errCode: String, message: String) : RuntimeException(message) {
    constructor(ec: ErrorCode, detail: String? = null) :
        this(ec.code, if (detail.isNullOrBlank()) ec.msg else "${ec.msg}（$detail）")
}
