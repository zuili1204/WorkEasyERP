package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class ProductOnlineRequest(
    val onlineStatus: String? = null,   // online/offline
    val onlineTitle: String? = null,
    val onlineDesc: String? = null,
    val minOrderQty: BigDecimal? = null,
    val stepQty: BigDecimal? = null,
    val minSalePrice: BigDecimal? = null,
)

data class ProductImageRequest(
    val url: String = "",
    val sortNo: Int = 0,
    val primary: Boolean = false,
)

/**
 * 商品扩展：线上属性（上下架/标题/描述/起订量/步长/最低售价）与图片。
 * 沿用「一行 = 一个 SKU」，不建 SPU。
 */
@Service
class ProductExtService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {

    /** 商品详情：商品字段 + 图片 + 等级价，供商品详情页与商城透出 */
    @Transactional(readOnly = true)
    fun detail(id: String): Map<String, Any?> {
        val p = jdbc.queryForList(
            "SELECT * FROM product WHERE id = ?::uuid AND deleted_at IS NULL",
            id,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
        val images = jdbc.queryForList(
            "SELECT id, url, sort_no, is_primary FROM product_image WHERE product_id = ?::uuid ORDER BY is_primary DESC, sort_no, created_at",
            id,
        )
        val levels = jdbc.queryForList(
            "SELECT price_level, price FROM product_price WHERE product_id = ?::uuid AND status = 'active' ORDER BY price_level",
            id,
        )
        return mapOf(
            "product" to p.mapValues { (_, v) -> v?.toString() },
            "images" to images.map { it.mapValues { (_, v) -> v?.toString() } },
            "levels" to levels.map { mapOf("level" to it["price_level"], "price" to it["price"]?.toString()) },
        )
    }

    /** 更新线上属性（只更新传入的字段） */
    @Transactional
    fun updateOnline(id: String, req: ProductOnlineRequest) {
        ensureProduct(id)
        val status = req.onlineStatus?.also {
            if (it !in setOf("online", "offline")) throw BizException(ErrorCode.BIZ_PARAM, "上下架状态只能是 online/offline")
        }
        req.minOrderQty?.let { if (it <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "起订量必须大于 0") }
        req.stepQty?.let { if (it <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "购买步长必须大于 0") }
        req.minSalePrice?.let { if (it < BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "最低售价不能为负") }

        jdbc.update(
            """
            UPDATE product SET
                online_status = COALESCE(?, online_status),
                online_title = COALESCE(?, online_title),
                online_desc = COALESCE(?, online_desc),
                min_order_qty = COALESCE(?::numeric, min_order_qty),
                step_qty = COALESCE(?::numeric, step_qty),
                min_sale_price = COALESCE(?::numeric, min_sale_price),
                updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            status, req.onlineTitle, req.onlineDesc,
            req.minOrderQty, req.stepQty, req.minSalePrice,
            id,
        )
        audit.log("product", "更新线上属性${status?.let { "，状态 $it" } ?: ""}", "product", id)
    }

    @Transactional
    fun addImage(id: String, req: ProductImageRequest): Map<String, Any?> {
        ensureProduct(id)
        if (req.url.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "图片地址不能为空")
        if (req.primary) {
            jdbc.update("UPDATE product_image SET is_primary = false WHERE product_id = ?::uuid", id)
        }
        val imageId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO product_image(id, product_id, url, sort_no, is_primary)
            VALUES (?::uuid, ?::uuid, ?, ?, ?)
            """.trimIndent(),
            imageId.toString(), id, req.url, req.sortNo, req.primary,
        )
        audit.log("product", "新增商品图片", "product_image", imageId.toString())
        return mapOf("id" to imageId.toString(), "url" to req.url)
    }

    @Transactional
    fun removeImage(id: String, imageId: String) {
        val n = jdbc.update(
            "DELETE FROM product_image WHERE id = ?::uuid AND product_id = ?::uuid",
            imageId, id,
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_NOT_FOUND, "图片不存在")
        audit.log("product", "删除商品图片", "product_image", imageId)
    }

    private fun ensureProduct(id: String) {
        val n = jdbc.queryForObject(
            "SELECT count(*) FROM product WHERE id = ?::uuid AND deleted_at IS NULL",
            Long::class.java,
            id,
        ) ?: 0
        if (n == 0L) throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
    }
}
