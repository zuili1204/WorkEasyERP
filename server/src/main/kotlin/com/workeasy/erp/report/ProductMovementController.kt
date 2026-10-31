package com.workeasy.erp.report

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.report.mapper.ProductMovementRow
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** 商品动销报表：按商品汇总销量/销售额/毛利（MyBatis 查询） */
@RestController
@RequestMapping("/reports")
class ProductMovementController(private val service: ProductMovementService) {

    @GetMapping("/product-movement")
    fun top(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<ProductMovementRow>> = ApiResponse.ok(service.top(from, to, limit))
}
