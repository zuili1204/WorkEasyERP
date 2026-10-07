package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 基础数据：category 分类 / product 商品 / supplier 供应商 / warehouse 仓库 / currency 汇率 */
@RestController
@RequestMapping("/basedata")
class BaseDataController(private val service: BaseDataService) {

    @GetMapping("/overview")
    fun overview(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.overview())

    @GetMapping("/types")
    fun types(): ApiResponse<List<String>> = ApiResponse.ok(service.types)

    @GetMapping("/{kind}")
    fun list(
        @PathVariable kind: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(kind, q, page, size))

    @PostMapping("/{kind}")
    fun create(
        @PathVariable kind: String,
        @RequestBody payload: Map<String, Any?>,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.create(kind, payload))
}
