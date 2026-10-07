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
@RequestMapping("/inventory/stocktake")
class StocktakeController(private val service: StocktakeService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(q, status, page, size))

    /** 创建盘点单：按仓库账面库存生成明细 */
    @PostMapping
    fun create(@RequestBody req: StocktakeCreateRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.create(req))

    @GetMapping("/{id}/items")
    fun items(@PathVariable id: String): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.items(id))

    /** 录入实盘数量 */
    @PostMapping("/{id}/input")
    fun input(
        @PathVariable id: String,
        @RequestBody lines: List<StocktakeLine>,
    ): ApiResponse<Boolean> {
        service.input(UUID.fromString(id), lines)
        return ApiResponse.ok(true)
    }

    /** 审核：差异生成 adjust 流水并调整库存 */
    @PostMapping("/{id}/audit")
    fun audit(@PathVariable id: String): ApiResponse<Boolean> {
        service.audit(UUID.fromString(id))
        return ApiResponse.ok(true)
    }
}
