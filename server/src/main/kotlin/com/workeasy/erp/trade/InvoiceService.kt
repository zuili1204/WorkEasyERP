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

data class InvoiceCreateRequest(
    val type: String = "sales",              // sales 销项 / purchase 进项
    val invoiceType: String? = null,
    val partyName: String = "",
    val partyTaxNo: String? = null,
    val amount: BigDecimal = BigDecimal.ZERO, // 价税合计
    val taxRate: BigDecimal = BigDecimal("0.13"),
    val bizType: String? = null,
    val bizId: String? = null,
)

/** 发票（进/销项）与对账 */
@Service
class InvoiceService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(type: String?, q: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!type.isNullOrBlank()) {
            cond.append("AND type = ? ")
            args.add(type)
        }
        if (!q.isNullOrBlank()) {
            cond.append("AND (invoice_no ILIKE ? OR buyer_name ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM invoice $cond", Long::class.java, *args.toTypedArray()
        ) ?: 0
        val rows = jdbc.queryForList(
            "SELECT * FROM invoice $cond ORDER BY created_at DESC LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    /** 登记发票：按价税合计与税率反算不含税金额与税额 */
    @Transactional
    fun create(req: InvoiceCreateRequest): Map<String, Any?> {
        if (req.partyName.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "往来方名称不能为空")
        if (req.amount <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "金额必须大于 0")

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val no = noGenerator.next("INV")
        val net = req.amount.divide(BigDecimal.ONE + req.taxRate, 2, RoundingMode.HALF_UP)
        val tax = req.amount - net

        jdbc.update(
            """
            INSERT INTO invoice(id, invoice_no, type, invoice_type, buyer_name, buyer_tax_no,
                                amount, net_amount, tax_amount, tax_rate, status, biz_type, biz_id)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'issued', ?, ?::uuid)
            """.trimIndent(),
            id.toString(), no, req.type, req.invoiceType, req.partyName, req.partyTaxNo,
            req.amount, net, tax, req.taxRate, req.bizType, req.bizId,
        )
        audit.log("finance", "登记${if (req.type == "sales") "销项" else "进项"}发票 $no", "invoice", id.toString())
        return mapOf("id" to id.toString(), "invoiceNo" to no, "netAmount" to net.toString(), "taxAmount" to tax.toString())
    }

    /**
     * 对账：按供应商汇总「订单额 / 入库额 / 应付 / 已付 / 差异」
     */
    @Transactional(readOnly = true)
    fun purchaseRecon(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT s.name AS supplier_name,
               COALESCE((SELECT SUM(total_amount) FROM purchase_order WHERE supplier_id = s.id AND deleted_at IS NULL), 0) AS order_amount,
               COALESCE((SELECT SUM(total_amount) FROM purchase_inbound WHERE supplier_id = s.id), 0) AS inbound_amount,
               COALESCE((SELECT SUM(amount) FROM ap_ledger WHERE supplier_id = s.id), 0) AS ap_amount,
               COALESCE((SELECT SUM(settled_amount) FROM ap_ledger WHERE supplier_id = s.id), 0) AS paid_amount,
               COALESCE((SELECT SUM(remain_amount) FROM ap_ledger WHERE supplier_id = s.id), 0) AS ap_remain
        FROM supplier s
        WHERE s.deleted_at IS NULL
        ORDER BY ap_remain DESC
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    /** 对账：按客户汇总「订单额 / 出库额 / 应收 / 已收 / 余额」 */
    @Transactional(readOnly = true)
    fun salesRecon(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT c.name AS customer_name,
               COALESCE((SELECT SUM(total_amount) FROM sales_order WHERE customer_id = c.id AND deleted_at IS NULL), 0) AS order_amount,
               COALESCE((SELECT SUM(total_amount) FROM sales_outbound WHERE customer_id = c.id), 0) AS outbound_amount,
               COALESCE((SELECT SUM(amount) FROM ar_ledger WHERE customer_id = c.id), 0) AS ar_amount,
               COALESCE((SELECT SUM(settled_amount) FROM ar_ledger WHERE customer_id = c.id), 0) AS received_amount,
               COALESCE((SELECT SUM(remain_amount) FROM ar_ledger WHERE customer_id = c.id), 0) AS ar_remain,
               COALESCE(c.credit_limit, 0) AS credit_limit
        FROM customer c
        WHERE c.deleted_at IS NULL
        ORDER BY ar_remain DESC
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }
}
