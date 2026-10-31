package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 平台会员接口：微信登录 / 手机号 / 租户列表 / 加入 / 换租户令牌 */
@RestController
@RequestMapping("/mall")
class MallAuthController(private val service: MallAuthService) {

    @PostMapping("/auth/wx-login")
    fun wxLogin(@RequestBody req: WxLoginRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.wxLogin(req))

    @PostMapping("/auth/phone")
    fun bindPhone(@RequestBody req: PhoneBindRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.bindPhone(MallContext.get().memberId, req))

    @GetMapping("/tenants")
    fun tenants(): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.tenants(MallContext.get().memberId))

    @PostMapping("/auth/join")
    fun join(@RequestBody req: JoinTenantRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.join(MallContext.get().memberId, req))

    @PostMapping("/auth/token")
    fun token(@RequestBody req: TenantTokenRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.tenantToken(MallContext.get().memberId, req))

    @GetMapping("/me")
    fun me(): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.me(MallContext.get().memberId))
}
