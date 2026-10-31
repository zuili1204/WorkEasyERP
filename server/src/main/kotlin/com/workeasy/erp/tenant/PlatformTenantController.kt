package com.workeasy.erp.tenant

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 平台侧租户运维接口（平台运营页使用）：
 * 使用平台令牌 `X-Platform-Token` 鉴权，与员工 JWT 完全隔离。
 */
@RestController
@RequestMapping("/platform")
class PlatformTenantController(
    private val provisioning: TenantProvisioningService,
    private val ops: TenantOpsService,
    @Value("\${workeasy.platform.admin-token:}") private val adminToken: String,
) {

    @PostMapping("/tenants")
    fun create(
        @RequestHeader(value = "X-Platform-Token", required = false) token: String?,
        @RequestBody req: TenantCreateRequest,
    ): ApiResponse<TenantCreateResult> {
        checkToken(token)
        return ApiResponse.ok(provisioning.provision(req))
    }

    @GetMapping("/tenants")
    fun list(
        @RequestHeader(value = "X-Platform-Token", required = false) token: String?,
    ): ApiResponse<List<Map<String, Any?>>> {
        checkToken(token)
        return ApiResponse.ok(provisioning.list())
    }

    /** 停用 / 启用 / 调整到期日 */
    @PatchMapping("/tenants/{code}/status")
    fun updateStatus(
        @RequestHeader(value = "X-Platform-Token", required = false) token: String?,
        @PathVariable code: String,
        @RequestBody req: TenantStatusRequest,
    ): ApiResponse<Boolean> {
        checkToken(token)
        ops.updateStatus(code, req)
        return ApiResponse.ok(true)
    }

    /** 上线加固指标：租户池、开通/迁移失败、到期预警、负库存 */
    @GetMapping("/metrics")
    fun metrics(
        @RequestHeader(value = "X-Platform-Token", required = false) token: String?,
    ): ApiResponse<Map<String, Any?>> {
        checkToken(token)
        return ApiResponse.ok(ops.metrics())
    }

    private fun checkToken(token: String?) {
        if (adminToken.isBlank() || token.isNullOrBlank() || token != adminToken) {
            throw BizException(ErrorCode.AUTH_FORBIDDEN, "平台令牌无效")
        }
    }
}
