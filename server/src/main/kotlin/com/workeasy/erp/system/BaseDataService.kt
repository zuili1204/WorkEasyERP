package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 基础数据：商品分类 / 商品 SKU / 供应商 / 仓库 / 汇率
 * 配置驱动一套代码维护五类字典数据（M2 进销存依赖）。
 */
private data class BaseConfig(
    val table: String,
    val prefix: String?,
    val title: String,
    val fields: List<String>,
    val nameField: String,
)

private val BASE_CONFIG: Map<String, BaseConfig> = mapOf(
    "category" to BaseConfig("product_category", null, "商品分类", listOf("code", "name", "sortNo"), "name"),
    "product" to BaseConfig("product", null, "商品 / SKU",
        listOf("sku", "name", "spec", "unit", "salePrice", "taxRate"), "name"),
    "supplier" to BaseConfig("supplier", "SUP", "供应商",
        listOf("code", "name", "contactName", "contactPhone", "paymentTerms"), "name"),
    "warehouse" to BaseConfig("warehouse", null, "仓库", listOf("code", "name", "location", "type"), "name"),
    "currency" to BaseConfig("currency_rate", null, "汇率",
        listOf("currencyFrom", "currencyTo", "rate", "rateDate"), "currency_from"),
)

private val BASE_FIELD_SQL: Map<String, Pair<String, String>> = mapOf(
    "code" to ("code" to "?"),
    "name" to ("name" to "?"),
    "sortNo" to ("sort_no" to "?::int"),
    "sku" to ("sku" to "?"),
    "spec" to ("spec" to "?"),
    "unit" to ("unit" to "?"),
    "salePrice" to ("sale_price" to "?::numeric"),
    "taxRate" to ("tax_rate" to "?::numeric"),
    "contactName" to ("contact_name" to "?"),
    "contactPhone" to ("contact_phone" to "?"),
    "paymentTerms" to ("payment_terms" to "?::int"),
    "location" to ("location" to "?"),
    "type" to ("type" to "?"),
    "currencyFrom" to ("currency_from" to "?"),
    "currencyTo" to ("currency_to" to "?"),
    "rate" to ("rate" to "?::numeric"),
    "rateDate" to ("rate_date" to "?::date"),
)

@Service
class BaseDataService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    val types: List<String> get() = BASE_CONFIG.keys.toList()

    /** 概览：各类基础数据条数 */
    @Transactional(readOnly = true)
    fun overview(): List<Map<String, Any?>> = BASE_CONFIG.map { (kind, cfg) ->
        val count = jdbc.queryForObject("SELECT count(*) FROM ${cfg.table}", Long::class.java) ?: 0
        mapOf("kind" to kind, "title" to cfg.title, "count" to count)
    }

    @Transactional(readOnly = true)
    fun list(kind: String, q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val cfg = BASE_CONFIG[kind] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的基础数据：$kind")
        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        if (!q.isNullOrBlank()) {
            cond.append("AND t.${cfg.nameField} ILIKE ? ")
            args.add("%$q%")
        }
        val from = "FROM ${cfg.table} t $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT t.* $from ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(kind: String, payload: Map<String, Any?>): Map<String, Any?> {
        val cfg = BASE_CONFIG[kind] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的基础数据：$kind")
        val me = UserContext.get()
        val id = UUID.randomUUID()

        val name = payload["name"]?.toString()
        if (name.isNullOrBlank() && kind != "currency") {
            throw BizException(ErrorCode.BIZ_PARAM, "${cfg.title}名称不能为空")
        }

        val cols = mutableListOf("id")
        val phs = mutableListOf("?::uuid")
        val args = mutableListOf<Any?>(id.toString())

        // 编号：供应商自动生成 SUP…，其余用传入值
        if (cfg.prefix != null) {
            val code = payload["code"]?.toString()?.takeIf { it.isNotBlank() } ?: noGenerator.next(cfg.prefix)
            cols += "code"
            phs += "?"
            args += code
        }
        cfg.fields.forEach { f ->
            if (f == "code") return@forEach
            val (col, ph) = BASE_FIELD_SQL[f] ?: return@forEach
            val v = payload[f]?.toString()
            if (!v.isNullOrBlank()) {
                cols += col
                phs += ph
                args += v
            }
        }

        jdbc.update(
            "INSERT INTO ${cfg.table} (${cols.joinToString(",")}) VALUES (${phs.joinToString(",")})",
            *args.toTypedArray(),
        )
        audit.log("basedata", "新增${cfg.title}：${name ?: payload["currencyFrom"] ?: id}", cfg.table, id.toString())
        return mapOf("id" to id.toString(), "status" to "active")
    }
}
