package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.workflow.DashboardOverview
import com.workeasy.erp.workflow.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dashboard")
class DashboardController(private val service: DashboardService) {

    /** 工作台 / 老板看板概览：统计卡 + 待办数 + 未读数 + 与我相关的流程 */
    @GetMapping("/overview")
    fun overview(): ApiResponse<DashboardOverview> = ApiResponse.ok(service.overview(UserContext.get()))
}
