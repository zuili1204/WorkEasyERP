package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 商城后台（员工 JWT，租户域）：设置 / 轮播 / 邀请码 / 会员绑定 / 订单 / 支付 / 售后。
 * 路径前缀刻意用 mall-admin，避免与会员端 mall 接口的鉴权规则冲突。
 */
@RestController
@RequestMapping("/mall-admin")
class MallAdminController(private val service: MallAdminService) {

    @GetMapping("/overview")
    fun overview(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.overview())

    // ---------- 设置 ----------

    @GetMapping("/settings")
    fun settings(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.settings())

    @PutMapping("/settings")
    fun updateSettings(@RequestBody body: Map<String, String?>): ApiResponse<Boolean> {
        service.updateSettings(body)
        return ApiResponse.ok(true)
    }

    // ---------- 轮播 ----------

    @GetMapping("/banners")
    fun banners(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.banners())

    @PostMapping("/banners")
    fun createBanner(@RequestBody req: MallBannerRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createBanner(req))

    @PutMapping("/banners/{id}")
    fun updateBanner(
        @PathVariable id: String,
        @RequestBody req: MallBannerRequest,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.updateBanner(id, req))

    @DeleteMapping("/banners/{id}")
    fun deleteBanner(@PathVariable id: String): ApiResponse<Boolean> {
        service.deleteBanner(id)
        return ApiResponse.ok(true)
    }

    // ---------- 邀请码 ----------

    @GetMapping("/invites")
    fun invites(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.invites())

    @PostMapping("/invites")
    fun createInvite(@RequestBody req: MallInviteRequest): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.createInvite(req))

    @PatchMapping("/invites/{id}/status")
    fun setInviteStatus(
        @PathVariable id: String,
        @RequestBody req: MallInviteStatusRequest,
    ): ApiResponse<Boolean> {
        service.setInviteStatus(id, req.status)
        return ApiResponse.ok(true)
    }

    // ---------- 会员绑定审核 ----------

    @GetMapping("/bindings")
    fun bindings(@RequestParam(required = false) status: String?): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.bindings(status))

    @PostMapping("/bindings/{id}/approve")
    fun approveBinding(
        @PathVariable id: String,
        @RequestBody req: MallBindingApproveRequest,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.approveBinding(id, req))

    @PostMapping("/bindings/{id}/reject")
    fun rejectBinding(
        @PathVariable id: String,
        @RequestBody req: MallRemarkRequest,
    ): ApiResponse<Boolean> {
        service.rejectBinding(id, req.remark)
        return ApiResponse.ok(true)
    }

    // ---------- 商城订单 ----------

    @GetMapping("/orders")
    fun orders(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.orders(q, status, page, size))

    @GetMapping("/orders/{id}")
    fun orderDetail(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.orderDetail(id))

    @PostMapping("/orders/{id}/close")
    fun closeOrder(
        @PathVariable id: String,
        @RequestBody(required = false) req: MallRemarkRequest?,
    ): ApiResponse<Boolean> {
        service.closeOrder(id, req?.remark)
        return ApiResponse.ok(true)
    }

    /** 账期超授信 → 经理确认放行 */
    @PostMapping("/orders/{id}/confirm-credit")
    fun confirmCredit(@PathVariable id: String): ApiResponse<Boolean> {
        service.confirmCredit(id)
        return ApiResponse.ok(true)
    }

    /** 线下打款确认收款 */
    @PostMapping("/orders/{id}/confirm-offline")
    fun confirmOffline(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.confirmOffline(id))

    // ---------- 支付流水 ----------

    @GetMapping("/payments")
    fun payments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.payments(q, status, page, size))

    // ---------- 售后 ----------

    @GetMapping("/after-sales")
    fun afterSales(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.afterSales(status, page, size))

    @PostMapping("/after-sales/{id}/approve")
    fun approveRefund(
        @PathVariable id: String,
        @RequestBody(required = false) req: MallRemarkRequest?,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.approveRefund(id, req?.remark))

    @PostMapping("/after-sales/{id}/reject")
    fun rejectRefund(
        @PathVariable id: String,
        @RequestBody(required = false) req: MallRemarkRequest?,
    ): ApiResponse<Boolean> {
        service.rejectRefund(id, req?.remark)
        return ApiResponse.ok(true)
    }
}
