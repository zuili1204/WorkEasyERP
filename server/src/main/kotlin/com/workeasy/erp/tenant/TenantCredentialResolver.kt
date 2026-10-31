package com.workeasy.erp.tenant

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * 解析租户库口令：优先环境变量/配置引用（`db_password_ref`），其次本地密文（`db_password_enc`）。
 * 两者都没有时视为配置缺失，直接抛错而不是静默用空口令连库。
 */
@Component
class TenantCredentialResolver(
    private val env: Environment,
    @Value("\${workeasy.tenant.db-secret:workeasy-dev-tenant-secret}") private val secret: String,
) {
    fun passwordOf(record: TenantRecord): String? {
        record.dbPasswordRef?.takeIf { it.isNotBlank() }?.let { ref ->
            val value = env.getProperty(ref) ?: System.getenv(ref)
                ?: throw BizException(ErrorCode.SYS_ERROR, "租户 ${record.code} 的口令引用 $ref 未配置")
            return value
        }
        record.dbPasswordEnc?.takeIf { it.isNotBlank() }?.let { enc ->
            return runCatching { SecretCodec.decrypt(enc, secret) }
                .getOrElse { throw BizException(ErrorCode.SYS_ERROR, "租户 ${record.code} 的口令密文无法解密") }
        }
        throw BizException(ErrorCode.SYS_ERROR, "租户 ${record.code} 未配置数据库口令")
    }
}
