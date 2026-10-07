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
import java.util.UUID

data class PaymentRequest(
    val type: String = "receive",        // receive 收款 / pay 付款
    val counterpartyId: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val payMethod: String? = null,
    val remark: String? = null,
)

/**
 * 财务：应收 / 应付台账 + 收付款核销。
 * 核销规则：按到期日早优先，逐笔冲减 `remain_amount`，冲完置 closed；一笔款可冲多单。
 */
@Service
class FinanceService(
    private val jdbc: JdbcTemplate,
    private val noGenerator: NoGenerator,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun arList(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        ledgerList("ar", q, status, page, size)

    @Transactional(readOnly = true)
    fun apList(q: String?, status: String?, page: Int, size: Int): PageResult<Map<String, Any?>> =
        ledgerList("ap", q, status, page, size)

    @Transactional(readOnly = true)
    fun payments(page: Int, size: Int): PageResult<Map<String, Any?>> {
        val total = jdbc.queryForObject("SELECT count(*) FROM payment", Long::class.java) ?: 0
        val rows = jdbc.queryForList(
            "SELECT * FROM payment ORDER BY created_at DESC LIMIT ? OFFSET ?",
            size,
            (page - 1) * size,
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    @Transactional
    fun pay(req: PaymentRequest) {
        val me = UserContext.get()
        if (req.counterpartyId.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "缺少往来单位")
        if (req.amount <= BigDecimal.ZERO) throw BizException(ErrorCode.BIZ_PARAM, "金额必须大于 0")

        val isAr = req.type == "receive"
        val ledgerType = if (isAr) "ar" else "ap"
        val table = if (isAr) "ar_ledger" else "ap_ledger"
        val partyCol = if (isAr) "customer_id" else "supplier_id"
        val partyTable = if (isAr) "customer" else "supplier"
        val partyName = jdbc.queryForObject(
            "SELECT name FROM $partyTable WHERE id = ?::uuid", String::class.java, req.counterpartyId
        )

        val pid = UUID.randomUUID()
        val no = noGenerator.next("PAY")
        jdbc.update(
            """
            INSERT INTO payment(id, payment_no, type, counterparty_id, counterparty_type, counterparty_name,
                                amount, pay_method, status, operator_id, remark)
            VALUES (?::uuid, ?, ?, ?::uuid, ?, ?, ?, ?, 'done', ?::uuid, ?)
            """.trimIndent(),
            pid.toString(), no, req.type, req.counterpartyId,
            if (isAr) "customer" else "supplier", partyName,
            req.amount, req.payMethod, me.userId.toString(), req.remark,
        )

        var remain = req.amount
        val rows = jdbc.queryForList(
            """
            SELECT id, amount, settled_amount, remain_amount
            FROM $table
            WHERE $partyCol = ?::uuid AND status <> 'closed'
            ORDER BY due_date NULLS LAST, created_at
            """.trimIndent(),
            req.counterpartyId,
        )
        rows.forEach { row ->
            if (remain <= BigDecimal.ZERO) return@forEach
            val ledgerRemain = bd(row["remain_amount"])
            if (ledgerRemain <= BigDecimal.ZERO) return@forEach
            val use = if (remain <= ledgerRemain) remain else ledgerRemain

            jdbc.update(
                """
                INSERT INTO payment_writeoff(payment_id, ledger_type, ledger_id, amount, operator_id)
                VALUES (?::uuid, ?, ?::uuid, ?, ?::uuid)
                """.trimIndent(),
                pid.toString(), ledgerType, row["id"].toString(), use, me.userId.toString(),
            )
            val settled = bd(row["settled_amount"]) + use
            val newRemain = ledgerRemain - use
            jdbc.update(
                """
                UPDATE $table SET settled_amount = ?, remain_amount = ?,
                       status = CASE WHEN ? <= 0 THEN 'closed' ELSE 'partial' END
                WHERE id = ?::uuid
                """.trimIndent(),
                settled, newRemain, newRemain, row["id"].toString(),
            )
            remain -= use
        }

        // 收款释放客户授信
        if (isAr) {
            jdbc.update(
                "UPDATE customer SET credit_used = GREATEST(COALESCE(credit_used,0) - ?, 0) WHERE id = ?::uuid",
                req.amount - remain,
                req.counterpartyId,
            )
        }
        audit.log(
            "finance",
            "${if (isAr) "收款" else "付款"} $no 核销 ${(req.amount - remain)}",
            "payment",
            pid.toString(),
        )
    }

    private fun ledgerList(
        type: String,
        q: String?,
        status: String?,
        page: Int,
        size: Int,
    ): PageResult<Map<String, Any?>> {
        val isAr = type == "ar"
        val table = if (isAr) "ar_ledger" else "ap_ledger"
        val partyJoin = if (isAr) {
            "LEFT JOIN customer c ON c.id = l.customer_id"
        } else {
            "LEFT JOIN supplier s ON s.id = l.supplier_id"
        }
        val partyName = if (isAr) "c.name" else "s.name"

        val args = mutableListOf<Any>()
        val cond = StringBuilder("WHERE 1=1 ")
        if (!q.isNullOrBlank()) {
            cond.append("AND (l.biz_no ILIKE ? OR $partyName ILIKE ?) ")
            args.add("%$q%")
            args.add("%$q%")
        }
        if (!status.isNullOrBlank()) {
            cond.append("AND l.status = ? ")
            args.add(status)
        }
        val from = "FROM $table l $partyJoin $cond"
        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT l.id, l.biz_type, l.biz_no, $partyName AS party_name,
                   ${if (isAr) "l.customer_id" else "l.supplier_id"} AS party_id,
                   l.amount, l.settled_amount, l.remain_amount, l.due_date, l.status, l.created_at
            $from
            ORDER BY l.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }
}
