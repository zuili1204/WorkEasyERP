package com.workeasy.erp.workflow

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

/** 报销：登记 → 提交审批（主管 → 财务）→ 付款 */
@RestController
@RequestMapping("/expenses")
class ExpenseController(private val service: ExpenseService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.list(q, status, scope, page, size))

    @PostMapping
    fun create(@RequestBody req: ExpenseCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.submit(UUID.fromString(id)))

    @PostMapping("/{id}/pay")
    fun pay(@PathVariable id: String): ApiResponse<Boolean> {
        service.pay(UUID.fromString(id))
        return ApiResponse.ok(true)
    }
}
