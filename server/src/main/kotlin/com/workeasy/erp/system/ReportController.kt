package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 自定义报表：定义管理 + 模板化运行 */
@RestController
@RequestMapping("/reports")
class ReportController(private val service: ReportService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.list(q, page, size))

    @GetMapping("/modules")
    fun modules(): ApiResponse<List<String>> = ApiResponse.ok(service.modules())

    @PostMapping
    fun create(@RequestBody req: ReportCreate): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): ApiResponse<Boolean> {
        service.remove(id)
        return ApiResponse.ok(true)
    }

    @GetMapping("/{id}/run")
    fun run(@PathVariable id: String): ApiResponse<ReportResult> = ApiResponse.ok(service.run(id))

    /** 不落定义直接试跑模板 */
    @GetMapping("/preview")
    fun preview(@RequestParam module: String): ApiResponse<ReportResult> = ApiResponse.ok(service.preview(module))
}
