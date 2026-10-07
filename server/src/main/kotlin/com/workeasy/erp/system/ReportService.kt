package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ReportCreate(
    val name: String = "",
    val module: String = "sales",
    val dimensions: List<String>? = null,
    val metrics: List<String>? = null,
    val frequency: String = "none",
)

data class ReportResult(
    val name: String,
    val module: String,
    val columns: List<String>,
    val rows: List<List<String?>>,
    val generatedAt: String,
)

/**
 * 自定义报表：定义（report_definition） + 运行（内置模板查询）。
 * 模板按 module 内置 SQL，dimensions/metrics 仅作配置留档，避免运行时拼接 SQL 带来的注入风险。
 */
@Service
class ReportService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {

    private val templates = mapOf(
        "sales" to (
            listOf("客户", "订单数", "销售额", "毛利") to
                """
                SELECT o.customer_name, COUNT(*) AS cnt,
                       COALESCE(SUM(o.total_amount), 0) AS amount,
                       COALESCE(SUM(o.total_profit), 0) AS profit
                FROM sales_order o
                WHERE o.deleted_at IS NULL
                GROUP BY o.customer_name ORDER BY amount DESC LIMIT 50
                """.trimIndent()
            ),
        "purchase" to (
            listOf("供应商", "订单数", "采购额") to
                """
                SELECT o.supplier_name, COUNT(*) AS cnt, COALESCE(SUM(o.total_amount), 0) AS amount
                FROM purchase_order o
                WHERE o.deleted_at IS NULL
                GROUP BY o.supplier_name ORDER BY amount DESC LIMIT 50
                """.trimIndent()
            ),
        "inventory" to (
            listOf("仓库", "商品数", "库存数量", "库存金额") to
                """
                SELECT w.name, COUNT(*) AS sku_count,
                       COALESCE(SUM(i.qty), 0) AS qty,
                       COALESCE(SUM(i.qty * i.avg_cost), 0) AS amount
                FROM inventory i JOIN warehouse w ON w.id = i.warehouse_id
                GROUP BY w.name ORDER BY amount DESC
                """.trimIndent()
            ),
        "finance" to (
            listOf("往来单位", "应收余额", "应付余额") to
                """
                SELECT COALESCE(c.name, '—') AS party,
                       COALESCE(SUM(a.remain_amount), 0) AS ar_remain,
                       0 AS ap_remain
                FROM ar_ledger a LEFT JOIN customer c ON c.id = a.customer_id
                WHERE a.status <> 'closed'
                GROUP BY c.name
                UNION ALL
                SELECT COALESCE(s.name, '—') AS party, 0 AS ar_remain,
                       COALESCE(SUM(p.remain_amount), 0) AS ap_remain
                FROM ap_ledger p LEFT JOIN supplier s ON s.id = p.supplier_id
                WHERE p.status <> 'closed'
                GROUP BY s.name
                ORDER BY 2 DESC LIMIT 50
                """.trimIndent()
            ),
        "hr" to (
            listOf("部门", "在职人数") to
                """
                SELECT d.name, COUNT(e.id) AS cnt
                FROM department d LEFT JOIN employee e
                  ON e.department_id = d.id AND e.deleted_at IS NULL
                GROUP BY d.name ORDER BY cnt DESC
                """.trimIndent()
            ),
    )

    @Transactional(readOnly = true)
    fun list(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val cond = StringBuilder("WHERE r.deleted_at IS NULL ")
        val args = mutableListOf<Any>()
        if (!q.isNullOrBlank()) {
            cond.append("AND (r.name ILIKE ? OR r.module ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = "FROM report_definition r $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id, r.name, r.module, r.frequency, r.last_generated_at, r.dimensions, r.metrics
            $from ORDER BY r.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(req: ReportCreate): Map<String, Any?> {
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "报表名称不能为空")
        if (req.module !in templates) {
            throw BizException(ErrorCode.BIZ_PARAM, "不支持的报表模块：${req.module}（可选 ${templates.keys.joinToString("/")}）")
        }
        val id = UUID.randomUUID()
        val dims = req.dimensions?.joinToString(",") ?: ""
        val mets = req.metrics?.joinToString(",") ?: ""
        jdbc.update(
            """
            INSERT INTO report_definition(id, name, module, dimensions, metrics, frequency, created_by)
            VALUES (?::uuid, ?, ?, ?::jsonb, ?::jsonb, ?, ?::uuid)
            """.trimIndent(),
            id.toString(), req.name, req.module,
            """{"dims":"$dims"}""", """{"metrics":"$mets"}""",
            req.frequency, UserContext.get().userId.toString(),
        )
        audit.log("report", "新建报表定义 ${req.name}", "report_definition", id.toString())
        return mapOf("id" to id.toString(), "status" to "active")
    }

    @Transactional
    fun remove(id: String) {
        jdbc.update("UPDATE report_definition SET deleted_at = now() WHERE id = ?::uuid", id)
        audit.log("report", "删除报表定义", "report_definition", id)
    }

    /** 运行：按模块模板查询并回写生成时间 */
    @Transactional
    fun run(id: String): ReportResult {
        val rows = jdbc.queryForList(
            "SELECT name, module FROM report_definition WHERE id = ?::uuid AND deleted_at IS NULL", id
        )
        if (rows.isEmpty()) throw BizException(ErrorCode.BIZ_NOT_FOUND, "报表不存在")
        val name = rows.first()["name"]?.toString() ?: ""
        val module = rows.first()["module"]?.toString() ?: "sales"
        val (columns, sql) = templates[module]
            ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的报表模块：$module")

        val data = jdbc.queryForList(sql)
        val out = data.map { row -> row.values.map { v -> v?.toString() } }
        jdbc.update("UPDATE report_definition SET last_generated_at = now() WHERE id = ?::uuid", id)
        audit.log("report", "运行报表 $name", "report_definition", id)
        return ReportResult(name, module, columns, out, Instant.now().toString())
    }

    /** 不落定义直接试跑某个模块模板 */
    @Transactional(readOnly = true)
    fun preview(module: String): ReportResult {
        val (columns, sql) = templates[module]
            ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的报表模块：$module")
        val data = jdbc.queryForList(sql)
        return ReportResult("$module 预览", module, columns, data.map { r -> r.values.map { v -> v?.toString() } }, Instant.now().toString())
    }

    fun modules(): List<String> = templates.keys.toList()
}
