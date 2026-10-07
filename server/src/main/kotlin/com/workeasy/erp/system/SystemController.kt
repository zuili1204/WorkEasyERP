package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class AssignPermissionRequest(val codes: List<String> = emptyList())

@RestController
@RequestMapping("/system")
class SystemController(private val service: SystemService) {

    /** 操作日志 */
    @GetMapping("/audit-logs")
    fun auditLogs(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) module: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.auditLogs(q, module, page, size))

    @GetMapping("/modules")
    fun modules(): ApiResponse<List<String>> = ApiResponse.ok(service.modules())

    /** 角色与权限 */
    @GetMapping("/roles")
    fun roles(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.roles())

    @GetMapping("/permissions")
    fun permissions(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.permissions())

    @GetMapping("/roles/{code}/permissions")
    fun rolePermissions(@PathVariable code: String): ApiResponse<List<String>> =
        ApiResponse.ok(service.rolePermissionCodes(code))

    @PostMapping("/roles/{code}/permissions")
    fun assignPermissions(
        @PathVariable code: String,
        @RequestBody req: AssignPermissionRequest,
    ): ApiResponse<Boolean> {
        service.assignPermissions(code, req.codes)
        return ApiResponse.ok(true)
    }

    /** 审批流程配置 */
    @GetMapping("/workflows")
    fun workflows(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.workflowDefs())

    @GetMapping("/workflows/{id}/nodes")
    fun workflowNodes(@PathVariable id: String): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.workflowNodes(id))
}
