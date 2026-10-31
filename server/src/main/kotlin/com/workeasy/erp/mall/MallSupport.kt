package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

/** 商城与 ERP 共用的轻量取数辅助（价格/库存/授信的权威计算仍在 ERP 服务） */
object MallSupport {

    fun config(jdbc: JdbcTemplate, key: String, default: String): String =
        jdbc.queryForList("SELECT value FROM mall_config WHERE key = ?", String::class.java, key)
            .firstOrNull()?.takeIf { it.isNotBlank() } ?: default

    /** 商城默认发货仓：配置优先，未配置取第一个启用仓库 */
    fun defaultWarehouse(jdbc: JdbcTemplate): UUID {
        val configured = config(jdbc, "default_warehouse_id", "")
        if (configured.isNotBlank()) {
            val exists = jdbc.queryForList(
                "SELECT id FROM warehouse WHERE id = ?::uuid AND status = 'active'",
                configured,
            ).firstOrNull()
            if (exists != null) return UUID.fromString(exists["id"].toString())
        }
        val row = jdbc.queryForList(
            "SELECT id FROM warehouse WHERE status = 'active' ORDER BY created_at LIMIT 1",
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_CONFLICT, "尚未配置可用仓库，商城无法下单")
        return UUID.fromString(row["id"].toString())
    }

    /** 指定仓库可用量（主单位） */
    fun available(jdbc: JdbcTemplate, warehouseId: UUID, productId: String): BigDecimal = bd(
        jdbc.queryForObject(
            "SELECT COALESCE(available_qty,0) FROM inventory WHERE product_id = ?::uuid AND warehouse_id = ?::uuid",
            BigDecimal::class.java,
            productId, warehouseId.toString(),
        )
    )

    fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    /** 起订量/步长校验（数量为主单位） */
    fun validatePurchaseQty(jdbc: JdbcTemplate, productId: String, qty: BigDecimal): BigDecimal {
        if (qty <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "购买数量必须大于 0")
        val p = jdbc.queryForList(
            "SELECT min_order_qty, step_qty, online_status, status FROM product WHERE id = ?::uuid AND deleted_at IS NULL",
            productId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
        if (p["status"]?.toString() != "active" || p["online_status"]?.toString() != "online") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "商品已下架")
        }
        val min = bd(p["min_order_qty"]).max(BigDecimal.ONE)
        val step = bd(p["step_qty"]).max(BigDecimal.ZERO)
        if (qty < min) throw BizException(ErrorCode.BIZ_PARAM, "低于起订量 $min")
        if (step > BigDecimal.ZERO && qty > min) {
            val diff = qty - min
            if (step > BigDecimal.ONE && diff.remainder(step) != BigDecimal.ZERO) {
                throw BizException(ErrorCode.BIZ_PARAM, "购买数量需按步长 $step 递增（起订 $min）")
            }
        }
        return qty
    }
}
