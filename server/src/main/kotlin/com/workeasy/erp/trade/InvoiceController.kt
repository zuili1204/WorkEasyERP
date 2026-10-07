package com.workeasy.erp.trade

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/finance")
class InvoiceController(private val service: InvoiceService) {

    /** 发票列表：type=sales 销项 / purchase 进项 */
    @GetMapping("/invoices")
    fun invoices(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(type, q, page, size))

    @PostMapping("/invoices")
    fun create(@RequestBody req: InvoiceCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    /** 采购对账：供应商维度 订单额 / 入库额 / 应付 / 已付 / 余额 */
    @GetMapping("/recon/purchase")
    fun purchaseRecon(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.purchaseRecon())

    /** 销售对账：客户维度 订单额 / 出库额 / 应收 / 已收 / 余额 / 授信 */
    @GetMapping("/recon/sales")
    fun salesRecon(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.salesRecon())
}
