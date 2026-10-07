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
@RequestMapping("/departments")
class DepartmentController(private val service: DepartmentService) {

    /**
     * 部门列表：q 搜索 / sort 排序 / page+size 分页
     * 例：GET /api/departments?q=销售&sort=name,asc&page=1&size=20
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
    ): ApiResponse<PageResult<DepartmentDto>> = ApiResponse.ok(service.list(q, page, size, sort))

    @PostMapping
    fun create(@RequestBody req: DepartmentCreateRequest): ApiResponse<DepartmentDto> =
        ApiResponse.ok(service.create(req))
}
