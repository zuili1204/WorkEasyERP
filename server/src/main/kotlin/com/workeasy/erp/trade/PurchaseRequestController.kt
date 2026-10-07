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

/** 采购申请：起草 → 提交审批 → 转采购订单 */
@RestController
@RequestMapping("/purchase/requests")
class PurchaseRequestController(private val service: PurchaseRequestService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.list(q, status, scope, page, size))

    @PostMapping
    fun create(@RequestBody req: PurchaseRequestCreate): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    @GetMapping("/{id}/items")
    fun items(@PathVariable id: String): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.items(id))

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.submit(UUID.fromString(id)))

    /** 审批通过 → 一键转采购订单 */
    @PostMapping("/{id}/to-order")
    fun toOrder(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.toOrder(UUID.fromString(id)))
}
