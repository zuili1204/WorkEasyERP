package com.workeasy.erp.report

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.report.mapper.CustomerContributionRow
import com.workeasy.erp.report.mapper.InventoryTurnoverRow
import com.workeasy.erp.report.mapper.SalesProfitRow
import com.workeasy.erp.report.mapper.SlowMovingRow
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** 经营分析报表：库存周转、滞销 Top、客户贡献、销售毛利 + xlsx 导出 */
@RestController
@RequestMapping("/reports")
class AnalysisReportController(private val service: AnalysisReportService) {

    @GetMapping("/inventory-turnover")
    fun inventoryTurnover(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<InventoryTurnoverRow>> = ApiResponse.ok(service.inventoryTurnover(from, to, limit))

    @GetMapping("/slow-moving")
    fun slowMoving(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<SlowMovingRow>> = ApiResponse.ok(service.slowMoving(from, to, limit))

    @GetMapping("/customer-contribution")
    fun customerContribution(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<CustomerContributionRow>> = ApiResponse.ok(service.customerContribution(from, to, limit))

    @GetMapping("/sales-profit")
    fun salesProfit(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<SalesProfitRow>> = ApiResponse.ok(service.salesProfit(from, to, limit))

    /** 五类报表按 type 导出 xlsx：product_movement / inventory_turnover / slow_moving / customer_contribution / sales_profit */
    @GetMapping("/export/{type}")
    fun export(
        @PathVariable type: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "500") limit: Int,
    ): ResponseEntity<ByteArray> {
        val bytes = service.export(type, from, to, limit)
        val name = URLEncoder.encode("$type-${LocalDate.now()}.xlsx", StandardCharsets.UTF_8.toString())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$name")
            .body(bytes)
    }
}
