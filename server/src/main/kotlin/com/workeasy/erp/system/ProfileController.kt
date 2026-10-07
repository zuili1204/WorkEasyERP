package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.config.UserContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profile")
class ProfileController(private val service: ProfileService) {

    /** 个人中心：档案 + 角色 + 部门 + 个人统计（数据范围严格 self） */
    @GetMapping
    fun profile(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.profile(UserContext.get()))
}
