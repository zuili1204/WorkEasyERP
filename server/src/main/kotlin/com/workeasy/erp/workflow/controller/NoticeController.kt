package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.workflow.NoticeDto
import com.workeasy.erp.workflow.NoticeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/notices")
class NoticeController(private val service: NoticeService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) onlyUnread: Boolean?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<NoticeDto>> =
        ApiResponse.ok(service.list(UserContext.get().userId, onlyUnread, page, size))

    @GetMapping("/unread-count")
    fun unreadCount(): ApiResponse<Map<String, Long>> =
        ApiResponse.ok(mapOf("count" to service.unreadCount(UserContext.get().userId)))

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: String): ApiResponse<Boolean> {
        service.markRead(UserContext.get().userId, UUID.fromString(id))
        return ApiResponse.ok(true)
    }

    @PostMapping("/read-all")
    fun markAllRead(): ApiResponse<Boolean> {
        service.markAllRead(UserContext.get().userId)
        return ApiResponse.ok(true)
    }
}
