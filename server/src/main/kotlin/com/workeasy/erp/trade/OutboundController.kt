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
@RequestMapping("/sales/outbounds")
class OutboundController(private val service: OutboundService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(q, status, scope, page, size))

    @PostMapping
    fun create(@RequestBody req: OutboundCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    /** 审核：扣库存（加权成本计价）+ 生成应收台账 + 占用授信 */
    @PostMapping("/{id}/audit")
    fun audit(@PathVariable id: String): ApiResponse<Boolean> {
        service.auditOutbound(UUID.fromString(id))
        return ApiResponse.ok(true)
    }
}
