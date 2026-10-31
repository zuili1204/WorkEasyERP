package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.system.SystemService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

data class CreditCheck(
    val customerId: String,
    val creditLimit: BigDecimal,
    val creditUsed: BigDecimal,
    val pendingAmount: BigDecimal,
    val available: BigDecimal,
    val requested: BigDecimal,
    val unlimited: Boolean,
    val over: Boolean,
    val mode: String,
    val action: String,     // ok / approval / block
    val message: String?,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "customerId" to customerId,
        "creditLimit" to creditLimit,
        "creditUsed" to creditUsed,
        "pendingAmount" to pendingAmount,
        "available" to available,
        "requested" to requested,
        "unlimited" to unlimited,
        "over" to over,
        "mode" to mode,
        "action" to action,
        "message" to message,
    )
}

/**
 * 授信统一校验：下单预览、订单确认、出库三处共用同一口径。
 *
 * 可用授信 = 授信额度 - 已用授信（出库占用）- 在途订单金额（未发货部分，含税）
 * 额度为 0 视为不限额。超限策略由 `sys_setting.credit.overflow_mode` 决定：
 * - approval（默认）：放行，转经理审批
 * - block：直接拒绝
 */
@Service
class CreditService(
    private val jdbc: JdbcTemplate,
    private val systemService: SystemService,
) {

    @Transactional(readOnly = true)
    fun check(customerId: String, amount: BigDecimal, excludeOrderId: String? = null): CreditCheck {
        val c = jdbc.queryForList(
            """
            SELECT COALESCE(credit_limit,0) AS credit_limit, COALESCE(credit_used,0) AS credit_used
            FROM customer WHERE id = ?::uuid AND deleted_at IS NULL
            """.trimIndent(),
            customerId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "客户不存在")

        val limit = bd(c["credit_limit"])
        val used = bd(c["credit_used"])
        val pending = pendingAmount(customerId, excludeOrderId)
        val mode = systemService.setting(KEY_OVERFLOW_MODE, "approval")

        if (limit <= BigDecimal.ZERO) {
            return CreditCheck(
                customerId, limit, used, pending, BigDecimal.ZERO, amount,
                unlimited = true, over = false, mode = mode, action = "ok", message = null,
            )
        }

        val available = (limit - used - pending).max(BigDecimal.ZERO)
        val over = used + pending + amount > limit
        val action = when {
            !over -> "ok"
            mode == "block" -> "block"
            else -> "approval"
        }
        val message = if (!over) null else {
            val tail = if (action == "block") "已拒绝" else "需经理审批后放行"
            "超出客户授信：额度 $limit，已用 $used，在途 $pending，本次 $amount，可用 $available（$tail）"
        }
        return CreditCheck(customerId, limit, used, pending, available, amount, false, over, mode, action, message)
    }

    /** 在途金额 = 未发货销售订单（草稿/审批中/已通过/部分发货）未发部分的含税金额 */
    private fun pendingAmount(customerId: String, excludeOrderId: String?): BigDecimal {
        val cond = if (excludeOrderId != null) "AND o.id <> ?::uuid" else ""
        val args = if (excludeOrderId != null) {
            arrayOf<Any>(customerId, excludeOrderId)
        } else {
            arrayOf<Any>(customerId)
        }
        return bd(
            jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(i.unship_qty * i.price * (1 + i.tax_rate)), 0)
                FROM sales_order o
                JOIN sales_order_item i ON i.order_id = o.id
                WHERE o.customer_id = ?::uuid
                  AND o.status IN ('draft','approving','approved','partial')
                  AND o.deleted_at IS NULL
                  $cond
                """.trimIndent(),
                BigDecimal::class.java,
                *args,
            )
        )
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    companion object {
        const val KEY_OVERFLOW_MODE = "credit.overflow_mode"
    }
}
