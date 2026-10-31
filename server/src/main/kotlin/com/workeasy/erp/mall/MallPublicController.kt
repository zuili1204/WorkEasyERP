package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.tenant.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 商城公开信息：公告、轮播、客服电话（按 X-Tenant-Code 定位租户库） */
@RestController
@RequestMapping("/mall/public")
class MallPublicController(private val jdbc: JdbcTemplate) {

    @GetMapping("/config")
    fun config(): ApiResponse<Map<String, Any?>> {
        val tenant = TenantContext.getOrNull()
        val rows = jdbc.queryForList("SELECT key, value FROM mall_config ORDER BY key")
            .associate { it["key"].toString() to it["value"]?.toString() }
        return ApiResponse.ok(
            mapOf(
                "tenantCode" to tenant?.code,
                "tenantName" to tenant?.name,
                "notice" to rows["notice"],
                "servicePhone" to rows["service_phone"],
                "creditEnabled" to (rows["credit_enabled"] == "true"),
                "wechatEnabled" to (rows["wechat_enabled"] == "true"),
                "autoConfirmDays" to (rows["auto_confirm_days"] ?: "7"),
                "minOrderAmount" to (rows["min_order_amount"] ?: "0"),
            )
        )
    }

    @GetMapping("/banners")
    fun banners(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(
        jdbc.queryForList(
            """
            SELECT id::text AS "id", title, image_url AS "imageUrl", link_url AS "linkUrl", sort_no AS "sortNo"
            FROM mall_banner WHERE status = 'active' ORDER BY sort_no, created_at
            """.trimIndent(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
    )
}
