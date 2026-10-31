package com.workeasy.erp.mall

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.NoGenerator
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.common.Paging
import com.workeasy.erp.config.PlatformDb
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.tenant.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class MallBannerRequest(
    val title: String? = null,
    val imageUrl: String = "",
    val linkUrl: String? = null,
    val sortNo: Int? = null,
    val status: String? = null,
)

data class MallInviteRequest(
    val customerId: String = "",
    val ownerId: String? = null,
    val remark: String? = null,
)

data class MallInviteStatusRequest(val status: String = "active")

data class MallBindingApproveRequest(
    val customerId: String? = null,
    val remark: String? = null,
)

data class MallRemarkRequest(val remark: String? = null)

/**
 * 商城后台（员工侧）：
 * - 商城设置 / 轮播 / 邀请码 / 会员绑定审核落租户库，绑定关系主体仍在平台库
 * - 订单、支付流水、售后复用会员端服务，避免双份状态机
 */
@Service
class MallAdminService(
    private val jdbc: JdbcTemplate,
    private val platformDb: PlatformDb,
    private val noGenerator: NoGenerator,
    private val orderService: MallOrderService,
    private val payService: MallPayService,
    private val afterSales: MallAfterSalesService,
    private val audit: AuditService,
) {

    // ---------- 商城设置 ----------

    @Transactional(readOnly = true)
    fun settings(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT key, value, remark, updated_at AS "updatedAt" FROM mall_config ORDER BY key
        """.trimIndent(),
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional
    fun updateSettings(body: Map<String, String?>) {
        if (body.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "设置项不能为空")
        val invalid = body.keys - SETTING_KEYS
        if (invalid.isNotEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "不支持的设置项：${invalid.joinToString(",")}")
        body.forEach { (key, value) ->
            jdbc.update(
                """
                INSERT INTO mall_config(key, value) VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """.trimIndent(),
                key, value.orEmpty(),
            )
        }
        audit.log("mall", "更新商城设置 ${body.keys.joinToString(",")}", "mall_config", null)
    }

    // ---------- Banner ----------

    @Transactional(readOnly = true)
    fun banners(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT id::text AS "id", title, image_url AS "imageUrl", link_url AS "linkUrl",
               sort_no AS "sortNo", status, created_at AS "createdAt"
        FROM mall_banner ORDER BY sort_no, created_at DESC
        """.trimIndent(),
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional
    fun createBanner(req: MallBannerRequest): Map<String, Any?> {
        if (req.imageUrl.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "图片地址不能为空")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO mall_banner(id, title, image_url, link_url, sort_no, status)
            VALUES (?::uuid, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id.toString(), req.title?.take(100), req.imageUrl.take(500), req.linkUrl?.take(500),
            req.sortNo ?: 0, validBannerStatus(req.status),
        )
        audit.log("mall", "新增商城轮播 ${req.title ?: id}", "mall_banner", id.toString())
        return bannerRow(id.toString())
    }

    @Transactional
    fun updateBanner(id: String, req: MallBannerRequest): Map<String, Any?> {
        requireBanner(id)
        jdbc.update(
            """
            UPDATE mall_banner SET title = COALESCE(?, title), image_url = COALESCE(?, image_url),
                   link_url = COALESCE(?, link_url), sort_no = COALESCE(?, sort_no),
                   status = COALESCE(?, status)
            WHERE id = ?::uuid
            """.trimIndent(),
            req.title?.take(100), req.imageUrl.takeIf { it.isNotBlank() }?.take(500), req.linkUrl?.take(500),
            req.sortNo, req.status?.takeIf { it in setOf("active", "inactive") }, id,
        )
        return bannerRow(id)
    }

    @Transactional
    fun deleteBanner(id: String) {
        requireBanner(id)
        jdbc.update("DELETE FROM mall_banner WHERE id = ?::uuid", id)
        audit.log("mall", "删除商城轮播 $id", "mall_banner", id)
    }

    // ---------- 邀请码 ----------

    @Transactional(readOnly = true)
    fun invites(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT i.id::text AS "id", i.code, i.customer_id::text AS "customerId", c.name AS "customerName",
               i.owner_id::text AS "ownerId", e.name AS "ownerName", i.status, i.remark,
               i.created_at AS "createdAt"
        FROM mall_invite i
        LEFT JOIN customer c ON c.id = i.customer_id
        LEFT JOIN employee e ON e.id = i.owner_id
        ORDER BY i.created_at DESC
        """.trimIndent(),
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional
    fun createInvite(req: MallInviteRequest): Map<String, Any?> {
        if (req.customerId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "客户不能为空")
        val customer = jdbc.queryForList(
            "SELECT id, name FROM customer WHERE id = ?::uuid AND deleted_at IS NULL",
            req.customerId,
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "客户不存在")
        val me = UserContext.getOrNull()
        val ownerId = req.ownerId?.takeIf { it.isNotBlank() } ?: me?.employeeId?.toString()
        val id = UUID.randomUUID()
        val code = noGenerator.next("INV")
        jdbc.update(
            """
            INSERT INTO mall_invite(id, code, customer_id, owner_id, status, remark)
            VALUES (?::uuid, ?, ?::uuid, ?::uuid, 'active', ?)
            """.trimIndent(),
            id.toString(), code, req.customerId, ownerId, req.remark?.take(200),
        )
        audit.log("mall", "生成商城邀请码 $code（${customer["name"]}）", "mall_invite", id.toString())
        return mapOf(
            "id" to id.toString(),
            "code" to code,
            "customerId" to req.customerId,
            "customerName" to customer["name"]?.toString(),
            "status" to "active",
        )
    }

    @Transactional
    fun setInviteStatus(id: String, status: String) {
        if (status !in setOf("active", "disabled")) throw BizException(ErrorCode.BIZ_PARAM, "邀请码状态非法")
        val n = jdbc.update("UPDATE mall_invite SET status = ? WHERE id = ?::uuid", status, id)
        if (n == 0) throw BizException(ErrorCode.BIZ_NOT_FOUND, "邀请码不存在")
    }

    // ---------- 会员绑定审核（平台库） ----------

    @Transactional(readOnly = true)
    fun bindings(status: String?): List<Map<String, Any?>> {
        val tenant = TenantContext.require()
        val st = status?.trim().orEmpty()
        val rows = platformDb.jdbc.queryForList(
            """
            SELECT b.id::text AS "id", b.member_id::text AS "memberId", b.customer_id::text AS "customerId",
                   b.status, b.invite_code AS "inviteCode", b.remark, b.created_at AS "createdAt",
                   m.nickname, m.phone
            FROM member_tenant_binding b
            JOIN platform_member m ON m.id = b.member_id
            WHERE b.tenant_id = ?::uuid AND (? = '' OR b.status = ?)
            ORDER BY b.created_at DESC
            """.trimIndent(),
            tenant.id.toString(), st, st,
        ).map { it.mapValues { (_, v) -> v?.toString() } }

        val customerIds = rows.mapNotNull { it["customerId"] }.filter { it.isNotBlank() }
        val names = if (customerIds.isEmpty()) emptyMap() else jdbc.queryForList(
            "SELECT id::text AS id, name FROM customer WHERE id::text = ANY(string_to_array(?, ','))",
            customerIds.joinToString(","),
        ).associate { it["id"].toString() to it["name"]?.toString() }
        return rows.map { it + mapOf("customerName" to names[it["customerId"]]) }
    }

    @Transactional
    fun approveBinding(id: String, req: MallBindingApproveRequest): Map<String, Any?> {
        val tenant = TenantContext.require()
        val binding = platformDb.jdbc.queryForList(
            "SELECT * FROM member_tenant_binding WHERE id = ?::uuid AND tenant_id = ?::uuid FOR UPDATE",
            id, tenant.id.toString(),
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "绑定申请不存在")
        if (binding["status"]?.toString() == "active") {
            throw BizException(ErrorCode.BIZ_CONFLICT, "该会员已绑定")
        }
        val cid = req.customerId?.takeIf { it.isNotBlank() }
            ?: binding["customer_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: resolveInviteCustomer(binding["invite_code"]?.toString())
        if (cid == null) throw BizException(ErrorCode.BIZ_PARAM, "请指定要绑定的客户档案")
        jdbc.queryForList(
            "SELECT id FROM customer WHERE id = ?::uuid AND deleted_at IS NULL", cid
        ).firstOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "客户档案不存在")

        platformDb.jdbc.update(
            """
            UPDATE member_tenant_binding SET status = 'active', customer_id = ?::uuid, remark = ?, updated_at = now()
            WHERE id = ?::uuid AND tenant_id = ?::uuid
            """.trimIndent(),
            cid, req.remark?.take(500), id, tenant.id.toString(),
        )
        audit.log("mall", "审核通过会员绑定 $id → 客户 $cid", "member_tenant_binding", id)
        return mapOf("id" to id, "status" to "active", "customerId" to cid)
    }

    @Transactional
    fun rejectBinding(id: String, remark: String?) {
        val tenant = TenantContext.require()
        val n = platformDb.jdbc.update(
            """
            UPDATE member_tenant_binding SET status = 'disabled', remark = ?, updated_at = now()
            WHERE id = ?::uuid AND tenant_id = ?::uuid AND status = 'pending'
            """.trimIndent(),
            remark?.take(500), id, tenant.id.toString(),
        )
        if (n == 0) throw BizException(ErrorCode.BIZ_CONFLICT, "仅待审核的绑定可驳回")
    }

    // ---------- 商城订单 ----------

    @Transactional(readOnly = true)
    fun orders(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val (pg, pgSize) = Paging.clamp(page, size)
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (o.order_no ILIKE ? OR o.customer_name ILIKE ? OR o.receiver ILIKE ?) ")
            args.add("%$q%"); args.add("%$q%"); args.add("%$q%")
        }
        val st = status?.trim()?.takeIf { it.isNotBlank() }
        if (st != null) {
            cond.append("AND o.status = ? ")
            args.add(st)
        }
        val total = jdbc.queryForObject("SELECT count(*) FROM mall_order o $cond", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT o.id::text AS "id", o.order_no AS "orderNo", o.customer_name AS "customerName",
                   o.status, o.pay_type AS "payType", o.total_qty AS "totalQty",
                   o.total_amount AS "totalAmount", o.sales_order_id::text AS "salesOrderId",
                   o.credit_over AS "creditOver", o.paid_at AS "paidAt", o.shipped_at AS "shippedAt",
                   o.created_at AS "createdAt"
            FROM mall_order o $cond
            ORDER BY o.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        return PageResult.of(rows, total, pg, pgSize)
    }

    @Transactional(readOnly = true)
    fun orderDetail(id: String): Map<String, Any?> {
        val head = orderService.loadOrderRow(id)
        val member = platformDb.jdbc.queryForList(
            "SELECT nickname, phone FROM platform_member WHERE id = ?::uuid", head["member_id"].toString()
        ).firstOrNull().orEmpty()
        val items = jdbc.queryForList(
            """
            SELECT line_no AS "lineNo", product_name AS "productName", spec, unit, qty, price,
                   tax_rate AS "taxRate", net_amount AS "netAmount", tax_amount AS "taxAmount", amount,
                   image_url AS "imageUrl"
            FROM mall_order_item WHERE order_id = ?::uuid ORDER BY line_no
            """.trimIndent(),
            id,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val payments = jdbc.queryForList(
            """
            SELECT payment_no AS "paymentNo", out_trade_no AS "outTradeNo", provider, amount,
                   status, paid_at AS "paidAt"
            FROM mall_payment WHERE order_id = ?::uuid ORDER BY created_at DESC
            """.trimIndent(),
            id,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val refunds = jdbc.queryForList(
            """
            SELECT id::text AS "id", refund_no AS "refundNo", amount, reason, status, remark,
                   created_at AS "createdAt"
            FROM mall_refund WHERE order_id = ?::uuid ORDER BY created_at DESC
            """.trimIndent(),
            id,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        return head.mapValues { (_, v) -> v?.toString() } + mapOf(
            "member" to member.mapValues { (_, v) -> v?.toString() },
            "items" to items,
            "payments" to payments,
            "refunds" to refunds,
        )
    }

    fun closeOrder(id: String, remark: String?) = orderService.adminClose(id, remark)

    fun confirmCredit(id: String) = orderService.confirmCreditOrder(id)

    fun confirmOffline(id: String) = payService.confirmOffline(id)

    // ---------- 支付流水 ----------

    @Transactional(readOnly = true)
    fun payments(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val (pg, pgSize) = Paging.clamp(page, size)
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (p.out_trade_no ILIKE ? OR o.order_no ILIKE ? OR p.transaction_id ILIKE ?) ")
            args.add("%$q%"); args.add("%$q%"); args.add("%$q%")
        }
        status?.trim()?.takeIf { it.isNotBlank() }?.let {
            cond.append("AND p.status = ? "); args.add(it)
        }
        val from = "FROM mall_payment p JOIN mall_order o ON o.id = p.order_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT p.id::text AS "id", p.payment_no AS "paymentNo", p.out_trade_no AS "outTradeNo",
                   o.order_no AS "orderNo", o.customer_name AS "customerName", p.provider,
                   p.amount, p.status, p.transaction_id AS "transactionId", p.paid_at AS "paidAt",
                   p.created_at AS "createdAt"
            $from
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        return PageResult.of(rows, total, pg, pgSize)
    }

    // ---------- 售后 ----------

    @Transactional(readOnly = true)
    fun afterSales(status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val (pg, pgSize) = Paging.clamp(page, size)
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        status?.trim()?.takeIf { it.isNotBlank() }?.let {
            cond.append("AND r.status = ? "); args.add(it)
        }
        val from = "FROM mall_refund r JOIN mall_order o ON o.id = r.order_id $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT r.id::text AS "id", r.refund_no AS "refundNo", o.order_no AS "orderNo",
                   o.customer_name AS "customerName", r.amount, r.reason, r.status,
                   r.sales_return_id::text AS "salesReturnId", r.remark, r.created_at AS "createdAt"
            $from
            ORDER BY r.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        return PageResult.of(rows, total, pg, pgSize)
    }

    fun approveRefund(id: String, remark: String?) = afterSales.approve(id, remark)

    fun rejectRefund(id: String, remark: String?) = afterSales.reject(id, remark)

    // ---------- 概览 ----------

    @Transactional(readOnly = true)
    fun overview(): Map<String, Any?> {
        val tenant = TenantContext.require()
        val statusRows = jdbc.queryForList(
            "SELECT status, COUNT(*) AS cnt, COALESCE(SUM(total_amount),0) AS amount FROM mall_order GROUP BY status"
        )
        val byStatus = statusRows.associate { it["status"].toString() to it["cnt"] }
        val today = jdbc.queryForMap(
            """
            SELECT COUNT(*) AS cnt, COALESCE(SUM(total_amount),0) AS amount
            FROM mall_order WHERE created_at::date = CURRENT_DATE
            """.trimIndent(),
        )
        val pendingBindings = platformDb.jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_tenant_binding WHERE tenant_id = ?::uuid AND status = 'pending'",
            Long::class.java, tenant.id.toString(),
        ) ?: 0
        val pendingRefunds = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mall_refund WHERE status = 'applied'", Long::class.java
        ) ?: 0
        return mapOf(
            "statusCounts" to byStatus,
            "todayOrders" to (today["cnt"] as? Number)?.toLong(),
            "todayAmount" to MallSupport.bd(today["amount"]),
            "pendingBindings" to pendingBindings,
            "pendingRefunds" to pendingRefunds,
        )
    }

    // ---------- 内部 ----------

    private fun bannerRow(id: String): Map<String, Any?> = jdbc.queryForList(
        """
        SELECT id::text AS "id", title, image_url AS "imageUrl", link_url AS "linkUrl",
               sort_no AS "sortNo", status
        FROM mall_banner WHERE id = ?::uuid
        """.trimIndent(),
        id,
    ).firstOrNull()?.mapValues { (_, v) -> v?.toString() }
        ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "轮播不存在")

    private fun requireBanner(id: String) {
        val n = jdbc.queryForObject("SELECT count(*) FROM mall_banner WHERE id = ?::uuid", Long::class.java, id) ?: 0
        if (n == 0L) throw BizException(ErrorCode.BIZ_NOT_FOUND, "轮播不存在")
    }

    private fun validBannerStatus(status: String?): String =
        status?.takeIf { it in setOf("active", "inactive") } ?: "active"

    private fun resolveInviteCustomer(inviteCode: String?): String? {
        val code = inviteCode?.takeIf { it.isNotBlank() } ?: return null
        return jdbc.queryForList(
            "SELECT customer_id::text AS cid FROM mall_invite WHERE code = ? AND status = 'active'", code
        ).firstOrNull()?.get("cid")?.toString()
    }

    private companion object {
        val SETTING_KEYS = setOf(
            "default_warehouse_id", "notice", "service_phone", "credit_enabled", "wechat_enabled",
            "auto_confirm_days", "bind_auto_approve", "min_order_amount",
        )
    }
}
