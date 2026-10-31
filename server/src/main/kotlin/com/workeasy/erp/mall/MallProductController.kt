package com.workeasy.erp.mall

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 商城商品 / 分类 */
@RestController
@RequestMapping("/mall")
class MallProductController(private val service: MallProductService) {

    @GetMapping("/products")
    fun products(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> = ApiResponse.ok(service.products(q, categoryId, page, size))

    @GetMapping("/products/{id}")
    fun detail(@PathVariable id: String): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.detail(id))

    @GetMapping("/categories")
    fun categories(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.categories())
}

/** 购物车（按租户隔离，存租户库） */
@RestController
@RequestMapping("/mall/cart")
class MallCartController(private val service: MallProductService) {

    @GetMapping
    fun list(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(service.cart())

    @PostMapping
    fun add(@RequestBody req: MallCartRequest): ApiResponse<Boolean> {
        service.addToCart(req)
        return ApiResponse.ok(true)
    }

    @PatchMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody req: MallCartUpdateRequest): ApiResponse<Boolean> {
        service.updateCart(id, req)
        return ApiResponse.ok(true)
    }

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): ApiResponse<Boolean> {
        service.removeCart(id)
        return ApiResponse.ok(true)
    }

    @DeleteMapping
    fun clear(): ApiResponse<Boolean> {
        service.clearCart()
        return ApiResponse.ok(true)
    }
}
