package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 商品扩展：详情 / 上下架与线上属性 / 图片维护 */
@RestController
@RequestMapping("/products")
class ProductExtController(private val service: ProductExtService) {

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.detail(id))

    /** 上下架与线上属性（只更新传入字段） */
    @PutMapping("/{id}/online")
    fun updateOnline(
        @PathVariable id: String,
        @RequestBody req: ProductOnlineRequest,
    ): ApiResponse<Boolean> {
        service.updateOnline(id, req)
        return ApiResponse.ok(true)
    }

    @PostMapping("/{id}/images")
    fun addImage(
        @PathVariable id: String,
        @RequestBody req: ProductImageRequest,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.addImage(id, req))

    @DeleteMapping("/{id}/images/{imageId}")
    fun removeImage(
        @PathVariable id: String,
        @PathVariable imageId: String,
    ): ApiResponse<Boolean> {
        service.removeImage(id, imageId)
        return ApiResponse.ok(true)
    }
}
