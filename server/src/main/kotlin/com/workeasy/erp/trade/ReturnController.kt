package com.workeasy.erp.trade

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/returns")
class ReturnController(private val service: ReturnService) {

    // 销售退货：回库 + 红冲应收
    @GetMapping("/sales")
    fun salesReturns(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.salesReturns(q, page, size))

    @PostMapping("/sales")
    fun createSalesReturn(@RequestBody req: SalesReturnRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createSalesReturn(req))

    @PostMapping("/sales/{id}/audit")
    fun auditSalesReturn(@PathVariable id: String): ApiResponse<Boolean> {
        service.auditSalesReturn(UUID.fromString(id))
        return ApiResponse.ok(true)
    }

    // 采购退货：扣减库存 + 红冲应付
    @GetMapping("/purchase")
    fun purchaseReturns(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.purchaseReturns(q, page, size))

    @PostMapping("/purchase")
    fun createPurchaseReturn(@RequestBody req: PurchaseReturnRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createPurchaseReturn(req))

    @PostMapping("/purchase/{id}/audit")
    fun auditPurchaseReturn(@PathVariable id: String): ApiResponse<Boolean> {
        service.auditPurchaseReturn(UUID.fromString(id))
        return ApiResponse.ok(true)
    }
}
