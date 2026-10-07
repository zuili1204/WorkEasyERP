package com.workeasy.erp.trade

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/** CRM：线索 / 商机 / 合同 */
@RestController
@RequestMapping("/crm")
class CrmController(private val service: CrmService) {

    // ---- 线索 ----
    @GetMapping("/leads")
    fun leads(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.leads(q, status, scope, page, size))

    @PostMapping("/leads")
    fun createLead(@RequestBody req: LeadRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createLead(req))

    /** 线索转客户 */
    @PostMapping("/leads/{id}/convert")
    fun convertLead(
        @PathVariable id: String,
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) creditLimit: BigDecimal?,
        @RequestParam(required = false) terms: Int?,
    ): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.convertLead(UUID.fromString(id), level, creditLimit, terms))

    // ---- 商机 ----
    @GetMapping("/opportunities")
    fun opportunities(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.opportunities(q, status, scope, page, size))

    @PostMapping("/opportunities")
    fun createOpportunity(@RequestBody req: OpportunityRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createOpportunity(req))

    /** 推进阶段：contact/quote/solution/negotiation/won/lost */
    @PostMapping("/opportunities/{id}/stage")
    fun updateStage(
        @PathVariable id: String,
        @RequestParam stage: String,
        @RequestParam(required = false) lostReason: String?,
    ): ApiResponse<Boolean> {
        service.updateStage(UUID.fromString(id), stage, lostReason)
        return ApiResponse.ok(true)
    }

    /** 商机转合同 */
    @PostMapping("/opportunities/{id}/convert")
    fun convertToContract(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.convertToContract(UUID.fromString(id)))

    // ---- 合同 ----
    @GetMapping("/contracts")
    fun contracts(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.contracts(q, status, scope, page, size))

    @PostMapping("/contracts")
    fun createContract(@RequestBody req: ContractRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createContract(req))

    /** 扫描并标记过期合同 */
    @PostMapping("/contracts/mark-expired")
    fun markExpired(): ApiResponse<Int> = ApiResponse.ok(service.markExpired())
}
