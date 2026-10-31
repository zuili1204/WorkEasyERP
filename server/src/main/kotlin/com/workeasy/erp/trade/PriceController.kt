package com.workeasy.erp.trade

import com.workeasy.erp.common.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 价格体系：下单预览取价 + 等级价/协议价维护 */
@RestController
@RequestMapping("/prices")
class PriceController(private val service: PriceService) {

    /** 批量取价：协议价 > 等级价 > 标准售价 */
    @PostMapping("/quote")
    fun quote(@RequestBody req: QuoteRequest): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.quote(req))

    @GetMapping("/product/{productId}")
    fun productPrices(@PathVariable productId: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.productPrices(productId))

    @PutMapping("/product/{productId}/{level}")
    fun setLevelPrice(
        @PathVariable productId: String,
        @PathVariable level: String,
        @RequestBody req: PriceLevelRequest,
    ): ApiResponse<Boolean> {
        service.setLevelPrice(productId, level, req.price)
        return ApiResponse.ok(true)
    }

    @DeleteMapping("/product/{productId}/{level}")
    fun deleteLevelPrice(
        @PathVariable productId: String,
        @PathVariable level: String,
    ): ApiResponse<Boolean> {
        service.deleteLevelPrice(productId, level)
        return ApiResponse.ok(true)
    }

    @GetMapping("/customer/{customerId}")
    fun customerPrices(@PathVariable customerId: String): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.customerPrices(customerId))

    @PostMapping("/customer/{customerId}")
    fun addCustomerPrice(
        @PathVariable customerId: String,
        @RequestBody req: CustomerPriceRequest,
    ): ApiResponse<Boolean> {
        service.addCustomerPrice(customerId, req)
        return ApiResponse.ok(true)
    }

    @DeleteMapping("/customer/{customerId}/{priceId}")
    fun disableCustomerPrice(
        @PathVariable customerId: String,
        @PathVariable priceId: String,
    ): ApiResponse<Boolean> {
        service.disableCustomerPrice(customerId, priceId)
        return ApiResponse.ok(true)
    }
}
