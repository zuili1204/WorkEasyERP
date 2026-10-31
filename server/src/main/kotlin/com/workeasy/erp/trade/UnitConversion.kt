package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 单位换算结果。
 * `unit` / `qty` / `price` 一律为**主单位**口径：库存、加权成本、报表都只认主单位；
 * `auxUnit` / `auxQty` 仅在按辅助单位录入时非空，用于单据回显与打印。
 */
data class UnitQty(
    val unit: String,
    val qty: BigDecimal,
    val price: BigDecimal,
    val auxUnit: String?,
    val auxQty: BigDecimal?,
    val convertRate: BigDecimal,
)

/**
 * 多单位换算：商品 `convert_rate` = 主单位数量 / 1 辅助单位。
 * - 录入单位留空或等于主单位 → 原样返回
 * - 录入单位等于辅助单位 → 数量 × 换算率、单价 ÷ 换算率
 */
object UnitConversion {

    fun resolve(
        jdbc: JdbcTemplate,
        productId: String,
        qty: BigDecimal,
        price: BigDecimal,
        entryUnit: String?,
    ): UnitQty {
        val p = jdbc.queryForList(
            "SELECT unit, aux_unit, convert_rate FROM product WHERE id = ?::uuid",
            productId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
        return resolve(
            qty, price, entryUnit,
            p["unit"]?.toString(), p["aux_unit"]?.toString(), bd(p["convert_rate"]),
        )
    }

    fun resolve(
        qty: BigDecimal,
        price: BigDecimal,
        entryUnit: String?,
        mainUnit: String?,
        auxUnit: String?,
        convertRate: BigDecimal?,
    ): UnitQty {
        val main = mainUnit?.trim()?.takeIf { it.isNotBlank() } ?: "件"
        val aux = auxUnit?.trim()?.takeIf { it.isNotBlank() }
        val entered = entryUnit?.trim().orEmpty()
        val rate = convertRate?.takeIf { it > BigDecimal.ZERO }

        if (entered.isBlank() || entered == main) {
            return UnitQty(
                unit = main,
                qty = qty.setScale(3, RoundingMode.HALF_UP),
                price = price.setScale(4, RoundingMode.HALF_UP),
                auxUnit = null,
                auxQty = null,
                convertRate = BigDecimal.ONE,
            )
        }
        if (aux == null || rate == null) {
            throw BizException(
                ErrorCode.BIZ_PARAM,
                "商品 $main 未配置辅助单位或换算率，不能按「$entered」录入",
            )
        }
        if (entered != aux) {
            throw BizException(
                ErrorCode.BIZ_PARAM,
                "商品单位不存在：$entered（主单位 $main，辅助单位 $aux）",
            )
        }
        return UnitQty(
            unit = main,
            qty = qty.multiply(rate).setScale(3, RoundingMode.HALF_UP),
            price = price.divide(rate, 4, RoundingMode.HALF_UP),
            auxUnit = aux,
            auxQty = qty.setScale(3, RoundingMode.HALF_UP),
            convertRate = rate,
        )
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
