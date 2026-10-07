package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

/** 多币种与汇率 */
@RestController
@RequestMapping("/fx")
class FxController(private val service: FxService) {

    @GetMapping("/currencies")
    fun currencies(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.currencies())

    @PostMapping("/currencies")
    fun saveCurrency(@RequestBody req: CurrencyUpsert): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.saveCurrency(req))

    @PostMapping("/currencies/{code}/base")
    fun setBase(@PathVariable code: String): ApiResponse<Boolean> {
        service.setBase(code)
        return ApiResponse.ok(true)
    }

    @PostMapping("/currencies/{code}/toggle")
    fun toggle(@PathVariable code: String, @RequestParam enabled: Boolean): ApiResponse<Boolean> {
        service.toggle(code, enabled)
        return ApiResponse.ok(true)
    }

    @GetMapping("/rates")
    fun rates(
        @RequestParam(required = false) currency: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.rates(currency, page, size))

    @PostMapping("/rates")
    fun upsertRate(@RequestBody req: RateUpsert): ApiResponse<Boolean> {
        service.upsertRate(req)
        return ApiResponse.ok(true)
    }

    @DeleteMapping("/rates/{id}")
    fun deleteRate(@PathVariable id: String): ApiResponse<Boolean> {
        service.deleteRate(id)
        return ApiResponse.ok(true)
    }

    /** 换算：GET /fx/convert?amount=100&from=USD&to=CNY&date=2026-10-05 */
    @GetMapping("/convert")
    fun convert(
        @RequestParam amount: BigDecimal,
        @RequestParam from: String,
        @RequestParam(defaultValue = "CNY") to: String,
        @RequestParam(required = false) date: String?,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(
        service.convert(
            amount,
            from,
            to,
            date?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        )
    )
}
