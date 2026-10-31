package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.common.Paging
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class TransferItemRequest(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
)

data class TransferCreateRequest(
    val fromWarehouseId: String = "",
    val toWarehouseId: String = "",
    val remark: String? = null,
    val items: List<TransferItemRequest> = emptyList(),
)

/**
 * 调拨单：草稿 → 审核。
 * 审核时逐行调用 [InventoryService.transfer]，同一事务内两仓原子过账；
 * 任一行失败整单回滚，两仓数量合计守恒。
 */
@Service
class TransferService(
    private val jdbc: JdbcTemplate,
    private val inventoryService: InventoryService,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val (pg, pgSize) = Paging.clamp(page, size)
        val kw = Paging.keyword(q)
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (kw != null) {
            cond.append("AND (transfer_no ILIKE ? OR from_warehouse_name ILIKE ? OR to_warehouse_name ILIKE ?) ")
            args.add("%$kw%")
            args.add("%$kw%")
            args.add("%$kw%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND status = ? ")
            args.add(status)
        }
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM transfer_order $cond", Long::class.java, *args.toTypedArray()
        ) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT id, transfer_no, from_warehouse_name, to_warehouse_name,
                   status, total_qty, remark, audited_at, created_at
            FROM transfer_order $cond
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, pg, pgSize)
    }

    @Transactional(readOnly = true)
    fun detail(id: String): Map<String, Any?> {
        val head = jdbc.queryForList(
            "SELECT * FROM transfer_order WHERE id = ?::uuid",
            id,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "调拨单不存在")
        val items = jdbc.queryForList(
            "SELECT * FROM transfer_order_item WHERE order_id = ?::uuid ORDER BY created_at",
            id,
        )
        return mapOf(
            "order" to head.mapValues { (_, v) -> v?.toString() },
            "items" to items.map { it.mapValues { (_, v) -> v?.toString() } },
        )
    }

    @Transactional
    fun create(req: TransferCreateRequest): Map<String, Any?> {
        if (req.fromWarehouseId.isBlank() || req.toWarehouseId.isBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "调出/调入仓库不能为空")
        }
        if (req.fromWarehouseId == req.toWarehouseId) {
            throw BizException(ErrorCode.BIZ_PARAM, "调出与调入仓库不能相同")
        }
        if (req.items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行不能为空")

        val fromName = warehouseName(req.fromWarehouseId)
        val toName = warehouseName(req.toWarehouseId)
        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("TR")

        var totalQty = BigDecimal.ZERO
        jdbc.update(
            """
            INSERT INTO transfer_order(id, transfer_no, from_warehouse_id, from_warehouse_name,
                                       to_warehouse_id, to_warehouse_name, status, remark, created_by)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?, 'draft', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.fromWarehouseId, fromName,
            req.toWarehouseId, toName, req.remark, me.userId.toString(),
        )
        req.items.forEach { line ->
            if (line.qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "调拨数量必须大于 0")
            val p = jdbc.queryForList(
                "SELECT name, unit FROM product WHERE id = ?::uuid AND deleted_at IS NULL",
                line.productId,
            ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
            totalQty += line.qty
            jdbc.update(
                """
                INSERT INTO transfer_order_item(order_id, product_id, product_name, unit, qty)
                VALUES (?::uuid, ?::uuid, ?, ?, ?)
                """.trimIndent(),
                id.toString(), line.productId, p["name"], p["unit"], line.qty,
            )
        }
        jdbc.update("UPDATE transfer_order SET total_qty = ? WHERE id = ?::uuid", totalQty, id.toString())
        audit.log("inventory", "创建调拨单 $no（$fromName → $toName）", "transfer_order", id.toString())
        return mapOf("id" to id.toString(), "transferNo" to no, "status" to "draft", "totalQty" to totalQty)
    }

    /** 审核：两仓原子过账，成功后单据置 done；重复审核直接拒绝 */
    @Transactional
    fun audit(id: UUID): Map<String, Any?> {
        val head = jdbc.queryForMap(
            "SELECT * FROM transfer_order WHERE id = ?::uuid FOR UPDATE", id.toString()
        )
        val status = head["status"]?.toString()
        if (status != "draft") throw BizException(ErrorCode.BIZ_CONFLICT, "仅草稿调拨单可审核（当前：$status）")
        val no = head["transfer_no"]?.toString() ?: id.toString()
        val from = head["from_warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少调出仓库")
        val to = head["to_warehouse_id"]?.toString() ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少调入仓库")

        val items = jdbc.queryForList(
            "SELECT id, product_id, qty FROM transfer_order_item WHERE order_id = ?::uuid ORDER BY created_at",
            id.toString(),
        )
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "明细行为空，无法审核")

        items.forEach { item ->
            val productId = item["product_id"]?.toString() ?: return@forEach
            val cost = inventoryService.transfer(
                productId = productId,
                fromWarehouseId = from,
                toWarehouseId = to,
                qty = bd(item["qty"]),
                refNo = no,
                remark = "调拨单 $no",
            )
            jdbc.update(
                "UPDATE transfer_order_item SET cost = ? WHERE id = ?::uuid",
                cost, item["id"].toString(),
            )
        }

        val me = UserContext.get()
        jdbc.update(
            """
            UPDATE transfer_order SET status = 'done', audited_by = ?::uuid, audited_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            me.userId.toString(), id.toString(),
        )
        audit.log("inventory", "审核调拨单 $no", "transfer_order", id.toString())
        return mapOf("id" to id.toString(), "status" to "done")
    }

    /** 作废草稿调拨单（已审核的不允许作废，避免账实不符） */
    @Transactional
    fun cancel(id: UUID): Map<String, Any?> {
        val head = jdbc.queryForMap(
            "SELECT transfer_no, status FROM transfer_order WHERE id = ?::uuid FOR UPDATE", id.toString()
        )
        val status = head["status"]?.toString()
        if (status != "draft") throw BizException(ErrorCode.BIZ_CONFLICT, "仅草稿调拨单可作废（当前：$status）")
        jdbc.update("UPDATE transfer_order SET status = 'void', updated_at = now() WHERE id = ?::uuid", id.toString())
        audit.log("inventory", "作废调拨单 ${head["transfer_no"]}", "transfer_order", id.toString())
        return mapOf("id" to id.toString(), "status" to "void")
    }

    private fun warehouseName(id: String): String = jdbc.queryForList(
        "SELECT name FROM warehouse WHERE id = ?::uuid", String::class.java, id,
    ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "仓库不存在")

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
