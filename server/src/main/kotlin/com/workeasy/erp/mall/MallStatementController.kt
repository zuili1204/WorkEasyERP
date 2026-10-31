package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.mall.mapper.MallQueryMapper
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** 对账单 / 应收：与 ERP `ar_ledger` 同源，商城只做展示 */
@Service
class MallStatementService(
    private val mapper: MallQueryMapper,
    private val creditService: com.workeasy.erp.trade.CreditService,
) {
    @Transactional(readOnly = true)
    fun statements(): List<Map<String, Any?>> {
        val user = MallContext.requireCustomer()
        return mapper.arLedger(user.customerId.toString())
    }

    @Transactional(readOnly = true)
    fun ar(): Map<String, Any?> {
        val user = MallContext.requireCustomer()
        val rows = mapper.arLedger(user.customerId.toString())
        val total = rows.fold(BigDecimal.ZERO) { a, r -> a + MallSupport.bd(r["remainAmount"]) }
        val overdue = rows.filter { (it["overdueDays"] as? Number)?.toInt()?.let { d -> d > 0 } == true }
            .fold(BigDecimal.ZERO) { a, r -> a + MallSupport.bd(r["remainAmount"]) }
        val aging = listOf("0-30" to 30, "31-60" to 60, "61-90" to 90, "90+" to Int.MAX_VALUE)
            .map { (label, max) ->
                mapOf("bucket" to label, "amount" to rows.filter { r ->
                    val d = (r["overdueDays"] as? Number)?.toInt() ?: 0
                    d > 0 && d <= max
                }.fold(BigDecimal.ZERO) { a, r -> a + MallSupport.bd(r["remainAmount"]) })
            }
        val credit = creditService.check(user.customerId.toString(), BigDecimal.ZERO).toMap()
        return mapOf(
            "totalRemain" to total,
            "overdueRemain" to overdue,
            "aging" to aging,
            "items" to rows,
            "credit" to credit,
        )
    }
}

@RestController
@RequestMapping("/mall")
class MallStatementController(
    private val statements: MallStatementService,
    private val afterSales: MallAfterSalesService,
) {

    @GetMapping("/statements")
    fun statements(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(statements.statements())

    @GetMapping("/ar")
    fun ar(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(statements.ar())

    @PostMapping("/after-sales")
    fun afterSales(@RequestBody req: AfterSalesRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(afterSales.apply(req))

    @GetMapping("/after-sales")
    fun myAfterSales(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(afterSales.myRefunds())
}
