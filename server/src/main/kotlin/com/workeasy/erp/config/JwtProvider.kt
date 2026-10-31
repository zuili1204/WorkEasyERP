package com.workeasy.erp.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * JWT 载荷：
 * - `typ=staff` 员工端；`typ=member` 平台会员（无 tid）；`typ=mall` 租户域会员（带 tid/mid/cid）
 * - `tid` 决定数据源路由，客户端不可越过；`mid`/`cid` 为平台会员与租户客户绑定
 */
data class JwtClaims(
    val userId: String,
    val username: String,
    val tenantId: UUID?,
    val type: String,
    val memberId: UUID? = null,
    val customerId: UUID? = null,
)

@Component
class JwtProvider(
    @Value("\${workeasy.jwt.secret}") private val secret: String,
    @Value("\${workeasy.jwt.expire-hours}") private val expireHours: Long,
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generate(
        userId: String,
        username: String,
        tenantId: UUID?,
        type: String = "staff",
        memberId: UUID? = null,
        customerId: UUID? = null,
    ): String {
        val now = Date()
        val exp = Date(now.time + expireHours * 3_600_000)
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("tid", tenantId?.toString().orEmpty())
            .claim("typ", type)
            .claim("mid", memberId?.toString().orEmpty())
            .claim("cid", customerId?.toString().orEmpty())
            .issuedAt(now)
            .expiration(exp)
            .signWith(key)
            .compact()
    }

    fun parse(token: String): JwtClaims {
        val payload = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        val tid = payload["tid"]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        fun uuidClaim(name: String): UUID? = payload[name]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return JwtClaims(
            userId = payload.subject,
            username = payload["username"]?.toString().orEmpty(),
            tenantId = tid,
            type = payload["typ"]?.toString() ?: "staff",
            memberId = uuidClaim("mid"),
            customerId = uuidClaim("cid"),
        )
    }
}
