package com.workeasy.erp.system

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.UserContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 附件：
 * - `POST /attachments/upload`（multipart：file + bizType + bizId? + remark?）
 * - `GET  /attachments?bizType=&bizId=`
 * - `GET  /attachments/{id}/download`
 * - `DELETE /attachments/{id}`（仅上传者本人或老板可删）
 */
@RestController
@RequestMapping("/attachments")
class AttachmentController(private val service: AttachmentService) {

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam bizType: String,
        @RequestParam(required = false) bizId: String?,
        @RequestParam(required = false) remark: String?,
    ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.upload(file, bizType, bizId, remark))

    @GetMapping
    fun list(@RequestParam bizType: String, @RequestParam bizId: String): ApiResponse<List<Map<String, Any?>>> =
        ApiResponse.ok(service.list(bizType, bizId))

    @GetMapping("/{id}/download")
    fun download(@PathVariable id: String): ResponseEntity<ByteArrayResource> {
        val (bytes, name) = service.download(id)
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encoded")
            .body(ByteArrayResource(bytes))
    }

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): ApiResponse<Boolean> {
        val row = service.rowOf(id)
        val me = UserContext.get()
        val owner = row["uploader_id"]?.toString()
        val isOwner = owner != null && owner == me.userId.toString()
        val isBoss = me.roles.contains("boss")
        if (!isOwner && !isBoss) throw BizException(ErrorCode.AUTH_FORBIDDEN, "只能删除自己上传的附件")
        service.remove(id)
        return ApiResponse.ok(true)
    }
}
