package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.common.Paging
import com.workeasy.erp.mall.mapper.MallQueryMapper
import com.workeasy.erp.trade.CreditService
import com.workeasy.erp.trade.InventoryService
import com.workeasy.erp.trade.PriceService
import com.workeasy.erp.trade.QuoteItemRequest
import com.workeasy.erp.trade.QuoteRequest
import com.workeasy.erp.trade.StockLockRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class MallOrderItemRequest(
    val productId: String = "",
    val qty: BigDecimal = BigDecimal.ZERO,
)

data class MallOrderPreviewRequest(
    val items: List<MallOrderItemRequest> = emptyList(),
    val addressId: String? = null,
    val payType: String? = null,
    val remark: String? = null,
)

data class MallOrderCreateRequest(
    val items: List<MallOrderItemRequest> = emptyList(),
    val addressId: String = "",
    val payType: String = "wechat",
    val remark: String? = null,
)

private data class PreparedLine(
    val productId: String,
    val name: String,
    val spec: String?,
    val unit: String?,
    val qty: BigDecimal,
    val price: BigDecimal,
    val taxRate: BigDecimal,
    val net: BigDecimal,
    val tax: BigDecimal,
    val amount: BigDecimal,
    val imageUrl: String?,
    val costPrice: BigDecimal,
)

private data class PreparedOrder(
    val customerId: String,
    val customerName: String,
    val ownerId: String?,
    val deptId: String?,
    val warehouseId: UUID,
    val lines: List<PreparedLine>,
    val totalQty: BigDecimal,
    val totalNet: BigDecimal,
    val totalTax: BigDecimal,
    val totalAmount: BigDecimal,
)

/**
 * 商城订单：价格、库存、授信、应收的权威计算全部在 ERP 侧完成。
 *
 * 状态机：
 * 待支付 --支付成功--> 待发货 --ERP出库--> 已发货 --确认收货--> 已完成
 * 账期单：待确认 --授信通过--> 待发货（不生成支付流水，生成应收）
 */
