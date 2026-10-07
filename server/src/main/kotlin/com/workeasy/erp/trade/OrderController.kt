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

/** 订单层：订单表达意向与条款，收发由履约单据执行（文档 §9.1） */
@RestController
@RequestMapping("/orders")
class OrderController(private val service: OrderService) {

    @GetMapping("/purchase")
    fun purchaseOrders(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.poList(q, status, page, size))

    @PostMapping("/purchase")
    fun createPurchaseOrder(@RequestBody req: OrderCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createPo(req))

    /** 提交采购订单审批（草稿 / 已驳回 可提交） */
    @PostMapping("/purchase/{id}/submit")
    fun submitPurchaseOrder(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.submit("purchase", UUID.fromString(id)))

    @GetMapping("/sales")
    fun salesOrders(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.soList(q, status, scope, page, size))

    @PostMapping("/sales")
    fun createSalesOrder(@RequestBody req: OrderCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createSo(req))

    /** 提交销售订单审批（草稿 / 已驳回 可提交） */
    @PostMapping("/sales/{id}/submit")
    fun submitSalesOrder(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.submit("sales", UUID.fromString(id)))
}
