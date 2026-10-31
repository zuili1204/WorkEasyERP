package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

data class QuoteItemRequest(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
)

data class QuoteRequest(
    val customerId: String? = null,
    val items: List<QuoteItemRequest> = emptyList(),
)

data class PriceLevelRequest(
    val price: BigDecimal? = null,
)

data class CustomerPriceRequest(
    val productId: String = "",
    val price: BigDecimal? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val remark: String? = null,
)

/**
 * 价格体系：**协议价 > 等级价 > 标准售价**。
 * - 协议价：`customer_price`，按客户 + 商品，支持生效期（多条生效时取最近维护的一条）
 * - 等级价：`product_price`，按商品 + 客户等级（A/B/C）
 * - 标准售价：`product.sale_price`
 * 最低售价（`product.min_sale_price`）只做校验，不参与取价。
 */
@Service
class PriceService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {

    /** 批量取价（下单预览）：返回每行的取价结果与最低售价校验标记 */
    @Transactional(readOnly = true)
    fun quote(req: QuoteRequest): List<Map<String, Any?>> {
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "取价明细不能为空")
        val customerId = req.customerId?.takeIf { it.isNotBlank() }
        val level = if (customerId != null) {
            jdbc.queryForObject(
                "SELECT COALESCE(level,'') FROM customer WHERE id = ?::uuid",
                String::class.java,
                customerId,
            ) ?: ""
        } else {
            ""
        }
        return req.items.map { item -> quoteOne(item, customerId, level) }
    }

    /** 商品价格维护视图：标准售价 + 最低售价 + 各等级价 */
    @Transactional(readOnly = true)
    fun productPrices(productId: String): Map<String, Any?> {
        val p = productRow(productId)
        val levels = jdbc.queryForList(
            "SELECT price_level, price, status FROM product_price WHERE product_id = ?::uuid ORDER BY price_level",
            productId,
        )
        return mapOf(
            "productId" to productId,
            "sku" to p["sku"],
            "name" to p["name"],
            "salePrice" to bd(p["sale_price"]),
            "minSalePrice" to bd(p["min_sale_price"]),
            "levels" to levels.map {
                mapOf(
                    "level" to it["price_level"],
                    "price" to bd(it["price"]),
                    "status" to it["status"],
                )
            },
        )
    }

    /** 设置等级价（A/B/C），低于最低售价直接拒绝 */
    @Transactional
    fun setLevelPrice(productId: String, level: String, price: BigDecimal?) {
        if (level !in LEVELS) throw BizException(ErrorCode.BIZ_PARAM, "等级只能是 A/B/C")
        if (price == null || price <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "等级价必须大于 0")
        val floor = bd(productRow(productId)["min_sale_price"])
        if (floor > BigDecimal.ZERO && price < floor) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "等级价 $price 低于最低售价 $floor")
        }
        jdbc.update(
            """
            INSERT INTO product_price(product_id, price_level, price, status)
            VALUES (?::uuid, ?, ?, 'active')
            ON CONFLICT (product_id, price_level)
            DO UPDATE SET price = EXCLUDED.price, status = 'active', updated_at = now()
            """.trimIndent(),
            productId, level, price,
        )
        audit.log("price", "设置等级价 $level=$price", "product_price", productId)
    }

    @Transactional
    fun deleteLevelPrice(productId: String, level: String) {
        if (level !in LEVELS) throw BizException(ErrorCode.BIZ_PARAM, "等级只能是 A/B/C")
        jdbc.update("DELETE FROM product_price WHERE product_id = ?::uuid AND price_level = ?", productId, level)
        audit.log("price", "删除等级价 $level", "product_price", productId)
    }

    @Transactional(readOnly = true)
    fun customerPrices(customerId: String): List<Map<String, Any?>> =
        jdbc.queryForList(
            """
            SELECT cp.id, cp.product_id, p.sku, p.name AS product_name,
                   cp.price, cp.start_date, cp.end_date, cp.status, cp.remark
            FROM customer_price cp
            JOIN product p ON p.id = cp.product_id
            WHERE cp.customer_id = ?::uuid AND cp.status = 'active'
            ORDER BY cp.created_at DESC
            """.trimIndent(),
            customerId,
        )

    /** 新增协议价（同一商品可维护多档生效期，取价时按生效期与维护时间取一条） */
    @Transactional
    fun addCustomerPrice(customerId: String, req: CustomerPriceRequest) {
        if (req.productId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商品不能为空")
        val price = req.price ?: throw BizException(ErrorCode.BIZ_PARAM, "协议价不能为空")
        if (price <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "协议价必须大于 0")
        val exists = jdbc.queryForObject(
            "SELECT count(*) FROM customer WHERE id = ?::uuid", Long::class.java, customerId,
        ) ?: 0
        if (exists == 0L) throw BizException(ErrorCode.BIZ_NOT_FOUND, "客户不存在")
        val floor = bd(productRow(req.productId)["min_sale_price"])
        if (floor > BigDecimal.ZERO && price < floor) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "协议价 $price 低于最低售价 $floor")
        }
        val start = parseDate(req.startDate, "生效日期")
        val end = parseDate(req.endDate, "失效日期")
        if (start != null && end != null && end.isBefore(start)) {
            throw BizException(ErrorCode.BIZ_PARAM, "失效日期不能早于生效日期")
        }
        jdbc.update(
            """
            INSERT INTO customer_price(customer_id, product_id, price, start_date, end_date, remark)
            VALUES (?::uuid, ?::uuid, ?, ?::date, ?::date, ?)
            """.trimIndent(),
            customerId, req.productId, price, start?.toString(), end?.toString(), req.remark,
        )
        audit.log("price", "新增协议价 $price", "customer_price", req.productId)
    }

    @Transactional
    fun disableCustomerPrice(customerId: String, priceId: String) {
        val n = jdbc.update(
            "UPDATE customer_price SET status = 'inactive', updated_at = now() WHERE id = ?::uuid AND customer_id = ?::uuid",
            priceId, customerId,
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_NOT_FOUND, "协议价不存在")
        audit.log("price", "停用协议价", "customer_price", priceId)
    }

    // ---------- 内部实现 ----------

    private fun quoteOne(item: QuoteItemRequest, customerId: String?, level: String): Map<String, Any?> {
        if (item.productId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商品不能为空")
        val p = productRow(item.productId)
        val listPrice = bd(p["sale_price"])
        val minSalePrice = bd(p["min_sale_price"])

        val contract = if (customerId != null) {
            jdbc.queryForList(
                """
                SELECT price FROM customer_price
                WHERE customer_id = ?::uuid AND product_id = ?::uuid AND status = 'active'
                  AND (start_date IS NULL OR start_date <= CURRENT_DATE)
                  AND (end_date IS NULL OR end_date >= CURRENT_DATE)
                ORDER BY COALESCE(start_date, DATE '1900-01-01') DESC, created_at DESC
                LIMIT 1
                """.trimIndent(),
                customerId, item.productId,
            ).firstOrNull()?.get("price")
        } else {
            null
        }

        val levelPrice = if (level in LEVELS) {
            jdbc.queryForList(
                """
                SELECT price FROM product_price
                WHERE product_id = ?::uuid AND price_level = ? AND status = 'active'
                LIMIT 1
                """.trimIndent(),
                item.productId, level,
            ).firstOrNull()?.get("price")
        } else {
            null
        }

        val (price, source) = when {
            contract != null -> bd(contract) to "contract"
            levelPrice != null -> bd(levelPrice) to "level"
            else -> listPrice to "standard"
        }
        val belowMin = minSalePrice > BigDecimal.ZERO && price < minSalePrice

        return mapOf(
            "productId" to item.productId,
            "sku" to p["sku"],
            "name" to p["name"],
            "unit" to p["unit"],
            "qty" to item.qty,
            "onlineStatus" to p["online_status"],
            "listPrice" to listPrice,
            "price" to price,
            "source" to source,
            "customerLevel" to level.ifBlank { null },
            "minSalePrice" to minSalePrice,
            "belowMin" to belowMin,
            "amount" to (price * item.qty).setScale(2, java.math.RoundingMode.HALF_UP),
        )
    }

    private fun productRow(productId: String): Map<String, Any?> =
        jdbc.queryForList(
            "SELECT sku, name, unit, sale_price, min_sale_price, online_status FROM product WHERE id = ?::uuid",
            productId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")

    private fun parseDate(v: String?, label: String): LocalDate? {
        if (v.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(v) }
            .getOrElse { throw BizException(ErrorCode.BIZ_PARAM, "${label}格式应为 yyyy-MM-dd") }
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    private companion object {
        val LEVELS = setOf("A", "B", "C")
    }
}
