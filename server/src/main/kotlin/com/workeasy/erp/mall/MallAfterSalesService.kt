package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class AfterSalesRequest(
    val orderId: String = "",
    val reason: String = "",
    val amount: BigDecimal? = null,
)

/**
 * 售后申请：会员提交 → 后台审核 → 生成 ERP 退货单（sales_return，draft）。
 * 退货入库与应收红冲沿用既有退货单据流程，不在商城域重复实现。
 */
@Service
class MallAfterSalesService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
) {

    @Transactional
    fun apply(req: AfterSalesRequest): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        if (req.orderId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "订单不能为空")
        if (req.reason.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "请填写售后原因")
        val order = jdbc.queryForList(
            "SELECT id, order_no, status, total_amount FROM mall_order WHERE id = ?::uuid AND member_id = ?::uuid",
            req.orderId, user.memberId.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "订单不存在")
        if (order["status"]?.toString() !in setOf("shipped", "completed")) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "仅已发货/已完成订单可申请售后")
        }
        val total = MallSupport.bd(order["total_amount"])
        val amount = req.amount ?: total
        if (amount <= BigDecimal.ZERO || amount > total) {
            throw BizException(ErrorCode.BIZ_PARAM, "退款金额需在 0 与订单金额之间")
        }
        val exists = jdbc.queryForList(
            "SELECT id FROM mall_refund WHERE order_id = ?::uuid AND status IN ('applied','approved')",
            req.orderId,
        ).firstOrNull()
        if (exists != null) throw BizException(ErrorCode.BIZ_CONFLICT, "该订单已有处理中的售后单")

        val refundId = UUID.randomUUID()
        val refundNo = noGenerator.next("MRF")
        jdbc.update(
            """
            INSERT INTO mall_refund(id, refund_no, order_id, member_id, amount, reason, status)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?, ?, 'applied')
            """.trimIndent(),
            refundId.toString(), refundNo, req.orderId, user.memberId.toString(), amount, req.reason.take(500),
        )
        return mapOf("id" to refundId.toString(), "refundNo" to refundNo, "status" to "applied", "amount" to amount)
    }

    @Transactional(readOnly = true)
    fun myRefunds(): List<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        return jdbc.queryForList(
            """
            SELECT r.id::text AS "id", r.refund_no AS "refundNo", o.order_no AS "orderNo",
                   r.amount, r.reason, r.status, r.created_at AS "createdAt"
            FROM mall_refund r JOIN mall_order o ON o.id = r.order_id
            WHERE r.member_id = ?::uuid ORDER BY r.created_at DESC
            """.trimIndent(),
            user.memberId.toString(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
    }

    /** 后台审核：通过时生成 ERP 退货单（draft），后续按既有退货流程入库/红冲 */
    @Transactional
    fun approve(refundId: String, remark: String?): Map<String, Any?> {
        val refund = jdbc.queryForMap("SELECT * FROM mall_refund WHERE id = ?::uuid FOR UPDATE", refundId)
        if (refund["status"]?.toString() != "applied") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "售后单状态不可审核：${refund["status"]}")
        }
        val orderId = refund["order_id"].toString()
        val order = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid", orderId)
        val items = jdbc.queryForList(
            "SELECT product_id, product_name, unit, qty, price, tax_rate FROM mall_order_item WHERE order_id = ?::uuid",
            orderId,
        )
        val returnId = UUID.randomUUID()
        val returnNo = noGenerator.next("SRT")
        val amount = MallSupport.bd(refund["amount"])
        var totalQty = BigDecimal.ZERO
        items.forEach { totalQty += MallSupport.bd(it["qty"]) }

        jdbc.update(
            """
            INSERT INTO sales_return(id, return_no, order_id, customer_id, warehouse_id, reason, status,
                                     total_qty, total_amount)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?::uuid, ?, 'draft', ?, ?)
            """.trimIndent(),
            returnId.toString(), returnNo,
            order["sales_order_id"]?.toString(), refund["member_id"]?.toString()?.let { order["customer_id"].toString() },
            order["warehouse_id"]?.toString(), "商城售后：${refund["reason"]}",
            totalQty, amount.setScale(2, RoundingMode.HALF_UP),
        )
        items.forEach { row ->
            val qty = MallSupport.bd(row["qty"])
            val price = MallSupport.bd(row["price"])
            val taxRate = MallSupport.bd(row["tax_rate"])
            jdbc.update(
                """
                INSERT INTO sales_return_item(return_id, product_id, product_name, unit, qty, price, tax_rate, amount)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                returnId.toString(), row["product_id"]?.toString(), row["product_name"]?.toString(),
                row["unit"]?.toString(), qty, price, taxRate,
                (qty * price * (BigDecimal.ONE + taxRate)).setScale(2, RoundingMode.HALF_UP),
            )
        }
        jdbc.update(
            """
            UPDATE mall_refund SET status = 'approved', sales_return_id = ?::uuid, remark = ?, updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            returnId.toString(), remark, refundId,
        )
        return mapOf("id" to refundId, "status" to "approved", "salesReturnId" to returnId.toString(), "returnNo" to returnNo)
    }

    @Transactional
    fun reject(refundId: String, remark: String?) {
        val n = jdbc.update(
            """
            UPDATE mall_refund SET status = 'rejected', remark = ?, updated_at = now()
            WHERE id = ?::uuid AND status = 'applied'
            """.trimIndent(),
            remark, refundId,
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_CONFLICT, "售后单状态不可驳回")
    }
}
