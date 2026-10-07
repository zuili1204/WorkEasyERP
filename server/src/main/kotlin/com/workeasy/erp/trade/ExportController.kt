package com.workeasy.erp.trade

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** 报表导出：GET /export/{type} 返回 xlsx 文件 */
@RestController
@RequestMapping("/export")
class ExportController(private val service: ExportService) {

    @GetMapping("/types")
    fun types(): List<String> = service.types

    @GetMapping("/{type}")
    fun export(@PathVariable type: String): ResponseEntity<ByteArray> {
        val bytes = service.export(type)
        val name = URLEncoder.encode("$type-${LocalDate.now()}.xlsx", StandardCharsets.UTF_8.toString())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$name")
            .body(bytes)
    }
}
