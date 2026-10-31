package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.JwtProvider
import com.workeasy.erp.config.PlatformDb
import com.workeasy.erp.tenant.TenantContext
import com.workeasy.erp.tenant.TenantRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.UUID

data class WxLoginRequest(
    val code: String = "",
    val nickname: String? = null,
    val phone: String? = null,
)

data class PhoneBindRequest(val phone: String = "")

data class JoinTenantRequest(
    val tenantCode: String = "",
    val inviteCode: String? = null,
)

data class TenantTokenRequest(val tenantCode: String = "")

/**
 * 平台会员身份：
 * 1. 小程序 `uni.login` 拿 code → 平台 AppID 换 openid（当前外部前置未就绪，走 mock）
 * 2. 会员加入租户后签发**租户域** JWT（tid/mid/cid），切换租户必须重签
 * 3. 绑定关系存平台库，业务数据仍落租户库
 */
@Service
class MallAuthService(
    private val platformDb: PlatformDb,
    private val jdbc: JdbcTemplate,
    private val tenants: TenantRegistry,
    private val jwt: JwtProvider,
    @Value("\${workeasy.mall.wx.mock:true}") private val wxMock: Boolean,
) {

    fun wxLogin(req: WxLoginRequest): Map<String, Any?> {
        val code = req.code.trim()
        if (code.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "微信登录 code 不能为空")
        // 真实接入点：wxMock=false 时用平台 AppID/Secret 调 jscode2session 换取 openid
        val openid = "mock_" + sha256(code).take(32)
        val existing = platformDb.jdbc.queryForList(
            "SELECT id, nickname, phone, status FROM platform_member WHERE wx_openid = ?",
            openid,
        ).firstOrNull()

        val memberId = existing?.get("id")?.toString() ?: UUID.randomUUID().toString()
        if (existing == null) {
            platformDb.jdbc.update(
                """
                INSERT INTO platform_member(id, wx_openid, nickname, phone, status)
                VALUES (?::uuid, ?, ?, ?, 'active')
                """.trimIndent(),
                memberId, openid, req.nickname?.take(100), validPhoneOrNull(req.phone),
            )
        } else {
            if (existing["status"]?.toString() != "active") {
                throw BizException(ErrorCode.AUTH_DISABLED, "会员已停用")
            }
            platformDb.jdbc.update(
                """
                UPDATE platform_member SET last_login = now(), updated_at = now(),
                    nickname = COALESCE(?, nickname), phone = COALESCE(?, phone)
                WHERE id = ?::uuid
                """.trimIndent(),
                req.nickname?.take(100), validPhoneOrNull(req.phone), memberId,
            )
        }

        val member = memberRow(UUID.fromString(memberId))!!
        return mapOf(
            "token" to platformToken(memberId, member["nickname"]?.toString()),
            "member" to member,
            "mock" to wxMock,
        )
    }

    fun bindPhone(memberId: UUID, req: PhoneBindRequest): Map<String, Any?> {
        val phone = validPhoneOrNull(req.phone) ?: throw BizException(ErrorCode.BIZ_PARAM, "手机号格式不正确")
        platformDb.jdbc.update(
            "UPDATE platform_member SET phone = ?, updated_at = now() WHERE id = ?::uuid",
            phone, memberId.toString(),
        )
        return memberRow(memberId)!!
    }

    /** 可进入的租户：已加入 + 可凭邀请码加入（只列 active 租户） */
    fun tenants(memberId: UUID): List<Map<String, Any?>> {
        val bindings = platformDb.jdbc.queryForList(
            "SELECT tenant_id, tenant_code, customer_id, status FROM member_tenant_binding WHERE member_id = ?::uuid",
            memberId.toString(),
        ).associateBy { it["tenant_id"]?.toString() }

        return tenants.all().filter { it.active }.map { t ->
            val b = bindings[t.id.toString()]
            mapOf(
                "tenantCode" to t.code,
                "name" to t.name,
                "joined" to (b?.get("status")?.toString() == "active"),
                "bindStatus" to b?.get("status")?.toString(),
                "customerId" to b?.get("customer_id")?.toString(),
            )
        }
    }

    fun join(memberId: UUID, req: JoinTenantRequest): Map<String, Any?> {
        val code = req.tenantCode.trim()
        if (code.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商户编码不能为空")
        val tenant = tenants.findByCode(code)
            ?: throw BizException(ErrorCode.BIZ_PARAM, "商户不存在：$code")
        if (!tenant.active) throw BizException(ErrorCode.AUTH_FORBIDDEN, "商户已停用")

        val existing = bindingRow(memberId, tenant.id)
        if (existing?.get("status")?.toString() == "active") {
            return mapOf("tenantCode" to tenant.code, "status" to "active", "customerId" to existing["customer_id"]?.toString())
        }

        var customerId: UUID? = null
        var autoApprove = false
        val invite = req.inviteCode?.trim()?.takeIf { it.isNotBlank() }
        if (invite != null) {
            TenantContext.withTenant(tenant.toInfo()) {
                val row = jdbc.queryForList(
                    """
                    SELECT i.customer_id, c.name AS customer_name
                    FROM mall_invite i JOIN customer c ON c.id = i.customer_id AND c.deleted_at IS NULL
                    WHERE i.code = ? AND i.status = 'active'
                    """.trimIndent(),
                    invite,
                ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_PARAM, "邀请码无效或已停用")
                customerId = UUID.fromString(row["customer_id"].toString())
                autoApprove = configValue("bind_auto_approve", "false") == "true"
            }
        }

        val status = if (customerId != null && autoApprove) "active" else "pending"
        if (existing == null) {
            platformDb.jdbc.update(
                """
                INSERT INTO member_tenant_binding(member_id, tenant_id, tenant_code, customer_id, status, invite_code)
                VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?)
                """.trimIndent(),
                memberId.toString(), tenant.id.toString(), tenant.code,
                customerId?.toString(), status, invite,
            )
        } else {
            platformDb.jdbc.update(
                """
                UPDATE member_tenant_binding
                SET status = ?, customer_id = COALESCE(?::uuid, customer_id), invite_code = COALESCE(?, invite_code),
                    updated_at = now()
                WHERE member_id = ?::uuid AND tenant_id = ?::uuid
                """.trimIndent(),
                status, customerId?.toString(), invite, memberId.toString(), tenant.id.toString(),
            )
        }
        return mapOf("tenantCode" to tenant.code, "status" to status, "customerId" to customerId?.toString())
    }

    /** 签发租户域 JWT：切换租户必须重新签发，不复用平台令牌 */
    fun tenantToken(memberId: UUID, req: TenantTokenRequest): Map<String, Any?> {
        val code = req.tenantCode.trim()
        if (code.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商户编码不能为空")
        val tenant = tenants.findByCode(code)
            ?: throw BizException(ErrorCode.BIZ_PARAM, "商户不存在：$code")
        if (!tenant.active) throw BizException(ErrorCode.AUTH_FORBIDDEN, "商户已停用")

        val binding = bindingRow(memberId, tenant.id)
            ?: throw BizException(ErrorCode.AUTH_FORBIDDEN, "尚未加入该商户")
        if (binding["status"]?.toString() != "active") {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "绑定待商户审核")
        }
        val cid = binding["customer_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw BizException(ErrorCode.AUTH_FORBIDDEN, "绑定关系缺少客户档案")

        val customer = TenantContext.withTenant(tenant.toInfo()) {
            val c = jdbc.queryForList(
                "SELECT id, code, name, level FROM customer WHERE id = ?::uuid AND deleted_at IS NULL",
                cid,
            ).firstOrNull() ?: throw BizException(ErrorCode.AUTH_FORBIDDEN, "客户档案不存在")
            // 首次进入租户时建立展示资料（幂等）
            jdbc.update(
                """
                INSERT INTO mall_member_profile(member_id, customer_id, company_name)
                VALUES (?::uuid, ?::uuid, ?)
                ON CONFLICT (member_id) DO UPDATE SET customer_id = EXCLUDED.customer_id, updated_at = now()
                """.trimIndent(),
                memberId.toString(), cid, c["name"]?.toString(),
            )
            c
        }

        val token = jwt.generate(
            memberId.toString(), customer["name"]?.toString().orEmpty(), tenant.id, "mall",
            memberId = memberId, customerId = UUID.fromString(cid),
        )
        return mapOf(
            "token" to token,
            "tenantCode" to tenant.code,
            "tenantName" to tenant.name,
            "customer" to mapOf(
                "id" to cid,
                "code" to customer["code"]?.toString(),
                "name" to customer["name"]?.toString(),
                "level" to customer["level"]?.toString(),
            ),
        )
    }

    fun me(memberId: UUID): Map<String, Any?> = memberRow(memberId) ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "会员不存在")

    /** 平台侧取绑定（管理审核用）；不存在返回 null */
    fun bindingRow(memberId: UUID, tenantId: UUID): Map<String, Any?>? = platformDb.jdbc.queryForList(
        """
        SELECT id, member_id, tenant_id, tenant_code, customer_id, status, invite_code, created_at, updated_at
        FROM member_tenant_binding WHERE member_id = ?::uuid AND tenant_id = ?::uuid
        """.trimIndent(),
        memberId.toString(), tenantId.toString(),
    ).firstOrNull()

    private fun memberRow(memberId: UUID): Map<String, Any?>? = platformDb.jdbc.queryForList(
        "SELECT id, nickname, phone, status, created_at FROM platform_member WHERE id = ?::uuid",
        memberId.toString(),
    ).firstOrNull()?.mapValues { (_, v) -> v?.toString() }

    private fun platformToken(memberId: String, nickname: String?): String =
        jwt.generate(memberId, nickname ?: "会员", null, "member", memberId = UUID.fromString(memberId))

    private fun configValue(key: String, default: String): String =
        jdbc.queryForList("SELECT value FROM mall_config WHERE key = ?", String::class.java, key)
            .firstOrNull() ?: default

    private fun validPhoneOrNull(v: String?): String? {
        val s = v?.trim().orEmpty()
        if (s.isBlank()) return null
        if (!s.matches(Regex("1[3-9]\\d{9}"))) throw BizException(ErrorCode.BIZ_PARAM, "手机号格式不正确")
        return s
    }

    private fun sha256(v: String): String =
        MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
}
