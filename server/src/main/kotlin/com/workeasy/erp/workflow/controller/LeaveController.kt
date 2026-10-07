package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.LeaveDto
import com.workeasy.erp.workflow.LeaveService
import com.workeasy.erp.workflow.LeaveSubmitRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/leaves")
class LeaveController(private val service: LeaveService) {

    /** 提交请假：写单 + 发起审批流程 */
    @PostMapping
    fun submit(@RequestBody req: LeaveSubmitRequest): ApiResponse<LeaveDto> =
        ApiResponse.ok(service.submit(req))

    /** 重新提交：仅被驳回 / 已撤销的单据 */
    @PostMapping("/{id}/resubmit")
    fun resubmit(
        @PathVariable id: String,
        @RequestBody req: LeaveSubmitRequest,
    ): ApiResponse<LeaveDto> = ApiResponse.ok(service.resubmit(UUID.fromString(id), req))

    /** 撤销归档：进行中的流程一并结束 */
    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String): ApiResponse<Boolean> {
        service.cancel(UUID.fromString(id))
        return ApiResponse.ok(true)
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<LeaveDto>> =
        ApiResponse.ok(service.list(q, scope, status, page, size))
}
