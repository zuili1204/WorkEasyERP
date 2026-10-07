package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 审批流程可视化配置 */
@RestController
@RequestMapping("/workflow/config")
class WorkflowConfigController(private val service: WorkflowConfigService) {

    @GetMapping("/definitions")
    fun defs(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.defs())

    @PostMapping("/definitions")
    fun create(@RequestBody req: DefInput): ApiResponse<Boolean> {
        service.createDef(req)
        return ApiResponse.ok(true)
    }

    @GetMapping("/definitions/{code}")
    fun detail(@PathVariable code: String): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.detail(code))

    /** 保存节点（全量覆盖，画布顺序即审批顺序） */
    @PostMapping("/definitions/{code}/nodes")
    fun saveNodes(@PathVariable code: String, @RequestBody nodes: List<NodeInput>): ApiResponse<Boolean> {
        service.saveNodes(code, nodes)
        return ApiResponse.ok(true)
    }

    @PostMapping("/definitions/{code}/status")
    fun setStatus(@PathVariable code: String, @RequestParam status: String): ApiResponse<Boolean> {
        service.setStatus(code, status)
        return ApiResponse.ok(true)
    }

    @GetMapping("/roles")
    fun roles(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.candidateRoles())
}
