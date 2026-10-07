package com.workeasy.erp.user

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.FieldPolicy
import com.workeasy.erp.config.UserContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.login(req))

    @GetMapping("/me")
    fun me(): ApiResponse<LoginUserInfo> {
        val u = UserContext.get()
        return ApiResponse.ok(
            LoginUserInfo(
                id = u.userId.toString(),
                username = u.username,
                displayName = u.displayName,
                deptName = u.deptName,
                roles = u.roles,
                dataScope = u.dataScope,
                hiddenFields = FieldPolicy.hiddenFields(u.roles),
            )
        )
    }
}
