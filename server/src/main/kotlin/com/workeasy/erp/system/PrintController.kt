package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 单据打印：
 * - `GET /print/{type}/{id}`        返回打印数据（前端渲染 A4 预览页，可浏览器打印 / 另存 PDF）
 * - `GET /print/{type}/{id}/pdf`    服务端生成 PDF 文件（适合归档、邮件附件）
 * type 支持：sales_order / purchase_order / purchase_inbound / sales_outbound / payment
 */
@RestController
@RequestMapping("/print")
class PrintController(private val service: PrintService) {

    @GetMapping("/{type}/{id}")
    fun bill(@PathVariable type: String, @PathVariable id: String): ApiResponse<PrintBill> =
        ApiResponse.ok(service.bill(type, id))

    @GetMapping("/{type}/{id}/pdf")
    fun pdf(@PathVariable type: String, @PathVariable id: String): ResponseEntity<ByteArray> {
        val bill = service.bill(type, id)
        val bytes = service.pdf(type, id)
        val name = URLEncoder.encode("${bill.title}-${bill.no}.pdf", StandardCharsets.UTF_8.toString())
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$name")
            .body(bytes)
    }
}
