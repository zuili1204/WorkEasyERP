package com.workeasy.erp.user

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/employees")
class EmployeeController(private val service: EmployeeService) {

    /**
     * 员工列表：q 搜索 / scope 数据范围（mine|dept|all）/ status 状态 / sort 排序 / page+size 分页
     * 例：GET /api/employees?scope=dept&sort=realName,asc&page=1&size=8
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
    ): ApiResponse<PageResult<EmployeeDto>> =
        ApiResponse.ok(service.list(q, scope, status, page, size, sort))

    @PostMapping
    fun create(@RequestBody req: EmployeeCreateRequest): ApiResponse<EmployeeDto> =
        ApiResponse.ok(service.create(req))
}
