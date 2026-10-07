package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.HrService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 人事事件：regular 转正 / transfer 调岗 / promo 晋升 / dimission 离职（走审批）
 *          entry 入职登记（确认后建档） / contract 劳动合同（登记）
 */
@RestController
@RequestMapping("/hr")
class HrController(private val service: HrService) {

    @GetMapping("/types")
    fun types(): ApiResponse<List<String>> = ApiResponse.ok(service.types)

    @GetMapping("/{kind}")
    fun list(
        @PathVariable kind: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.list(kind, q, scope, page, size))

    @PostMapping("/{kind}")
    fun submit(
        @PathVariable kind: String,
        @RequestBody payload: Map<String, Any?>,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.submit(kind, payload))

    /** 入职确认：生成员工档案 */
    @PostMapping("/entry/{id}/confirm")
    fun confirmEntry(@PathVariable id: String): ApiResponse<Boolean> {
        service.confirmEntry(UUID.fromString(id))
        return ApiResponse.ok(true)
    }
}
