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
@RequestMapping("/customers")
class CustomerController(private val service: CustomerService) {

    /**
     * 客户列表：scope=mine|dept|all（默认按角色数据范围推导）
     * 例：GET /api/customers?scope=mine&level=A&q=宏达
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) level: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(q, scope, level, page, size))

    @PostMapping
    fun create(@RequestBody req: CustomerCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))
}
