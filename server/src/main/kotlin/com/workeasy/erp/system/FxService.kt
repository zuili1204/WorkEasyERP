package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class CurrencyUpsert(
    val code: String = "",
    val name: String = "",
    val symbol: String? = null,
    val decimalPlaces: Int = 2,
)

data class RateUpsert(
    val currencyFrom: String = "",
    val currencyTo: String = "CNY",
    val rate: BigDecimal = BigDecimal.ZERO,
    val rateDate: String? = null,
    val source: String? = "manual",
)

/**
 * 多币种与汇率：币种字典（含本位币）+ 汇率维护 + 换算。
 * 汇率按「日期生效」语义：换算时取 <= 指定日期的最新一条。
 */
@Service
class FxService(private val jdbc: JdbcTemplate) {

    @Transactional(readOnly = true)
    fun currencies(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT c.code, c.name, c.symbol, c.is_base, c.enabled, c.decimal_places,
               (SELECT count(*) FROM currency_rate r WHERE r.currency_from = c.code) AS rate_count,
               (SELECT max(r.rate) FROM currency_rate r
                 WHERE r.currency_from = c.code AND r.currency_to = 'CNY') AS latest_rate
        FROM currency c ORDER BY c.is_base DESC, c.code
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional
    fun saveCurrency(req: CurrencyUpsert): Map<String, Any?> {
        val code = req.code.trim().uppercase()
        if (code.isBlank() || code.length > 8) throw BizException(ErrorCode.BIZ_PARAM, "币种代码无效")
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "币种名称不能为空")
        jdbc.update(
            """
            INSERT INTO currency(code, name, symbol, decimal_places)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, symbol = EXCLUDED.symbol,
                                             decimal_places = EXCLUDED.decimal_places
            """.trimIndent(),
            code, req.name, req.symbol, req.decimalPlaces,
        )
        return mapOf("code" to code, "status" to "active")
    }

    /** 设置本位币：全局唯一 */
    @Transactional
    fun setBase(code: String) {
        val n = jdbc.queryForObject("SELECT count(*) FROM currency WHERE code = ?", Long::class.java, code) ?: 0
        if (n == 0L) throw BizException(ErrorCode.BIZ_PARAM, "币种 $code 不存在")
        jdbc.update("UPDATE currency SET is_base = false WHERE is_base = true")
        jdbc.update("UPDATE currency SET is_base = true WHERE code = ?", code)
    }

    @Transactional
    fun toggle(code: String, enabled: Boolean) {
        jdbc.update("UPDATE currency SET enabled = ? WHERE code = ?", enabled, code)
    }

    @Transactional(readOnly = true)
    fun rates(currency: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        if (!currency.isNullOrBlank()) {
            cond.append("AND r.currency_from = ? ")
            args.add(currency.uppercase())
        }
        val from = """
            FROM currency_rate r
            JOIN currency c ON c.code = r.currency_from
            $cond
        """.trimIndent()
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id, r.currency_from, c.name AS currency_name, c.symbol,
                   r.currency_to, r.rate, r.rate_date, r.source
            $from ORDER BY r.rate_date DESC, r.currency_from LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    /** 汇率录入：同一（币种对 + 日期）覆盖更新 */
    @Transactional
    fun upsertRate(req: RateUpsert) {
        val from = req.currencyFrom.trim().uppercase()
        val to = req.currencyTo.trim().uppercase()
        if (from.isBlank() || to.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "币种不能为空")
        if (from == to) throw BizException(ErrorCode.BIZ_PARAM, "源币种与目标币种不能相同")
        if (req.rate <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "汇率必须大于 0")

        val date = req.rateDate?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
        jdbc.update(
            """
            INSERT INTO currency_rate(currency_from, currency_to, rate, rate_date, source)
            VALUES (?, ?, ?, ?::date, ?)
            ON CONFLICT (currency_from, currency_to, rate_date)
            DO UPDATE SET rate = EXCLUDED.rate, source = EXCLUDED.source
            """.trimIndent(),
            from, to, req.rate, date, req.source ?: "manual",
        )
    }

    @Transactional
    fun deleteRate(id: String) {
        jdbc.update("DELETE FROM currency_rate WHERE id = ?::uuid", id)
    }

    /** 取 <= date 的最新汇率；同币种返回 1 */
    @Transactional(readOnly = true)
    fun rateOf(from: String, to: String, date: LocalDate): BigDecimal {
        val f = from.uppercase()
        val t = to.uppercase()
        if (f == t) return BigDecimal.ONE
        val direct = jdbc.queryForList(
            """
            SELECT rate FROM currency_rate
            WHERE currency_from = ? AND currency_to = ? AND rate_date <= ?::date
            ORDER BY rate_date DESC LIMIT 1
            """.trimIndent(),
            f, t, date.toString(),
        ).firstOrNull()
        if (direct != null) return bd(direct["rate"])

        // 反向汇率取倒数
        val reverse = jdbc.queryForList(
            """
            SELECT rate FROM currency_rate
            WHERE currency_from = ? AND currency_to = ? AND rate_date <= ?::date
            ORDER BY rate_date DESC LIMIT 1
            """.trimIndent(),
            t, f, date.toString(),
        ).firstOrNull()
        if (reverse != null) {
            val r = bd(reverse["rate"])
            if (r > BigDecimal.ZERO) return BigDecimal.ONE.divide(r, 6, RoundingMode.HALF_UP)
        }
        throw BizException(ErrorCode.BIZ_PARAM, "未维护 $f → $t 在 $date 及之前的汇率")
    }

    /** 换算结果 */
    @Transactional(readOnly = true)
    fun convert(amount: BigDecimal, from: String, to: String, date: LocalDate): Map<String, Any?> {
        val rate = rateOf(from, to, date)
        val result = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        return mapOf(
            "amount" to amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            "from" to from.uppercase(),
            "to" to to.uppercase(),
            "rate" to rate.toPlainString(),
            "result" to result.toPlainString(),
            "date" to date.toString(),
        )
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
