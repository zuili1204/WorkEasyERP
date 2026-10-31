package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 商城订单（会员端） */
@RestController
@RequestMapping("/mall/orders")
class MallOrderController(private val service: MallOrderService) {

    @PostMapping("/preview")
    fun preview(@RequestBody req: MallOrderPreviewRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.preview(req))

    @PostMapping
    fun create(@RequestBody req: MallOrderCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.list(status, page, size))

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.detail(id))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String): ApiResponse<Map<String, Any?>> {
        service.cancel(id)
        return ApiResponse.ok(service.detail(id))
    }

    @PostMapping("/{id}/confirm")
    fun confirm(@PathVariable id: String): ApiResponse<Map<String, Any?>> {
        service.confirmReceipt(id)
        return ApiResponse.ok(service.detail(id))
    }
}