@Service
class MallOrderService(
    private val jdbc: JdbcTemplate,
    private val mapper: MallQueryMapper,
    private val priceService: PriceService,
    private val creditService: CreditService,
    private val inventoryService: InventoryService,
    private val noGenerator: NoGenerator,
    @Value("\${workeasy.mall.wx.mock:true}") private val wxMock: Boolean,
) {

    @Transactional(readOnly = true)
    fun preview(req: MallOrderPreviewRequest): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val prepared = prepare(user.customerId.toString(), req.items)
        val credit = creditService.check(user.customerId.toString(), prepared.totalAmount)
        val payType = req.payType?.takeIf { it in PAY_TYPES } ?: "wechat"
        val address = req.addressId?.takeIf { it.isNotBlank() }?.let { address(user.memberId, it) }
        return mapOf(
            "lines" to prepared.lines.map { it.toMap() },
            "totalQty" to prepared.totalQty,
            "totalNet" to prepared.totalNet,
            "totalTax" to prepared.totalTax,
            "totalAmount" to prepared.totalAmount,
            "warehouseId" to prepared.warehouseId.toString(),
            "credit" to credit.toMap(),
            "payType" to payType,
            "payOptions" to payOptions(),
            "address" to address,
            "minOrderAmount" to MallSupport.bd(MallSupport.config(jdbc, "min_order_amount", "0")),
        )
    }

    @Transactional
    fun create(req: MallOrderCreateRequest): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val payType = req.payType.trim().ifBlank { "wechat" }
        if (payType !in PAY_TYPES) throw BizException(ErrorCode.BIZ_PARAM, "不支持的支付方式：$payType")
        checkPayType(payType)
        if (req.addressId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "请选择收货地址")
        val address = address(user.memberId, req.addressId)

        val orderId = UUID.randomUUID()
        val orderNo = noGenerator.next("MALL")
        val prepared = prepare(user.customerId.toString(), req.items)
        val minAmount = MallSupport.bd(MallSupport.config(jdbc, "min_order_amount", "0"))
        if (minAmount > BigDecimal.ZERO && prepared.totalAmount < minAmount) {
            throw BizException(ErrorCode.BIZ_PARAM, "未达到起送金额 $minAmount")
        }

        val credit = creditService.check(user.customerId.toString(), prepared.totalAmount)
        if (payType == "credit" && credit.action == "block") {
            throw BizException(ErrorCode.BIZ_CONFLICT, credit.message ?: "超出客户授信额度")
        }

        val addressText = listOfNotNull(address["province"], address["city"], address["district"], address["detail"])
            .joinToString("") { it.toString() }

        // 账期超限 → 待确认（不锁库存、不建销售订单，由 ERP 后台经理确认放行）
        val pendingConfirm = payType == "credit" && credit.action == "approval"
        val status = when {
            pendingConfirm -> "pending_confirm"
            payType == "credit" -> "pending_ship"
            else -> "pending_pay"
        }
        val outTradeNo = if (payType == "wechat") "WX" + orderNo else null

        jdbc.update(
            """
            INSERT INTO mall_order(id, order_no, member_id, customer_id, customer_name, status, pay_type,
                                   total_qty, total_net, total_tax, total_amount, warehouse_id,
                                   receiver, receiver_phone, address, remark, out_trade_no, credit_over)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            orderId.toString(), orderNo, user.memberId.toString(), user.customerId.toString(),
            prepared.customerName, status, payType,
            prepared.totalQty, prepared.totalNet, prepared.totalTax, prepared.totalAmount,
            prepared.warehouseId.toString(), address["receiver"], address["phone"], addressText,
            req.remark?.take(500), outTradeNo, pendingConfirm,
        )
        prepared.lines.forEachIndexed { idx, l ->
            jdbc.update(
                """
                INSERT INTO mall_order_item(order_id, line_no, product_id, product_name, spec, unit, qty,
                                            price, tax_rate, net_amount, tax_amount, amount, image_url)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                orderId.toString(), idx + 1, l.productId, l.name, l.spec, l.unit, l.qty,
                l.price, l.taxRate, l.net, l.tax, l.amount, l.imageUrl,
            )
        }

        if (!pendingConfirm) {
            lockOrder(orderNo, prepared)
        }

        if (payType == "credit" && !pendingConfirm) {
            val so = createSalesOrder(orderId, orderNo, "credit", prepared)
            jdbc.update(
                "UPDATE mall_order SET sales_order_id = ?::uuid, status = 'pending_ship' WHERE id = ?::uuid",
                so.toString(), orderId.toString(),
            )
        } else if (payType != "credit") {
            jdbc.update(
                """
                INSERT INTO mall_payment(payment_no, order_id, member_id, out_trade_no, provider, amount, status)
                VALUES (?, ?::uuid, ?::uuid, ?, ?, ?, 'pending')
                """.trimIndent(),
                noGenerator.next("MPAY"), orderId.toString(), user.memberId.toString(),
                outTradeNo, if (payType == "offline") "offline" else "wechat", prepared.totalAmount,
            )
        }

        // 下单成功后清理购物车中已下单的商品
        prepared.lines.forEach {
            jdbc.update(
                "DELETE FROM mall_cart_item WHERE member_id = ?::uuid AND product_id = ?::uuid",
                user.memberId.toString(), it.productId,
            )
        }
        return detail(orderId.toString())
    }

    @Transactional(readOnly = true)
    fun list(status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        val (pg, pgSize) = Paging.clamp(page, size)
        val st = status?.trim()?.takeIf { it.isNotBlank() }
        val rows = mapper.orderList(user.memberId.toString(), st, (pg - 1) * pgSize, pgSize)
        return PageResult.of(rows, mapper.countOrders(user.memberId.toString(), st), pg, pgSize)
    }

    @Transactional(readOnly = true)
    fun detail(orderId: String): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val head = orderRow(user.memberId.toString(), orderId)
        val items = jdbc.queryForList(
            """
            SELECT id::text AS "id", line_no AS "lineNo", product_id::text AS "productId", product_name AS "productName",
                   spec, unit, qty, price, tax_rate AS "taxRate", net_amount AS "netAmount",
                   tax_amount AS "taxAmount", amount, image_url AS "imageUrl"
            FROM mall_order_item WHERE order_id = ?::uuid ORDER BY line_no
            """.trimIndent(),
            orderId,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val payments = jdbc.queryForList(
            """
            SELECT payment_no AS "paymentNo", out_trade_no AS "outTradeNo", provider, amount,
                   status, paid_at AS "paidAt", created_at AS "createdAt"
            FROM mall_payment WHERE order_id = ?::uuid ORDER BY created_at DESC
            """.trimIndent(),
            orderId,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val soStatus = head["salesOrderId"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            jdbc.queryForList("SELECT status FROM sales_order WHERE id = ?::uuid", String::class.java, it).firstOrNull()
        }
        return head + mapOf("items" to items, "payments" to payments, "salesOrderStatus" to soStatus)
    }

    /** 取消：待支付/待确认可取消；释放锁定，若已生成销售订单则作废并冲回应收 */
    @Transactional
    fun cancel(orderId: String) {
        val user = MallContext.requireCustomer()
        val head = jdbc.queryForMap(
            "SELECT * FROM mall_order WHERE id = ?::uuid AND member_id = ?::uuid FOR UPDATE",
            orderId, user.memberId.toString(),
        )
        val status = head["status"]?.toString()
        if (status !in setOf("pending_pay", "pending_confirm")) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "当前状态不可取消：$status")
        }
        releaseOrder(head, "会员取消")
        jdbc.update(
            "UPDATE mall_order SET status = 'cancelled', cancelled_at = now(), updated_at = now() WHERE id = ?::uuid",
            orderId,
        )
        jdbc.update(
            "UPDATE mall_payment SET status = 'failed', updated_at = now() WHERE order_id = ?::uuid AND status = 'pending'",
            orderId,
        )
    }

    /** 确认收货：已发货后才能完成 */
    @Transactional
    fun confirmReceipt(orderId: String) {
        val user = MallContext.requireCustomer()
        val n = jdbc.update(
            """
            UPDATE mall_order SET status = 'completed', completed_at = now(), updated_at = now()
            WHERE id = ?::uuid AND member_id = ?::uuid AND status = 'shipped'
            """.trimIndent(),
            orderId, user.memberId.toString(),
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_CONFLICT, "当前状态不可确认收货")
    }

    /**
     * 支付成功 / 线下打款确认：生成 sales_order（approved、channel=mall）+ 应收 + 核销。
     * 幂等：已有 sales_order_id 时只补状态。
     */
    fun finalizePaidOrder(orderId: String, payType: String, tx: Map<String, Any?>) {
        val head = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid FOR UPDATE", orderId)
        val status = head["status"]?.toString()
        if (status !in setOf("pending_pay", "pending_confirm")) {
            // 已处理（重复回调）直接返回
            return
        }
        val prepared = loadPrepared(orderId)
        val soId = head["sales_order_id"]?.toString()?.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }
            ?: createSalesOrder(UUID.fromString(orderId), head["order_no"].toString(), payType, prepared)

        val arId = jdbc.queryForList(
            "SELECT id, amount FROM ar_ledger WHERE biz_type = 'mall_order' AND biz_id = ?::uuid AND status <> 'closed'",
            soId.toString(),
        ).firstOrNull()
        if (arId != null && payType != "credit") {
            val amount = bd(arId["amount"])
            val paymentId = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO payment(id, payment_no, type, counterparty_id, counterparty_type, counterparty_name,
                                    amount, pay_method, status, pay_date, remark)
                VALUES (?::uuid, ?, 'receive', ?::uuid, 'customer', ?, ?, ?, 'done', CURRENT_DATE, ?)
                """.trimIndent(),
                paymentId.toString(), noGenerator.next("REC"),
                head["customer_id"].toString(), head["customer_name"]?.toString(),
                amount, if (payType == "wechat") "wechat" else "offline", "商城订单 ${head["order_no"]}",
            )
            jdbc.update(
                "INSERT INTO payment_writeoff(payment_id, ledger_type, ledger_id, amount) VALUES (?::uuid, 'ar', ?::uuid, ?)",
                paymentId.toString(), arId["id"].toString(), amount,
            )
            jdbc.update(
                "UPDATE ar_ledger SET settled_amount = ?, remain_amount = 0, status = 'closed' WHERE id = ?::uuid",
                amount, arId["id"].toString(),
            )
            jdbc.update(
                "UPDATE sales_order SET received = ? , updated_at = now() WHERE id = ?::uuid",
                amount, soId.toString(),
            )
        }

        jdbc.update(
            """
            UPDATE mall_order SET sales_order_id = ?::uuid, status = 'pending_ship',
                   paid_at = CASE WHEN ? = 'credit' THEN paid_at ELSE now() END, updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
            soId.toString(), payType, orderId,
        )
        // 支付流水回填交易号（wx 回调带 transaction_id）
        tx["transactionId"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            jdbc.update(
                "UPDATE mall_payment SET transaction_id = ?, updated_at = now() WHERE order_id = ?::uuid AND status = 'success'",
                it, orderId,
            )
        }
    }

    /** 账期待确认 → 经理确认放行：重新校验授信，锁库存并生成销售订单/应收 */
    @Transactional
    fun confirmCreditOrder(orderId: String) {
        val head = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid FOR UPDATE", orderId)
        if (head["status"]?.toString() != "pending_confirm") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "当前状态不可确认：${head["status"]}")
        }
        val prepared = loadPrepared(orderId)
        val chk = creditService.check(head["customer_id"].toString(), prepared.totalAmount)
        if (chk.action == "block") throw BizException(ErrorCode.BIZ_CONFLICT, chk.message ?: "超出客户授信额度")
        lockOrder(head["order_no"].toString(), prepared)
        val so = createSalesOrder(UUID.fromString(orderId), head["order_no"].toString(), "credit", prepared)
        jdbc.update(
            """
            UPDATE mall_order SET sales_order_id = ?::uuid, status = 'pending_ship',
                   credit_over = false, updated_at = now() WHERE id = ?::uuid
            """.trimIndent(),
            so.toString(), orderId,
        )
    }

    /** 后台关闭（待支付超时）：释放锁定并置 cancelled */
    @Transactional
    fun adminClose(orderId: String, status: String?) {
        val head = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid FOR UPDATE", orderId)
        val current = head["status"]?.toString()
        if (current !in setOf("pending_pay", "pending_confirm")) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "当前状态不可关闭：$current")
        }
        releaseOrder(head, "后台关闭")
        jdbc.update(
            "UPDATE mall_order SET status = 'closed', cancelled_at = now(), updated_at = now(), remark = COALESCE(?, remark) WHERE id = ?::uuid",
            status, orderId,
        )
        jdbc.update(
            "UPDATE mall_payment SET status = 'failed', updated_at = now() WHERE order_id = ?::uuid AND status = 'pending'",
            orderId,
        )
    }

    fun loadOrderRow(orderId: String): Map<String, Any?> =
        jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid", orderId)

    // ---------- 内部 ----------

    private fun prepare(customerId: String, items: List<MallOrderItemRequest>): PreparedOrder {
        if (items.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "订单明细不能为空")
        val merged = items.groupBy { it.productId }
            .map { (pid, rows) -> pid to rows.fold(BigDecimal.ZERO) { a, b -> a + b.qty } }
        if (merged.any { it.first.isBlank() }) throw BizException(ErrorCode.BIZ_PARAM, "商品不能为空")
        val warehouse = MallSupport.defaultWarehouse(jdbc)
        val customer = jdbc.queryForMap(
            "SELECT id, name, owner_id, dept_id FROM customer WHERE id = ?::uuid AND deleted_at IS NULL",
            customerId,
        )
        val lines = merged.map { (pid, qty) -> prepareLine(customerId, pid, qty, warehouse) }
        return PreparedOrder(
            customerId = customerId,
            customerName = customer["name"]?.toString().orEmpty(),
            ownerId = customer["owner_id"]?.toString()?.takeIf { it.isNotBlank() },
            deptId = customer["dept_id"]?.toString()?.takeIf { it.isNotBlank() },
            warehouseId = warehouse,
            lines = lines,
            totalQty = lines.fold(BigDecimal.ZERO) { a, l -> a + l.qty },
            totalNet = lines.fold(BigDecimal.ZERO) { a, l -> a + l.net },
            totalTax = lines.fold(BigDecimal.ZERO) { a, l -> a + l.tax },
            totalAmount = lines.fold(BigDecimal.ZERO) { a, l -> a + l.amount },
        )
    }

    private fun prepareLine(customerId: String, productId: String, qty: BigDecimal, warehouse: UUID): PreparedLine {
        MallSupport.validatePurchaseQty(jdbc, productId, qty)
        val p = jdbc.queryForList(
            """
            SELECT name, spec, unit, COALESCE(tax_rate,0) AS tax_rate,
                   (SELECT pi.url FROM product_image pi WHERE pi.product_id = product.id
                    ORDER BY is_primary DESC, sort_no LIMIT 1) AS image_url,
                   COALESCE((SELECT MAX(avg_cost) FROM inventory WHERE product_id = product.id), 0) AS cost_price
            FROM product WHERE id = ?::uuid
            """.trimIndent(),
            productId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "商品不存在")
        val price = priceFor(customerId, productId)
        if (price <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_CONFLICT, "商品 ${p["name"]} 未设置有效售价")
        val taxRate = MallSupport.bd(p["tax_rate"])
        val net = (price * qty).setScale(2, RoundingMode.HALF_UP)
        val tax = (net * taxRate).setScale(2, RoundingMode.HALF_UP)
        val available = MallSupport.available(jdbc, warehouse, productId)
        if (available < qty) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "商品 ${p["name"]} 可用库存不足：可用 $available，需 $qty")
        }
        return PreparedLine(
            productId = productId, name = p["name"].toString(), spec = p["spec"]?.toString(),
            unit = p["unit"]?.toString(), qty = qty, price = price, taxRate = taxRate,
            net = net, tax = tax, amount = net + tax, imageUrl = p["image_url"]?.toString(),
            costPrice = MallSupport.bd(p["cost_price"]),
        )
    }

    /** 客户身份取价：协议价 > 等级价 > 标准售价 */
    private fun priceFor(customerId: String, productId: String): BigDecimal {
        val q = priceService.quote(
            QuoteRequest(customerId = customerId, items = listOf(QuoteItemRequest(productId, BigDecimal.ONE)))
        ).firstOrNull()
        return MallSupport.bd(q?.get("price"))
    }

    private fun loadPrepared(orderId: String): PreparedOrder {
        val head = jdbc.queryForMap("SELECT * FROM mall_order WHERE id = ?::uuid", orderId)
        val warehouse = UUID.fromString(head["warehouse_id"].toString())
        val customerId = head["customer_id"].toString()
        val lines = jdbc.queryForList(
            "SELECT product_id::text AS pid, product_name, spec, unit, qty, price, tax_rate, net_amount, tax_amount, amount, image_url FROM mall_order_item WHERE order_id = ?::uuid ORDER BY line_no",
            orderId,
        ).map { row ->
            val pid = row["pid"].toString()
            PreparedLine(
                productId = pid, name = row["product_name"].toString(), spec = row["spec"]?.toString(),
                unit = row["unit"]?.toString(), qty = bd(row["qty"]), price = bd(row["price"]),
                taxRate = bd(row["tax_rate"]), net = bd(row["net_amount"]), tax = bd(row["tax_amount"]),
                amount = bd(row["amount"]), imageUrl = row["image_url"]?.toString(),
                costPrice = MallSupport.bd(
                    jdbc.queryForObject(
                        "SELECT COALESCE(MAX(avg_cost),0) FROM inventory WHERE product_id = ?::uuid",
                        BigDecimal::class.java, pid,
                    )
                ),
            )
        }
        return PreparedOrder(
            customerId = customerId,
            customerName = head["customer_name"]?.toString().orEmpty(),
            ownerId = jdbc.queryForList("SELECT owner_id FROM customer WHERE id = ?::uuid", customerId)
                .firstOrNull()?.get("owner_id")?.toString()?.takeIf { it.isNotBlank() },
            deptId = jdbc.queryForList("SELECT dept_id FROM customer WHERE id = ?::uuid", customerId)
                .firstOrNull()?.get("dept_id")?.toString()?.takeIf { it.isNotBlank() },
            warehouseId = warehouse,
            lines = lines,
            totalQty = bd(head["total_qty"]),
            totalNet = bd(head["total_net"]),
            totalTax = bd(head["total_tax"]),
            totalAmount = bd(head["total_amount"]),
        )
    }

    private fun lockOrder(orderNo: String, prepared: PreparedOrder) {
        prepared.lines.forEach { l ->
            inventoryService.lock(
                StockLockRequest(
                    productId = l.productId,
                    warehouseId = prepared.warehouseId.toString(),
                    qty = l.qty,
                    refType = "mall_order",
                    refNo = orderNo,
                    remark = "商城下单锁定",
                )
            )
        }
    }

    private fun createSalesOrder(orderId: UUID, mallOrderNo: String, payType: String, prepared: PreparedOrder): UUID {
        val soId = UUID.randomUUID()
        val soNo = noGenerator.next("SO")
        val terms = queryPaymentTerms(prepared.customerId)
        val totalNet = prepared.totalNet
        val totalCost = prepared.lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.costPrice * l.qty }
        val profit = totalNet - totalCost
        val rate = if (totalNet > BigDecimal.ZERO) profit.divide(totalNet, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO

        jdbc.update(
            """
            INSERT INTO sales_order(id, order_no, customer_id, customer_name, dept_id, owner_id,
                                    order_date, credit_days, due_date, status, channel, mall_order_id,
                                    total_qty, total_net, total_tax, total_amount, total_cost,
                                    total_profit, profit_rate, receivable, received, remark)
            VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid, ?::uuid, CURRENT_DATE, ?, CURRENT_DATE + ?, 'approved', 'mall', ?::uuid,
                    ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            """.trimIndent(),
            soId.toString(), soNo, prepared.customerId, prepared.customerName,
            prepared.deptId, prepared.ownerId, terms, terms, orderId.toString(),
            prepared.totalQty, totalNet, prepared.totalTax, prepared.totalAmount, totalCost,
            profit, rate, prepared.totalAmount, "商城订单 $mallOrderNo",
        )

        prepared.lines.forEachIndexed { idx, l ->
            val costAmount = (l.costPrice * l.qty).setScale(2, RoundingMode.HALF_UP)
            val lineProfit = l.net - costAmount
            jdbc.update(
                """
                INSERT INTO sales_order_item(order_id, line_no, product_id, product_name, spec, unit,
                                             qty, price, tax_rate, tax_amount, net_amount, amount,
                                             cost_price, cost_amount, profit, profit_rate,
                                             shipped_qty, unship_qty, warehouse_id)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?::uuid)
                """.trimIndent(),
                soId.toString(), idx + 1, l.productId, l.name, l.spec, l.unit, l.qty, l.price,
                l.taxRate, l.tax, l.net, l.amount, l.costPrice, costAmount, lineProfit,
                if (l.net > BigDecimal.ZERO) lineProfit.divide(l.net, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO,
                l.qty, prepared.warehouseId.toString(),
            )
        }

        jdbc.update(
            """
            INSERT INTO ar_ledger(customer_id, biz_type, biz_id, biz_no, amount, remain_amount, due_date, status)
            VALUES (?::uuid, 'mall_order', ?::uuid, ?, ?, ?, CURRENT_DATE + ?, 'open')
            """.trimIndent(),
            prepared.customerId, soId.toString(), soNo, prepared.totalAmount, prepared.totalAmount, terms,
        )
        if (payType == "credit") {
            jdbc.update(
                "UPDATE customer SET credit_used = COALESCE(credit_used,0) + ? WHERE id = ?::uuid",
                prepared.totalAmount, prepared.customerId,
            )
        }
        return soId
    }

    private fun queryPaymentTerms(customerId: String): Int =
        (jdbc.queryForList("SELECT payment_terms FROM customer WHERE id = ?::uuid", customerId)
            .firstOrNull()?.get("payment_terms") as? Number)?.toInt() ?: 0

    private fun orderRow(memberId: String, orderId: String): Map<String, Any?> =
        jdbc.queryForList(
            """
            SELECT id::text AS "id", order_no AS "orderNo", status, pay_type AS "payType",
                   total_qty AS "totalQty", total_net AS "totalNet", total_tax AS "totalTax",
                   total_amount AS "totalAmount", receiver, receiver_phone AS "receiverPhone",
                   address, remark, sales_order_id::text AS "salesOrderId", out_trade_no AS "outTradeNo",
                   credit_over AS "creditOver", paid_at AS "paidAt", shipped_at AS "shippedAt",
                   completed_at AS "completedAt", created_at AS "createdAt"
            FROM mall_order WHERE id = ?::uuid AND member_id = ?::uuid
            """.trimIndent(),
            orderId, memberId,
        ).firstOrNull()?.mapValues { (_, v) -> v?.toString() }
            ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "订单不存在")

    private fun address(memberId: UUID, addressId: String): Map<String, Any?> =
        jdbc.queryForList(
            """
            SELECT receiver, phone, province, city, district, detail
            FROM mall_address WHERE id = ?::uuid AND member_id = ?::uuid AND deleted_at IS NULL
            """.trimIndent(),
            addressId, memberId.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "收货地址不存在")

    private fun releaseOrder(head: Map<String, Any?>, reason: String) {
        val orderId = head["id"].toString()
        val orderNo = head["order_no"]?.toString().orEmpty()
        val warehouseId = head["warehouse_id"]?.toString().orEmpty()
        if (warehouseId.isNotBlank()) {
            jdbc.queryForList(
                "SELECT product_id, qty FROM mall_order_item WHERE order_id = ?::uuid",
                orderId,
            ).forEach { row ->
                inventoryService.release(
                    StockLockRequest(
                        productId = row["product_id"].toString(),
                        warehouseId = warehouseId,
                        qty = MallSupport.bd(row["qty"]),
                        refType = "mall_order",
                        refNo = orderNo,
                        remark = reason,
                    )
                )
            }
        }
        val soId = head["sales_order_id"]?.toString()?.takeIf { it.isNotBlank() } ?: return
        jdbc.update(
            "UPDATE sales_order SET status = 'void', updated_at = now() WHERE id = ?::uuid AND status = 'approved'",
            soId,
        )
        jdbc.queryForList(
            "SELECT id, amount FROM ar_ledger WHERE biz_type = 'mall_order' AND biz_id = ?::uuid AND status <> 'closed'",
            soId,
        ).forEach { ar ->
            jdbc.update("UPDATE ar_ledger SET remain_amount = 0, status = 'closed' WHERE id = ?::uuid", ar["id"].toString())
            jdbc.update(
                "UPDATE customer SET credit_used = GREATEST(COALESCE(credit_used,0) - ?, 0) WHERE id = ?::uuid",
                bd(ar["amount"]), head["customer_id"].toString(),
            )
        }
    }

    private fun payOptions(): List<Map<String, Any?>> {
        val options = mutableListOf<Map<String, Any?>>()
        val wxEnabled = wxMock || MallSupport.config(jdbc, "wechat_enabled", "false") == "true"
        if (wxEnabled) options += mapOf("type" to "wechat", "label" to "微信支付", "enabled" to true)
        if (MallSupport.config(jdbc, "credit_enabled", "true") == "true") {
            options += mapOf("type" to "credit", "label" to "账期支付", "enabled" to true)
        }
        options += mapOf("type" to "offline", "label" to "线下打款", "enabled" to true)
        return options
    }

    private fun checkPayType(payType: String) {
        val option = payOptions().firstOrNull { it["type"] == payType }
        if (option == null || option["enabled"] != true) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "当前商户未开通该支付方式：$payType")
        }
    }

    private fun PreparedLine.toMap(): Map<String, Any?> = mapOf(
        "productId" to productId,
        "name" to name,
        "spec" to spec,
        "unit" to unit,
        "qty" to qty,
        "price" to price,
        "taxRate" to taxRate,
        "netAmount" to net,
        "taxAmount" to tax,
        "amount" to amount,
        "imageUrl" to imageUrl,
    )

    private fun bd(v: Any?): BigDecimal = MallSupport.bd(v)

    companion object {
        private val PAY_TYPES = setOf("wechat", "credit", "offline")
    }
}
