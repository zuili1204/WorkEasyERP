package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.OaApplyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 通用假勤申请：overtime 加班 / appeal 补卡 / outing 外出 / trip 出差 */
@RestController
@RequestMapping("/oa")
class OaApplyController(private val service: OaApplyService) {

    @GetMapping("/types")
    fun types(): ApiResponse<List<String>> = ApiResponse.ok(service.types)

    @PostMapping("/{bizType}")
    fun submit(
        @PathVariable bizType: String,
        @RequestBody payload: Map<String, Any?>,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.submit(bizType, payload))

    @GetMapping("/{bizType}")
    fun list(
        @PathVariable bizType: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(bizType, q, scope, page, size))
}
