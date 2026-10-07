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
@RequestMapping("/inventory")
class InventoryController(private val service: InventoryService) {

    /** 库存现量（含移动加权成本与库存金额） */
    @GetMapping("/stock")
    fun stock(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.stock(q, page, size))

    /** 出入库流水 */
    @GetMapping("/txns")
    fun txns(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.txns(q, page, size))

    /** 入库：按移动加权平均更新成本 */
    @PostMapping("/inbound")
    fun inbound(@RequestBody req: StockMoveRequest): ApiResponse<Boolean> {
        service.inbound(req)
        return ApiResponse.ok(true)
    }

    /** 出库：按当前加权成本计价并扣减 */
    @PostMapping("/outbound")
    fun outbound(@RequestBody req: StockMoveRequest): ApiResponse<Boolean> {
        service.outbound(req)
        return ApiResponse.ok(true)
    }
}
