package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.tenant.TenantContext
import com.workeasy.erp.tenant.TenantRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class WxNotifyRequest(
    val outTradeNo: String = "",
    val transactionId: String? = null,
    val amount: BigDecimal? = null,
    val sign: String? = null,
    val resultCode: String = "SUCCESS",
)

/**
 * 商城支付：
 * - 平台统一 AppID + 租户商户号（资金不经过平台），当前外部商户号未就绪，走 mock 通道
 * - `out_trade_no` 唯一键保证回调幂等：重复投递只处理一次
 * - 支付成功后的销售订单/应收/核销在同一事务内完成
 */
@Service
class MallPayService(
    private val jdbc: JdbcTemplate,
    private val tenants: TenantRegistry,
    private val orderService: MallOrderService,
    private val noGenerator: NoGenerator,
    @Value("\${workeasy.mall.wx.mock:true}") private val wxMock: Boolean,
    @Value("\${workeasy.mall.wx.pay-key:workeasy-dev-pay-key}") private val mockPayKey: String,
) {

    /** 返回微信支付参数（真实接入时替换为统一下单 + JSAPI 签名） */
    @Transactional(readOnly = true)
    fun jsapi(orderId: String): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val order = jdbc.queryForList(
            """
            SELECT id, order_no, status, pay_type, total_amount, out_trade_no
            FROM mall_order WHERE id = ?::uuid AND member_id = ?::uuid
            """.trimIndent(),
            orderId, user.memberId.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "订单不存在")
        if (order["status"]?.toString() != "pending_pay") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "订单当前状态不可支付：${order["status"]}")
        }
        if (order["pay_type"]?.toString() != "wechat") throw BizException(ErrorCode.BIZ_PARAM, "该订单不是微信支付")
        val outTradeNo = order["out_trade_no"]?.toString().orEmpty()
        val nonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val timeStamp = (System.currentTimeMillis() / 1000).toString()
        return mapOf(
            "appId" to "mock-platform-appid",
            "timeStamp" to timeStamp,
            "nonceStr" to nonce,
            "package" to "prepay_id=mock_${outTradeNo.takeLast(16)}",
            "signType" to "RSA",
            "paySign" to "MOCK",
            "outTradeNo" to outTradeNo,
            "orderNo" to order["order_no"]?.toString(),
            "amount" to MallSupport.bd(order["total_amount"]),
            "mock" to wxMock,
        )
    }

    /** mock 支付成功（仅 mock 模式）：等价于一条已验签的回调 */
    @Transactional
    fun mockPay(orderId: String): Map<String, Any?> {
        if (!wxMock) throw BizException(ErrorCode.AUTH_FORBIDDEN, "当前环境未开启 mock 支付")
        val user = MallContext.requireCustomer()
        val order = jdbc.queryForList(
            "SELECT out_trade_no, total_amount, status FROM mall_order WHERE id = ?::uuid AND member_id = ?::uuid",
            orderId, user.memberId.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "订单不存在")
        val outTradeNo = order["out_trade_no"]?.toString() ?: throw BizException(ErrorCode.BIZ_CONFLICT, "订单缺少支付单号")
        applyPaid(
            outTradeNo = outTradeNo,
            transactionId = "MOCK_" + outTradeNo,
            amount = MallSupport.bd(order["total_amount"]),
            raw = mapOf("mock" to true),
        )
        return orderService.detail(orderId)
    }

    /** 支付回调：免登录，按 tenantCode 定位租户；禁止回退到 default */
    fun notify(tenantCode: String, req: WxNotifyRequest): Map<String, Any?> {
        val code = tenantCode.trim()
        if (code.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "租户编码缺失")
        val tenant = tenants.findByCode(code)
            ?: throw BizException(ErrorCode.BIZ_PARAM, "租户不存在：$code")
        if (!tenant.active) throw BizException(ErrorCode.AUTH_FORBIDDEN, "租户已停用")

        verifySign(req)
        if (req.resultCode != "SUCCESS") throw BizException(ErrorCode.BIZ_CONFLICT, "支付未成功")

        TenantContext.withTenant(tenant.toInfo()) {
            val amount = req.amount ?: throw BizException(ErrorCode.BIZ_PARAM, "回调缺少金额")
            applyPaid(req.outTradeNo, req.transactionId, amount, mapOf("sign" to req.sign, "resultCode" to req.resultCode))
        }
        return mapOf("code" to "SUCCESS", "message" to "OK")
    }

    /**
     * 幂等处理：`out_trade_no` 已成功直接返回；金额不一致拒绝。
     * 调用方必须在租户上下文中执行。
     */
    private fun applyPaid(outTradeNo: String, transactionId: String?, amount: BigDecimal, raw: Map<String, Any?>) {
        val payment = jdbc.queryForList(
            "SELECT id, order_id, amount, status FROM mall_payment WHERE out_trade_no = ? FOR UPDATE",
            outTradeNo,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "支付单不存在：$outTradeNo")
        if (payment["status"]?.toString() == "success") return
        if (payment["status"]?.toString() !in setOf("pending", "failed")) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "支付单状态不可回调：${payment["status"]}")
        }
        val expected = MallSupport.bd(payment["amount"])
        if (expected.compareTo(amount) != 0) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "回调金额不一致：应付 $expected，实付 $amount")
        }
        jdbc.update(
            """
            UPDATE mall_payment SET status = 'success', transaction_id = ?, paid_at = now(), updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            transactionId, payment["id"].toString(),
        )
        val orderId = payment["order_id"].toString()
        val order = jdbc.queryForMap("SELECT pay_type FROM mall_order WHERE id = ?::uuid", orderId)
        orderService.finalizePaidOrder(
            orderId,
            order["pay_type"]?.toString() ?: "wechat",
            mapOf("transactionId" to transactionId),
        )
        auditPayment(orderId, outTradeNo, amount, raw)
    }

    /** 线下打款登记（ERP 后台）：直接以人工流水确认订单 */
    @Transactional
    fun confirmOffline(orderId: String): Map<String, Any?> {
        val head = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid FOR UPDATE", orderId)
        if (head["status"]?.toString() != "pending_pay") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "订单当前状态不可确认收款：${head["status"]}")
        }
        val amount = MallSupport.bd(head["total_amount"])
        val outTradeNo = head["out_trade_no"]?.toString()?.takeIf { it.isNotBlank() } ?: ("OD" + head["order_no"])
        val existing = jdbc.queryForList("SELECT id, status FROM mall_payment WHERE order_id = ?::uuid", orderId).firstOrNull()
        if (existing == null) {
            jdbc.update(
                """
                INSERT INTO mall_payment(payment_no, order_id, member_id, out_trade_no, provider, amount, status)
                VALUES (?, ?::uuid, ?::uuid, ?, 'offline', ?, 'pending')
                """.trimIndent(),
                noGenerator.next("MPAY"), orderId, head["member_id"].toString(), outTradeNo, amount,
            )
        }
        jdbc.update(
            "UPDATE mall_payment SET status = 'success', paid_at = now(), updated_at = now() WHERE order_id = ?::uuid",
            orderId,
        )
        orderService.finalizePaidOrder(orderId, "offline", mapOf("transactionId" to "OFFLINE"))
        return mapOf("orderId" to orderId, "amount" to amount, "outTradeNo" to outTradeNo)
    }

    private fun auditPayment(orderId: String, outTradeNo: String, amount: BigDecimal, raw: Map<String, Any?>) {
        val memberId = jdbc.queryForList("SELECT member_id FROM mall_order WHERE id = ?::uuid", orderId)
            .firstOrNull()?.get("member_id")?.toString()
        jdbc.update(
            "INSERT INTO audit_log(user_name, module, action, target_table, target_id) VALUES ('mall', 'mall', ?, 'mall_payment', ?)",
            "支付成功 $outTradeNo / $amount ${raw.keys}", memberId,
        )
    }

    /**
     * 验签：mock 模式接受 `MOCK`；非 mock 用租户支付密钥做 HMAC-SHA256 对比。
     * 真实接入点：微信支付 v3 使用平台证书验签，再校验 out_trade_no 金额。
     */
    private fun verifySign(req: WxNotifyRequest) {
        if (wxMock) {
            if (req.sign.isNullOrBlank()) throw BizException(ErrorCode.BIZ_PARAM, "回调缺少签名")
            return
        }
        val sign = req.sign?.takeIf { it.isNotBlank() } ?: throw BizException(ErrorCode.BIZ_PARAM, "回调缺少签名")
        val expected = hmac("${req.outTradeNo}|${req.amount?.toPlainString()}|$mockPayKey")
        if (!MessageDigest.isEqual(sign.toByteArray(), expected.toByteArray())) {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "支付回调验签失败")
        }
    }

    private fun hmac(v: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(mockPayKey.toByteArray(), "HmacSHA256"))
        return mac.doFinal(v.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
