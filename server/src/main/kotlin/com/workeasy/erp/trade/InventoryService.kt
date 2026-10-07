package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.FieldPolicy
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

data class StockMoveRequest(
    val productId: String = "",
    val warehouseId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal? = null,      // 入库单价；出库不传（按加权成本计价）
    val batchNo: String? = null,
    val location: String? = null,
    val refType: String? = null,        // purchase/sales/adjust
    val refNo: String? = null,
    val remark: String? = null,
)

/**
 * 库存与流水：**移动加权平均成本**
 * - 入库：newAvg = (原数量×原均价 + 入库量×入库价) / (原数量+入库量)
 * - 出库：按当前加权成本计价，均价不变，仅扣数量
 * 并发安全：先 `INSERT ... ON CONFLICT DO NOTHING` 保证行存在，再 `SELECT ... FOR UPDATE` 加行锁。
 */
@Service
class InventoryService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun stock(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (p.name ILIKE ? OR p.sku ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = """
            FROM inventory i
            JOIN product p ON p.id = i.product_id
            JOIN warehouse w ON w.id = i.warehouse_id
            $cond
        """.trimIndent()
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT i.id, p.sku, p.name AS product_name, w.name AS warehouse_name, p.unit,
                   i.qty, i.available_qty, i.locked_qty, i.avg_cost, i.last_cost,
                   (i.qty * i.avg_cost) AS stock_amount
            $from
            ORDER BY p.sku
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(
            FieldPolicy.mask(rows.map { it.mapValues { (_, v) -> v?.toString() } }, UserContext.get().roles),
            total, page, size,
        )
    }

    @Transactional(readOnly = true)
    fun txns(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (t.product_name ILIKE ? OR t.txn_no ILIKE ? OR t.ref_no ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = "FROM inventory_txn t $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT t.* $from ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(
            FieldPolicy.mask(rows.map { it.mapValues { (_, v) -> v?.toString() } }, UserContext.get().roles),
            total, page, size,
        )
    }

    @Transactional
    fun inbound(req: StockMoveRequest) {
        validate(req)
        val price = req.price ?: throw BizException(ErrorCode.BIZ_PARAM, "入库必须填写单价")
        val row = lockRow(req.productId, req.warehouseId)
        val oldQty = bd(row["qty"])
        val oldAvg = bd(row["avg_cost"])
        val newQty = oldQty + req.qty
        val newAvg = if (newQty > BigDecimal.ZERO) {
            (oldQty * oldAvg + req.qty * price).divide(newQty, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        jdbc.update(
            """
            UPDATE inventory SET qty = ?, avg_cost = ?, last_cost = ?, last_in_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            newQty, newAvg, price, row["id"].toString(),
        )
        writeTxn(req, "in", req.qty, price, req.qty * price, newQty, newQty * newAvg)
        audit.log("inventory", "入库 ${req.qty} × $price", "inventory_txn", req.productId)
    }

    @Transactional
    fun outbound(req: StockMoveRequest) {
        validate(req)
        val row = lockRow(req.productId, req.warehouseId)
        val oldQty = bd(row["qty"])
        val avg = bd(row["avg_cost"])
        if (oldQty < req.qty) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "库存不足：现存 $oldQty，需出库 ${req.qty}")
        }
        val newQty = oldQty - req.qty
        val amount = req.qty * avg

        jdbc.update(
            """
            UPDATE inventory SET qty = ?, last_out_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            newQty, row["id"].toString(),
        )
        // 流水以负数表示出库
        writeTxn(req, "out", req.qty.negate(), avg, amount, newQty, newQty * avg)
        audit.log("inventory", "出库 ${req.qty}（成本价 $avg）", "inventory_txn", req.productId)
    }

    private fun validate(req: StockMoveRequest) {
        if (req.productId.isBlank() || req.warehouseId.isBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "商品与仓库不能为空")
        }
        if (req.qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "数量必须大于 0")
    }

    /** 保证库存行存在并加行锁 */
    private fun lockRow(productId: String, warehouseId: String): MutableMap<String, Any?> {
        jdbc.update(
            """
            INSERT INTO inventory(product_id, warehouse_id, qty, locked_qty, avg_cost, last_cost)
            VALUES (?::uuid, ?::uuid, 0, 0, 0, 0)
            ON CONFLICT (product_id, warehouse_id) DO NOTHING
            """.trimIndent(),
            productId, warehouseId,
        )
        return jdbc.queryForMap(
            "SELECT id, qty, locked_qty, avg_cost FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid FOR UPDATE",
            productId, warehouseId,
        )
    }

    private fun writeTxn(
        req: StockMoveRequest,
        type: String,
        qty: BigDecimal,
        price: BigDecimal,
        amount: BigDecimal,
        balanceQty: BigDecimal,
        balanceCost: BigDecimal,
    ) {
        val me = runCatching { UserContext.get() }.getOrNull()
        val name = jdbc.queryForObject(
            "SELECT name FROM product WHERE id = ?::uuid", String::class.java, req.productId
        )
        val unit = jdbc.queryForObject(
            "SELECT unit FROM product WHERE id = ?::uuid", String::class.java, req.productId
        )
        jdbc.update(
            """
            INSERT INTO inventory_txn(txn_no, txn_type, product_id, product_name, warehouse_id, location,
                                      batch_no, unit, qty, price, amount, balance_qty, balance_cost,
                                      ref_type, ref_no, operator_id, remark)
            VALUES (?, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?)
            """.trimIndent(),
            noGenerator.next("TXN"),
            type,
            req.productId,
            name,
            req.warehouseId,
            req.location,
            req.batchNo,
            unit,
            qty,
            price,
            amount,
            balanceQty,
            balanceCost,
            req.refType,
            req.refNo,
            me?.userId?.toString(),
            req.remark,
        )
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
