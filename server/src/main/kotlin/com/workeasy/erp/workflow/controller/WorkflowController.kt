package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.workflow.WorkflowService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CommentRequest(val comment: String? = null)
data class TransferRequest(val targetUserId: String = "", val comment: String? = null)

@RestController
@RequestMapping("/workflow/tasks")
class WorkflowController(private val service: WorkflowService) {

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: String,
        @RequestBody(required = false) req: CommentRequest?,
    ): ApiResponse<Boolean> {
        service.approve(UUID.fromString(id), req?.comment, UserContext.get())
        return ApiResponse.ok(true)
    }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: String,
        @RequestBody(required = false) req: CommentRequest?,
    ): ApiResponse<Boolean> {
        service.reject(UUID.fromString(id), req?.comment, UserContext.get())
        return ApiResponse.ok(true)
    }

    @PostMapping("/{id}/transfer")
    fun transfer(
        @PathVariable id: String,
        @RequestBody req: TransferRequest,
    ): ApiResponse<Boolean> {
        service.transfer(UUID.fromString(id), UUID.fromString(req.targetUserId), req.comment, UserContext.get())
        return ApiResponse.ok(true)
    }
}
