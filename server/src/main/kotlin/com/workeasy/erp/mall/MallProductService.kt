package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.common.Paging
import com.workeasy.erp.mall.mapper.MallQueryMapper
import com.workeasy.erp.trade.PriceService
import com.workeasy.erp.trade.QuoteItemRequest
import com.workeasy.erp.trade.QuoteRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MallCartRequest(
    val productId: String = "",
    val qty: java.math.BigDecimal = java.math.BigDecimal.ZERO,
)

data class MallCartUpdateRequest(val qty: java.math.BigDecimal = java.math.BigDecimal.ZERO)

/**
 * 商城商品与购物车：
 * - 只展示 `online` 商品；价格一律走 [PriceService]（协议价 > 等级价 > 标准售价），客户端价格忽略
 * - 可用量按商城默认发货仓口径，只做展示与下单校验，真正的扣减仍在 ERP
 */
@Service
class MallProductService(
    private val jdbc: JdbcTemplate,
    private val mapper: MallQueryMapper,
    private val priceService: PriceService,
) {

    @Transactional(readOnly = true)
    fun products(q: String?, categoryId: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        val (pg, pgSize) = Paging.clamp(page, size)
        val kw = Paging.keyword(q)
        val cat = categoryId?.trim()?.takeIf { it.isNotBlank() }
        val rows = mapper.onlineProducts(kw, cat, (pg - 1) * pgSize, pgSize)
        val total = mapper.countOnlineProducts(kw, cat)
        val warehouse = MallSupport.defaultWarehouse(jdbc)
        val quoted = quote(user.customerId.toString(), rows)
        val list = rows.map { it + quoted[it["productId"]?.toString()].orEmpty() + mapOf(
            "availableQty" to MallSupport.available(jdbc, warehouse, it["productId"].toString()),
        ) }
        return PageResult.of(list, total, pg, pgSize)
    }

    @Transactional(readOnly = true)
    fun detail(id: String): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val p = jdbc.queryForList(
            """
            SELECT p.id::text AS "productId", p.sku, p.name,
                   COALESCE(p.online_title, p.name) AS "onlineTitle", p.online_desc AS "onlineDesc",
                   p.spec, p.brand, p.origin, p.unit, p.aux_unit AS "auxUnit", p.convert_rate AS "convertRate",
                   p.sale_price AS "salePrice", p.min_order_qty AS "minOrderQty", p.step_qty AS "stepQty",
                   p.tax_rate AS "taxRate", c.name AS "categoryName"
            FROM product p LEFT JOIN product_category c ON c.id = p.category_id
            WHERE p.id = ?::uuid AND p.deleted_at IS NULL AND p.status = 'active' AND p.online_status = 'online'
            """.trimIndent(),
            id,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在或已下架")
        val images = jdbc.queryForList(
            "SELECT id, url, is_primary AS \"isPrimary\" FROM product_image WHERE product_id = ?::uuid ORDER BY is_primary DESC, sort_no",
            id,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val quoted = quote(user.customerId.toString(), listOf(mapOf("productId" to id)))
        val warehouse = MallSupport.defaultWarehouse(jdbc)
        return p + quoted[id].orEmpty() + mapOf(
            "images" to images,
            "availableQty" to MallSupport.available(jdbc, warehouse, id),
        )
    }

    @Transactional(readOnly = true)
    fun categories(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT c.id::text AS "id", c.name,
               (SELECT count(*) FROM product p
                WHERE p.category_id = c.id AND p.deleted_at IS NULL
                  AND p.status = 'active' AND p.online_status = 'online') AS "productCount"
        FROM product_category c
        ORDER BY c.sort_no, c.name
        """.trimIndent(),
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    // ---------- 购物车 ----------

    @Transactional(readOnly = true)
    fun cart(): List<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        val rows = mapper.cartItems(user.memberId.toString())
        val quoted = quote(user.customerId.toString(), rows)
        val warehouse = MallSupport.defaultWarehouse(jdbc)
        return rows.map { row ->
            val q = quoted[row["productId"]?.toString()].orEmpty()
            val price = MallSupport.bd(q["price"])
            val qty = MallSupport.bd(row["qty"])
            val available = MallSupport.available(jdbc, warehouse, row["productId"].toString())
            row + q + mapOf(
                "availableQty" to available,
                "amount" to (price * qty).setScale(2, java.math.RoundingMode.HALF_UP),
                "stockEnough" to (available >= qty),
                "online" to (row["onlineStatus"]?.toString() == "online"),
            )
        }
    }

    @Transactional
    fun addToCart(req: MallCartRequest) {
        val user = MallContext.requireCustomer()
        if (req.productId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商品不能为空")
        MallSupport.validatePurchaseQty(jdbc, req.productId, req.qty)
        jdbc.update(
            """
            INSERT INTO mall_cart_item(member_id, product_id, qty)
            VALUES (?::uuid, ?::uuid, ?)
            ON CONFLICT (member_id, product_id)
            DO UPDATE SET qty = mall_cart_item.qty + EXCLUDED.qty, updated_at = now()
            """.trimIndent(),
            user.memberId.toString(), req.productId, req.qty,
        )
    }

    /** 购物车数量改为绝对值（0 表示删除） */
    @Transactional
    fun updateCart(id: String, req: MallCartUpdateRequest) {
        val user = MallContext.requireCustomer()
        val row = jdbc.queryForList(
            "SELECT product_id FROM mall_cart_item WHERE id = ?::uuid AND member_id = ?::uuid",
            id, user.memberId.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "购物车记录不存在")
        if (req.qty <= java.math.BigDecimal.ZERO) {
            jdbc.update("DELETE FROM mall_cart_item WHERE id = ?::uuid", id)
            return
        }
        MallSupport.validatePurchaseQty(jdbc, row["product_id"].toString(), req.qty)
        jdbc.update(
            "UPDATE mall_cart_item SET qty = ?, updated_at = now() WHERE id = ?::uuid AND member_id = ?::uuid",
            req.qty, id, user.memberId.toString(),
        )
    }

    @Transactional
    fun removeCart(id: String) {
        val user = MallContext.requireCustomer()
        jdbc.update(
            "DELETE FROM mall_cart_item WHERE id = ?::uuid AND member_id = ?::uuid",
            id, user.memberId.toString(),
        )
    }

    @Transactional
    fun clearCart() {
        val user = MallContext.requireCustomer()
        jdbc.update("DELETE FROM mall_cart_item WHERE member_id = ?::uuid", user.memberId.toString())
    }

    // ---------- 内部 ----------

    private fun quote(customerId: String, rows: List<Map<String, Any?>>): Map<String?, Map<String, Any?>> {
        if (rows.isEmpty()) return emptyMap()
        val items = rows.map {
            QuoteItemRequest(it["productId"]?.toString().orEmpty(), java.math.BigDecimal.ONE)
        }
        return priceService.quote(QuoteRequest(customerId = customerId, items = items))
            .associateBy { it["productId"]?.toString() }
    }
}
