package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.workflow.TodoDto
import com.workeasy.erp.workflow.TodoService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/todos")
class TodoController(private val service: TodoService) {

    /** 四视图：submitted 我提交的 / pending 待我办 / done 已办结 / cc 抄送我 */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "pending") view: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<TodoDto>> =
        ApiResponse.ok(service.list(view, UserContext.get(), page, size))
}
