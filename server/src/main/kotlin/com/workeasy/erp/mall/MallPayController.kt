package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 商城支付：JSAPI 参数、mock 支付、支付回调（免登录） */
@RestController
@RequestMapping("/mall/pay")
class MallPayController(private val service: MallPayService) {

    @PostMapping("/jsapi")
    fun jsapi(@RequestBody req: ConfirmOrderRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.jsapi(req.orderId))

    @PostMapping("/mock/{orderId}")
    fun mockPay(@PathVariable orderId: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.mockPay(orderId))

    /** 微信支付回调：URL 带 tenantCode，免登录、验签、幂等 */
    @PostMapping("/notify/{tenantCode}")
    fun notify(@PathVariable tenantCode: String, @RequestBody req: WxNotifyRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.notify(tenantCode, req))
}

data class ConfirmOrderRequest(val orderId: String = "")
