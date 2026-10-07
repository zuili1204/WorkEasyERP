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

data class ReturnItemRequest(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal = BigDecimal.ZERO,
    val taxRate: BigDecimal = BigDecimal("0.13"),
)

data class SalesReturnRequest(
    val customerId: String = "",
    val warehouseId: String = "",
    val orderId: String? = null,
    val outboundId: String? = null,
    val reason: String? = null,
    val items: List<ReturnItemRequest> = emptyList(),
)

data class PurchaseReturnRequest(
    val supplierId: String = "",
    val warehouseId: String = "",
    val orderId: String? = null,
    val remark: String? = null,
    val items: List<ReturnItemRequest> = emptyList(),
)

/**
 * 退货：
 * - 销售退货：回库（按当前加权成本入库）→ 红冲应收（减少 ar_ledger 余额，归零置 closed）→ 释放授信
 * - 采购退货：扣减库存 → 红冲应付（减少 ap_ledger 余额）
 */
@Service
class ReturnService(
    private val jdbc: JdbcTemplate,
    private val inventoryService: InventoryService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    // ---------- 列表 ----------

    @Transactional(readOnly = true)
    fun salesReturns(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (r.return_no ILIKE ? OR c.name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = "FROM sales_return r LEFT JOIN customer c ON c.id = r.customer_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id, r.return_no, c.name AS customer_name, r.return_date, r.status,
                   r.total_qty, r.total_amount, r.ar_offset_amount
            $from ORDER BY r.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional(readOnly = true)
    fun purchaseReturns(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE r.deleted_at IS NULL ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (r.no ILIKE ? OR s.name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = "FROM purchase_return r LEFT JOIN supplier s ON s.id = r.supplier_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id, r.no AS return_no, s.name AS supplier_name, r.return_date, r.status,
                   r.total_amount, r.ap_offset_amount
            $from ORDER BY r.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    // ---------- 销售退货 ----------

    @Transactional
    fun createSalesReturn(req: SalesReturnRequest): Map<String, Any?> {
        if (req.customerId.isBlank() || req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "客户与仓库不能为空")
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "退货明细不能为空")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("SRT")

        var totalQty = BigDecimal.ZERO
        var totalAmount = BigDecimal.ZERO
        jdbc.update(
            """
            INSERT INTO sales_return(id, return_no, order_id, outbound_id, customer_id, warehouse_id, reason, status, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, 'draft', ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.orderId, req.outboundId, req.customerId, req.warehouseId, req.reason, me.userId.toString(),
        )
        req.items.forEach { line ->
            val p = jdbc.queryForMap(
                "SELECT name, unit FROM product WHERE id = ?::uuid", line.productId
            )
            val amount = line.qty * line.price * (BigDecimal.ONE + line.taxRate)
            totalQty += line.qty
            totalAmount += amount
            jdbc.update(
                """
                INSERT INTO sales_return_item(return_id, product_id, product_name, unit, qty, price, tax_rate, amount)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, p["name"].toString(), p["unit"]?.toString(),
                line.qty, line.price, line.taxRate, amount.setScale(2, RoundingMode.HALF_UP),
            )
        }
        jdbc.update(
            "UPDATE sales_return SET total_qty = ?, total_amount = ? WHERE id = ?::uuid",
            totalQty, totalAmount.setScale(2, RoundingMode.HALF_UP), id.toString(),
        )
        audit.log("sales", "创建销售退货单 $no", "sales_return", id.toString())
        return mapOf("id" to id.toString(), "returnNo" to no, "status" to "draft")
    }

    /** 审核销售退货：回库 + 红冲应收 + 释放授信 */
    @Transactional
    fun auditSalesReturn(id: UUID) {
        val head = jdbc.queryForMap("SELECT * FROM sales_return WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = head["status"]?.toString()
        if (status == "done" || status == "void") throw BizException(ErrorCode.BIZ_CONFLICT, "退货单已审核或作废")

        val no = head["return_no"]?.toString() ?: id.toString()
        val warehouseId = head["warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少仓库")
        val items = jdbc.queryForList("SELECT * FROM sales_return_item WHERE return_id = ?::uuid", id.toString())
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "退货明细为空")

        // 回库：按当前加权成本入库，避免把加权成本拉偏
        items.forEach { it ->
            val pid = it["product_id"].toString()
            val avg = bd(
                jdbc.queryForObject(
                    "SELECT COALESCE(MAX(avg_cost),0) FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid",
                    BigDecimal::class.java, pid, warehouseId,
                )
            )
            inventoryService.inbound(
                StockMoveRequest(
                    productId = pid,
                    warehouseId = warehouseId,
                    qty = bd(it["qty"]),
                    price = avg,
                    refType = "sales_return",
                    refNo = no,
                    remark = "销售退货回库 $no",
                )
            )
        }

        // 红冲应收：按到期日早优先冲减该客户未结应收
        var remain = bd(head["total_amount"])
        val ledgers = jdbc.queryForList(
            """
            SELECT id, amount, settled_amount, remain_amount FROM ar_ledger
            WHERE customer_id = ?::uuid AND status <> 'closed'
            ORDER BY due_date NULLS LAST, created_at
            """.trimIndent(),
            head["customer_id"].toString(),
        )
        ledgers.forEach { row ->
            if (remain <= BigDecimal.ZERO) return@forEach
            val ledgerRemain = bd(row["remain_amount"])
            val use = if (remain <= ledgerRemain) remain else ledgerRemain
            val newRemain = ledgerRemain - use
            jdbc.update(
                """
                UPDATE ar_ledger SET amount = amount - ?, remain_amount = ?,
                       status = CASE WHEN ? <= 0 THEN 'closed' ELSE status END
                WHERE id = ?::uuid
                """.trimIndent(),
                use, newRemain, newRemain, row["id"].toString(),
            )
            remain -= use
        }

        jdbc.update(
            "UPDATE sales_return SET status = 'done', ar_offset_amount = ? WHERE id = ?::uuid",
            bd(head["total_amount"]) - remain, id.toString(),
        )
        // 释放授信
        jdbc.update(
            "UPDATE customer SET credit_used = GREATEST(COALESCE(credit_used,0) - ?, 0) WHERE id = ?::uuid",
            bd(head["total_amount"]) - remain,
            head["customer_id"].toString(),
        )
        audit.log("sales", "审核销售退货 $no，红冲应收 ${bd(head["total_amount"]) - remain}", "sales_return", id.toString())
    }

    // ---------- 采购退货 ----------

    @Transactional
    fun createPurchaseReturn(req: PurchaseReturnRequest): Map<String, Any?> {
        if (req.supplierId.isBlank() || req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "供应商与仓库不能为空")
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "退货明细不能为空")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("PRT")

        var total = BigDecimal.ZERO
        jdbc.update(
            """
            INSERT INTO purchase_return(id, no, purchase_order_id, supplier_id, warehouse_id, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::uuid, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.orderId, req.supplierId, req.warehouseId, req.remark, me.userId.toString(),
        )
        req.items.forEach { line ->
            val cost = bd(
                jdbc.queryForObject(
                    "SELECT COALESCE(MAX(avg_cost),0) FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid",
                    BigDecimal::class.java, line.productId, req.warehouseId,
                )
            )
            val amount = line.qty * cost
            total += amount
            jdbc.update(
                """
                INSERT INTO purchase_return_item(return_id, product_id, qty, cost_price, amount)
                VALUES (?::uuid, ?::uuid, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, line.qty, cost, amount.setScale(2, RoundingMode.HALF_UP),
            )
        }
        jdbc.update(
            "UPDATE purchase_return SET total_amount = ? WHERE id = ?::uuid",
            total.setScale(2, RoundingMode.HALF_UP), id.toString(),
        )
        audit.log("purchase", "创建采购退货单 $no", "purchase_return", id.toString())
        return mapOf("id" to id.toString(), "returnNo" to no, "status" to "draft")
    }

    /** 审核采购退货：扣减库存 + 红冲应付 */
    @Transactional
    fun auditPurchaseReturn(id: UUID) {
        val head = jdbc.queryForMap("SELECT * FROM purchase_return WHERE id = ?::uuid FOR UPDATE", id.toString())
        val status = head["status"]?.toString()
        if (status == "done" || status == "void") throw BizException(ErrorCode.BIZ_CONFLICT, "退货单已审核或作废")

        val no = head["no"]?.toString() ?: id.toString()
        val warehouseId = head["warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少仓库")
        val items = jdbc.queryForList("SELECT * FROM purchase_return_item WHERE return_id = ?::uuid", id.toString())
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "退货明细为空")

        items.forEach { it ->
            inventoryService.outbound(
                StockMoveRequest(
                    productId = it["product_id"].toString(),
                    warehouseId = warehouseId,
                    qty = bd(it["qty"]),
                    refType = "purchase_return",
                    refNo = no,
                    remark = "采购退货出库 $no",
                )
            )
        }

        var remain = bd(head["total_amount"])
        val ledgers = jdbc.queryForList(
            """
            SELECT id, amount, settled_amount, remain_amount FROM ap_ledger
            WHERE supplier_id = ?::uuid AND status <> 'closed'
            ORDER BY due_date NULLS LAST, created_at
            """.trimIndent(),
            head["supplier_id"].toString(),
        )
        ledgers.forEach { row ->
            if (remain <= BigDecimal.ZERO) return@forEach
            val ledgerRemain = bd(row["remain_amount"])
            val use = if (remain <= ledgerRemain) remain else ledgerRemain
            val newRemain = ledgerRemain - use
            jdbc.update(
                """
                UPDATE ap_ledger SET amount = amount - ?, remain_amount = ?,
                       status = CASE WHEN ? <= 0 THEN 'closed' ELSE status END
                WHERE id = ?::uuid
                """.trimIndent(),
                use, newRemain, newRemain, row["id"].toString(),
            )
            remain -= use
        }

        jdbc.update(
            "UPDATE purchase_return SET status = 'done', ap_offset_amount = ?, updated_at = now() WHERE id = ?::uuid",
            bd(head["total_amount"]) - remain, id.toString(),
        )
        audit.log("purchase", "审核采购退货 $no，红冲应付 ${bd(head["total_amount"]) - remain}", "purchase_return", id.toString())
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
