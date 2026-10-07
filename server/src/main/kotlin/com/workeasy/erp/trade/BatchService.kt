package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.system.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

data class BatchCreateRequest(
    val productId: String = "",
    val warehouseId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
    val costPrice: BigDecimal = BigDecimal.ZERO,
    val batchNo: String? = null,
    val locationCode: String? = null,
    val productDate: String? = null,
    val expireDate: String? = null,
)

/** 批次 / 库位：按批次追踪库存（与 inventory 的数量互为补充，批次用于追溯与效期管理） */
@Service
class BatchService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (b.batch_no ILIKE ? OR p.name ILIKE ? OR b.location_code ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
            args.add("%$q%")
        }
        val from = """
            FROM inventory_batch b
            JOIN product p ON p.id = b.product_id
            JOIN warehouse w ON w.id = b.warehouse_id
            $cond
        """.trimIndent()
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT b.id, b.batch_no, p.name AS product_name, p.sku, w.name AS warehouse_name,
                   b.location_code, b.qty, b.cost_price, b.inbound_date, b.expire_date
            $from ORDER BY b.created_at DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun create(req: BatchCreateRequest): Map<String, Any?> {
        if (req.productId.isBlank() || req.warehouseId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "商品与仓库不能为空")
        if (req.qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "数量必须大于 0")

        val no = req.batchNo?.takeIf { it.isNotBlank() } ?: noGenerator.next("B")
        val name = jdbc.queryForObject(
            "SELECT name FROM product WHERE id = ?::uuid", String::class.java, req.productId
        )
        jdbc.update(
            """
            INSERT INTO inventory_batch(batch_no, product_id, product_name, warehouse_id, location_code,
                                        qty, cost_price, product_date, expire_date)
            VALUES (?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?::date, ?::date)
            """.trimIndent(),
            no, req.productId, name, req.warehouseId, req.locationCode,
            req.qty, req.costPrice, req.productDate, req.expireDate,
        )
        audit.log("inventory", "登记批次 $no（$name × ${req.qty}）", "inventory_batch", req.productId)
        return mapOf("batchNo" to no, "status" to "active")
    }

    /** 即将过期（30 天内）与已过期的批次，供看板预警 */
    @Transactional(readOnly = true)
    fun expiring(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT batch_no, product_name, location_code, qty, expire_date,
               (expire_date - CURRENT_DATE) AS days_left
        FROM inventory_batch
        WHERE expire_date IS NOT NULL AND expire_date <= CURRENT_DATE + 30
        ORDER BY expire_date
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }
}
