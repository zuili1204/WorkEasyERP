package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class MallAddressRequest(
    val receiver: String = "",
    val phone: String = "",
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val detail: String = "",
    val isDefault: Boolean = false,
)

/** 收货地址：按租户 + 会员隔离 */
@Service
class MallAddressService(private val jdbc: JdbcTemplate) {

    @Transactional(readOnly = true)
    fun list(): List<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        return jdbc.queryForList(
            """
            SELECT id::text AS "id", receiver, phone, province, city, district, detail,
                   is_default AS "isDefault", created_at AS "createdAt"
            FROM mall_address WHERE member_id = ?::uuid AND deleted_at IS NULL
            ORDER BY is_default DESC, updated_at DESC
            """.trimIndent(),
            user.memberId.toString(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
    }

    @Transactional
    fun create(req: MallAddressRequest): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        validate(req)
        val id = UUID.randomUUID()
        if (req.isDefault) clearDefault(user.memberId)
        jdbc.update(
            """
            INSERT INTO mall_address(id, member_id, receiver, phone, province, city, district, detail, is_default)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id.toString(), user.memberId.toString(), req.receiver.trim(), req.phone.trim(),
            req.province, req.city, req.district, req.detail.trim(), req.isDefault,
        )
        return mapOf("id" to id.toString())
    }

    @Transactional
    fun update(id: String, req: MallAddressRequest): Boolean {
        val user = MallContext.requireCustomer()
        validate(req)
        if (req.isDefault) clearDefault(user.memberId)
        val n = jdbc.update(
            """
            UPDATE mall_address SET receiver = ?, phone = ?, province = ?, city = ?, district = ?,
                   detail = ?, is_default = ?, updated_at = now()
            WHERE id = ?::uuid AND member_id = ?::uuid AND deleted_at IS NULL
            """.trimIndent(),
            req.receiver.trim(), req.phone.trim(), req.province, req.city, req.district,
            req.detail.trim(), req.isDefault, id, user.memberId.toString(),
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_NOT_FOUND, "地址不存在")
        return true
    }

    @Transactional
    fun remove(id: String): Boolean {
        val user = MallContext.requireCustomer()
        jdbc.update(
            "UPDATE mall_address SET deleted_at = now() WHERE id = ?::uuid AND member_id = ?::uuid",
            id, user.memberId.toString(),
        )
        return true
    }

    private fun clearDefault(memberId: UUID) {
        jdbc.update("UPDATE mall_address SET is_default = false WHERE member_id = ?::uuid", memberId.toString())
    }

    private fun validate(req: MallAddressRequest) {
        if (req.receiver.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "收货人不能为空")
        if (!req.phone.trim().matches(Regex("1[3-9]\\d{9}"))) throw BizException(ErrorCode.BIZ_PARAM, "手机号格式不正确")
        if (req.detail.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "详细地址不能为空")
    }
}

@RestController
@RequestMapping("/mall/addresses")
class MallAddressController(private val service: MallAddressService) {

    @GetMapping
    fun list(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.list())

    @PostMapping
    fun create(@RequestBody req: MallAddressRequest): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.create(req))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody req: MallAddressRequest): ApiResponse<Boolean> =
        ApiResponse.ok(service.update(id, req))

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): ApiResponse<Boolean> = ApiResponse.ok(service.remove(id))
}
