package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.PayrollGenerateRequest
import com.workeasy.erp.workflow.PayrollService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payroll")
class PayrollController(private val service: PayrollService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(period, scope, page, size))

    @PostMapping
    fun generate(@RequestBody req: PayrollGenerateRequest): ApiResponse<Boolean> {
        service.generate(req)
        return ApiResponse.ok(true)
    }
}
