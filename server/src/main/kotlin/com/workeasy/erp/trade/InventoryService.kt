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
    val unit: String? = null,           // 录入单位；留空=主单位，辅助单位按换算率折算
    val price: BigDecimal? = null,      // 入库单价；出库不传（按加权成本计价）
    val batchNo: String? = null,
    val location: String? = null,
    val refType: String? = null,        // purchase/sales/adjust
    val refNo: String? = null,
    val remark: String? = null,
    val releaseLocked: Boolean = false, // 销售出库关联订单时：扣减现量同时释放锁定
    // 录入单位快照（由单据行带入，或手工出入库时按商品换算率解析），仅供流水回显
    val auxUnit: String? = null,
    val auxQty: BigDecimal? = null,
    val convertRate: BigDecimal? = null,
)

data class StockLockRequest(
    val productId: String = "",
    val warehouseId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val refType: String? = "sales_order",
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
        val entryPrice = req.price ?: throw BizException(ErrorCode.BIZ_PARAM, "入库必须填写单价")
        val u = UnitConversion.resolve(jdbc, req.productId, req.qty, entryPrice, req.unit)
        val row = lockRow(req.productId, req.warehouseId)
        val oldQty = bd(row["qty"])
        val oldAvg = bd(row["avg_cost"])
        val newQty = oldQty + u.qty
        val newAvg = if (newQty > BigDecimal.ZERO) {
            (oldQty * oldAvg + u.qty * u.price).divide(newQty, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        jdbc.update(
            """
            UPDATE inventory SET qty = ?, avg_cost = ?, last_cost = ?, last_in_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            newQty, newAvg, u.price, row["id"].toString(),
        )
        writeTxn(
            req, "in", u.qty, u.price, u.qty * u.price, newQty, newQty * newAvg,
            auxUnit = req.auxUnit ?: u.auxUnit,
            auxQty = req.auxQty ?: u.auxQty,
            convertRate = req.convertRate ?: u.convertRate,
        )
        audit.log("inventory", "入库 ${u.qty} × ${u.price}", "inventory_txn", req.productId)
    }

    @Transactional
    fun outbound(req: StockMoveRequest) {
        validate(req)
        val u = UnitConversion.resolve(jdbc, req.productId, req.qty, BigDecimal.ZERO, req.unit)
        val row = lockRow(req.productId, req.warehouseId)
        val oldQty = bd(row["qty"])
        val avg = bd(row["avg_cost"])
        if (oldQty < u.qty) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "库存不足：现存 $oldQty，需出库 ${u.qty}")
        }
        val newQty = oldQty - u.qty
        val amount = u.qty * avg
        // 关联销售订单出库：同一行锁内释放锁定，避免「先出库后释放」的并发窗口
        val newLocked = if (req.releaseLocked) {
            bd(row["locked_qty"]).subtract(u.qty).max(BigDecimal.ZERO)
        } else {
            bd(row["locked_qty"])
        }

        jdbc.update(
            """
            UPDATE inventory SET qty = ?, locked_qty = ?, last_out_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            newQty, newLocked, row["id"].toString(),
        )
        // 流水以负数表示出库
        writeTxn(
            req, "out", u.qty.negate(), avg, amount, newQty, newQty * avg,
            auxUnit = req.auxUnit ?: u.auxUnit,
            auxQty = req.auxQty ?: u.auxQty,
            convertRate = req.convertRate ?: u.convertRate,
        )
        audit.log("inventory", "出库 ${u.qty}（成本价 $avg）", "inventory_txn", req.productId)
    }

    /**
     * 锁定库存（下单/确认）：只增 `locked_qty`，不动现量与成本。
     * 可用量不足直接拒绝，保证并发下单不超卖。
     */
    @Transactional
    fun lock(req: StockLockRequest) {
        validateLock(req)
        val row = lockRow(req.productId, req.warehouseId)
        val available = bd(row["qty"]) - bd(row["locked_qty"])
        if (available < req.qty) {
            throw BizException(
                ErrorCode.BIZ_CONFLICT,
                "可用库存不足：可用 $available，需锁定 ${req.qty}",
            )
        }
        jdbc.update(
            "UPDATE inventory SET locked_qty = locked_qty + ?, updated_at = now() WHERE id = ?::uuid",
            req.qty, row["id"].toString(),
        )
        audit.log("inventory", "锁定 ${req.qty}（${req.refNo ?: "-"}）", "inventory", req.productId)
    }

    /** 释放锁定（取消/超时）：只减 `locked_qty`，最大减到 0，可重复调用 */
    @Transactional
    fun release(req: StockLockRequest) {
        if (req.qty <= BigDecimal.ZERO) return
        // 行不存在说明从未锁过，直接视为已释放
        val row = jdbc.queryForList(
            "SELECT id, locked_qty FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid FOR UPDATE",
            req.productId, req.warehouseId,
        ).firstOrNull() ?: return
        val newLocked = bd(row["locked_qty"]).subtract(req.qty).max(BigDecimal.ZERO)
        jdbc.update(
            "UPDATE inventory SET locked_qty = ?, updated_at = now() WHERE id = ?::uuid",
            newLocked, row["id"].toString(),
        )
        audit.log("inventory", "释放锁定 ${req.qty}（${req.refNo ?: "-"}）", "inventory", req.productId)
    }

    /**
     * 调拨过账：同一事务内从调出仓扣减、调入仓增加，成本按调出仓移动加权价。
     * 两仓行锁按仓库 ID 排序获取，避免 A→B / B→A 并发死锁；调拨不改变锁定量。
     */
    @Transactional
    fun transfer(
        productId: String,
        fromWarehouseId: String,
        toWarehouseId: String,
        qty: BigDecimal,
        refNo: String,
        remark: String? = null,
    ): BigDecimal {
        if (productId.isBlank() || fromWarehouseId.isBlank() || toWarehouseId.isBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "商品与调出/调入仓库不能为空")
        }
        if (qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "调拨数量必须大于 0")
        if (fromWarehouseId == toWarehouseId) throw BizException(ErrorCode.BIZ_PARAM, "调出与调入仓库不能相同")

        ensureRow(productId, fromWarehouseId)
        ensureRow(productId, toWarehouseId)
        val locked = mutableMapOf<String, MutableMap<String, Any?>>()
        listOf(fromWarehouseId, toWarehouseId).sorted().forEach { wh ->
            locked[wh] = jdbc.queryForMap(
                "SELECT id, qty, locked_qty, avg_cost FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid FOR UPDATE",
                productId, wh,
            )
        }
        val src = locked.getValue(fromWarehouseId)
        val dst = locked.getValue(toWarehouseId)

        val srcQty = bd(src["qty"])
        val srcAvailable = srcQty - bd(src["locked_qty"])
        if (srcAvailable < qty) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "调出仓可用库存不足：可用 $srcAvailable，需调拨 $qty")
        }
        val cost = bd(src["avg_cost"])
        val newSrcQty = srcQty - qty

        val dstQty = bd(dst["qty"])
        val dstAvg = bd(dst["avg_cost"])
        val newDstQty = dstQty + qty
        val newDstAvg = if (newDstQty > BigDecimal.ZERO) {
            (dstQty * dstAvg + qty * cost).divide(newDstQty, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        jdbc.update(
            "UPDATE inventory SET qty = ?, last_out_at = now(), updated_at = now() WHERE id = ?::uuid",
            newSrcQty, src["id"].toString(),
        )
        jdbc.update(
            "UPDATE inventory SET qty = ?, avg_cost = ?, last_cost = ?, last_in_at = now(), updated_at = now() WHERE id = ?::uuid",
            newDstQty, newDstAvg, cost, dst["id"].toString(),
        )

        val outReq = StockMoveRequest(
            productId = productId, warehouseId = fromWarehouseId,
            refType = "transfer", refNo = refNo, remark = remark ?: "调拨出",
        )
        val inReq = StockMoveRequest(
            productId = productId, warehouseId = toWarehouseId,
            refType = "transfer", refNo = refNo, remark = remark ?: "调拨入",
        )
        writeTxn(outReq, "transfer", qty.negate(), cost, qty * cost, newSrcQty, newSrcQty * cost)
        writeTxn(inReq, "transfer", qty, cost, qty * cost, newDstQty, newDstQty * newDstAvg)
        audit.log("inventory", "调拨 ${qty}（$refNo）", "inventory_txn", productId)
        return cost
    }

    private fun validateLock(req: StockLockRequest) {
        if (req.productId.isBlank() || req.warehouseId.isBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "商品与仓库不能为空")
        }
        if (req.qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "锁定数量必须大于 0")
    }

    private fun validate(req: StockMoveRequest) {
        if (req.productId.isBlank() || req.warehouseId.isBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "商品与仓库不能为空")
        }
        if (req.qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "数量必须大于 0")
    }

    /** 保证库存行存在（不锁） */
    private fun ensureRow(productId: String, warehouseId: String) {
        jdbc.update(
            """
            INSERT INTO inventory(product_id, warehouse_id, qty, locked_qty, avg_cost, last_cost)
            VALUES (?::uuid, ?::uuid, 0, 0, 0, 0)
            ON CONFLICT (product_id, warehouse_id) DO NOTHING
            """.trimIndent(),
            productId, warehouseId,
        )
    }

    /** 保证库存行存在并加行锁 */
    private fun lockRow(productId: String, warehouseId: String): MutableMap<String, Any?> {
        ensureRow(productId, warehouseId)
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
        auxUnit: String? = null,
        auxQty: BigDecimal? = null,
        convertRate: BigDecimal? = BigDecimal.ONE,
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
                                      aux_unit, aux_qty, convert_rate,
                                      ref_type, ref_no, operator_id, remark)
            VALUES (?, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?)
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
            auxUnit,
            auxQty,
            convertRate ?: BigDecimal.ONE,
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
