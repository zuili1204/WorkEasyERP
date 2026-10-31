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

/** 调拨单：草稿 / 审核过账 / 作废 */
@RestController
@RequestMapping("/transfers")
class TransferController(private val service: TransferService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(q, status, page, size))

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.detail(id))

    @PostMapping
    fun create(@RequestBody req: TransferCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    /** 审核：两仓原子过账（两条库存流水） */
    @PostMapping("/{id}/audit")
    fun audit(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.audit(UUID.fromString(id)))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.cancel(UUID.fromString(id)))
}
