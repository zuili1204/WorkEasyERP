package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class StocktakeLine(
    val productId: String = "",
    val actualQty: BigDecimal = BigDecimal.ZERO,
    val batchNo: String? = null,
    val locationCode: String? = null,
)

data class StocktakeCreateRequest(
    val warehouseId: String = "",
    val remark: String? = null,
)

/**
 * 盘点：按仓库生成账面明细 → 录入实盘 → 审核后生成 `inventory_txn(type='adjust')`
 * 并调整 `inventory.qty`（差异可正可负：正=盘盈、负=盘亏）。
 */
@Service
class StocktakeService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE s.deleted_at IS NULL ")
        if (!q.isNullOrBlank()) {
            cond.append("AND s.no ILIKE ? ")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND s.status = ? ")
            args.add(status)
        }
        val from = "FROM stocktake s LEFT JOIN warehouse w ON w.id = s.warehouse_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT s.id, s.no, w.name AS warehouse_name, s.take_date, s.status, s.diff_qty, s.remark
            $from ORDER BY s.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional(readOnly = true)
    fun items(id: String): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT i.id, i.product_id, i.product_name, i.unit, i.batch_no, i.location_code,
               i.book_qty, i.actual_qty, i.diff_qty, i.cost_price
        FROM stocktake_item i WHERE i.stocktake_id = ?::uuid ORDER BY i.product_name
        """.trimIndent(),
        id,
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    /** 创建盘点单：按仓库现有库存生成账面明细 */
    @Transactional
    fun create(req: StocktakeCreateRequest): Map<String, Any?> {
        if (req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "仓库不能为空")
        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("PD")

        jdbc.update(
            """
            INSERT INTO stocktake(id, no, warehouse_id, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.warehouseId, req.remark, me.userId.toString(),
        )

        val rows = jdbc.queryForList(
            """
            SELECT i.product_id, p.name AS product_name, p.unit, i.qty, i.avg_cost
            FROM inventory i JOIN product p ON p.id = i.product_id
            WHERE i.warehouse_id = ?::uuid
            """.trimIndent(),
            req.warehouseId,
        )
        if (rows.isEmpty()) throw BizException(ErrorCode.BIZ_CONFLICT, "该仓库当前无库存，无需盘点")
        rows.forEach { r ->
            jdbc.update(
                """
                INSERT INTO stocktake_item(stocktake_id, product_id, product_name, unit, book_qty, cost_price)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), r["product_id"].toString(), r["product_name"]?.toString(),
                r["unit"]?.toString(), bd(r["qty"]), bd(r["avg_cost"]),
            )
        }
        audit.log("inventory", "创建盘点单 $no（${rows.size} 行）", "stocktake", id.toString())
        return mapOf("id" to id.toString(), "no" to no, "status" to "draft", "lines" to rows.size)
    }

    /** 录入实盘数量，自动算差异 */
    @Transactional
    fun input(id: UUID, lines: List<StocktakeLine>) {
        if (lines.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "实盘明细不能为空")
        lines.forEach { l ->
            jdbc.update(
                """
                UPDATE stocktake_item
                SET actual_qty = ?, diff_qty = ? - book_qty, batch_no = ?, location_code = ?
                WHERE stocktake_id = ?::uuid AND product_id = ?::uuid
                """.trimIndent(),
                l.actualQty, l.actualQty, l.batchNo, l.locationCode, id.toString(), l.productId,
            )
        }
        jdbc.update("UPDATE stocktake SET status='counting', updated_at=now() WHERE id=?::uuid", id.toString())
        audit.log("inventory", "录入实盘 ${lines.size} 行", "stocktake", id.toString())
    }

    /** 审核：按差异调整库存并生成 adjust 流水 */
    @Transactional
    fun audit(id: UUID) {
        val head = jdbc.queryForMap("SELECT * FROM stocktake WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = head["status"]?.toString()
        if (status == "adjusted" || status == "void") throw BizException(ErrorCode.BIZ_CONFLICT, "盘点单已调整或作废")

        val no = head["no"]?.toString() ?: id.toString()
        val wid = head["warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少仓库")
        val rows = jdbc.queryForList("SELECT * FROM stocktake_item WHERE stocktake_id = ?::uuid", id.toString())
        if (rows.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "盘点明细为空")

        var totalDiff = BigDecimal.ZERO
        rows.forEach { r ->
            val diff = bd(r["diff_qty"])
            if (diff.compareTo(BigDecimal.ZERO) == 0) return@forEach
            val pid = r["product_id"].toString()
            val cost = bd(r["cost_price"])

            jdbc.update(
                """
                INSERT INTO inventory(product_id, warehouse_id, qty, locked_qty, avg_cost, last_cost)
                VALUES (?::uuid, ?::uuid, 0, 0, 0, 0)
                ON CONFLICT (product_id, warehouse_id) DO NOTHING
                """.trimIndent(),
                pid, wid,
            )
            jdbc.update(
                "UPDATE inventory SET qty = qty + ?, updated_at = now() WHERE product_id = ?::uuid AND warehouse_id = ?::uuid",
                diff, pid, wid,
            )
            val bal = bd(
                jdbc.queryForObject(
                    "SELECT qty FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid",
                    BigDecimal::class.java, pid, wid,
                )
            )
            jdbc.update(
                """
                INSERT INTO inventory_txn(txn_no, txn_type, product_id, product_name, warehouse_id, unit,
                                          qty, price, amount, balance_qty, balance_cost, ref_type, ref_no, remark)
                VALUES (?, 'adjust', ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, 'stocktake', ?, ?)
                """.trimIndent(),
                noGenerator.next("TXN"), pid, r["product_name"]?.toString(), wid, r["unit"]?.toString(),
                diff, cost, (diff * cost).setScale(2, RoundingMode.HALF_UP), bal, bal * cost, no, "盘点调整",
            )
            totalDiff += diff
        }

        jdbc.update(
            "UPDATE stocktake SET status='adjusted', diff_qty=?, updated_at=now() WHERE id=?::uuid",
            totalDiff, id.toString(),
        )
        audit.log("inventory", "审核盘点单 $no，差异合计 $totalDiff", "stocktake", id.toString())
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
