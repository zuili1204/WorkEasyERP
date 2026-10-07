package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** 允许的扩展名白名单（其余一律拒绝，避免上传可执行脚本） */
private val ALLOWED_EXT = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "txt", "csv", "zip",
)

private const val MAX_SIZE = 10L * 1024 * 1024 // 10MB

data class AttachmentVo(
    val id: String,
    val bizType: String,
    val bizId: String?,
    val fileName: String,
    val fileExt: String?,
    val fileSize: Long,
    val uploaderName: String?,
    val remark: String?,
    val createdAt: String?,
)

/** 附件：上传落盘 + 元数据入库 + 按业务单据查询 / 下载 / 删除 */
@Service
class AttachmentService(
    private val jdbc: JdbcTemplate,
    private val storage: FileStorageService,
    private val audit: AuditService,
) {

    @Transactional
    fun upload(file: MultipartFile, bizType: String, bizId: String?, remark: String?): Map<String, Any?> {
        if (file.isEmpty) throw BizException(ErrorCode.BIZ_PARAM, "文件为空")
        if (file.size > MAX_SIZE) throw BizException(ErrorCode.BIZ_PARAM, "文件不能超过 10MB")

        val original = sanitize(file.originalFilename)
        val ext = original.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext !in ALLOWED_EXT) {
            throw BizException(ErrorCode.BIZ_PARAM, "不支持的文件类型：$ext（允许 ${ALLOWED_EXT.joinToString("/")}）")
        }

        val me = UserContext.get()
        val id = UUID.randomUUID()
        val rel = storage.save(file.bytes, ext)

        jdbc.update(
            """
            INSERT INTO attachment(id, biz_type, biz_id, file_name, file_ext, file_size,
                                   content_type, storage_path, uploader_id, uploader_name, remark)
            VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?::uuid, ?, ?)
            """.trimIndent(),
            id.toString(), bizType, bizId, original, ext, file.size,
            file.contentType, rel, me.userId.toString(), me.displayName, remark,
        )
        audit.log("attachment", "上传附件 $original（${bizType}）", "attachment", id.toString())
        return mapOf("id" to id.toString(), "fileName" to original, "size" to file.size.toString())
    }

    @Transactional(readOnly = true)
    fun list(bizType: String, bizId: String): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT id, biz_type, biz_id, file_name, file_ext, file_size, uploader_name, remark, created_at
        FROM attachment
        WHERE biz_type = ? AND biz_id = ?::uuid AND deleted_at IS NULL
        ORDER BY created_at DESC
        """.trimIndent(),
        bizType, bizId,
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    /** 下载：返回字节与原始文件名 */
    @Transactional(readOnly = true)
    fun download(id: String): Pair<ByteArray, String> {
        val row = rowOf(id)
        val rel = row["storage_path"]?.toString()
            ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "附件记录不完整")
        return storage.read(rel) to (row["file_name"]?.toString() ?: "attachment")
    }

    /** 删除：软删除并清理磁盘文件（删除者需为上传者或老板/财务，控制器侧校验） */
    @Transactional
    fun remove(id: String) {
        val row = rowOf(id)
        row["storage_path"]?.toString()?.let { storage.delete(it) }
        jdbc.update("UPDATE attachment SET deleted_at = now() WHERE id = ?::uuid", id)
        audit.log("attachment", "删除附件 ${row["file_name"]}", "attachment", id)
    }

    @Transactional(readOnly = true)
    fun rowOf(id: String): Map<String, Any?> {
        val rows = jdbc.queryForList(
            "SELECT * FROM attachment WHERE id = ?::uuid AND deleted_at IS NULL", id
        )
        if (rows.isEmpty()) throw BizException(ErrorCode.BIZ_NOT_FOUND, "附件不存在")
        return rows.first()
    }

    /** 去掉路径分隔符，避免恶意文件名 */
    private fun sanitize(name: String?): String {
        val raw = name?.substringAfterLast('\\')?.substringAfterLast('/')?.trim().orEmpty()
        val cleaned = raw.replace(Regex("[\\p{Cntrl}]"), "").trim()
        return cleaned.ifBlank { "unnamed" }.take(200)
    }
}
