package com.workeasy.erp.system

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 本地文件存储：按 `yyyy/MM/dd` 分目录，文件名用 UUID 避免重名与路径穿越。
 * 生产替换为对象存储（OSS/S3/MinIO）时，只需替换本实现，`attachment.storage_path` 语义不变。
 */
@Service
class FileStorageService(
    @Value("\${erp.storage.dir:./data/uploads}") private val root: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    private lateinit var baseDir: Path

    @PostConstruct
    fun init() {
        baseDir = Paths.get(root).toAbsolutePath().normalize()
        Files.createDirectories(baseDir)
        log.info("附件存储目录：{}", baseDir)
    }

    /** 落盘并返回相对路径（供 attachment.storage_path 存储） */
    fun save(bytes: ByteArray, ext: String): String {
        val rel = "${LocalDate.now().format(dayFmt)}/${UUID.randomUUID()}${if (ext.isBlank()) "" else ".$ext"}"
        val target = baseDir.resolve(rel)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        return rel
    }

    fun read(rel: String): ByteArray {
        val p = safeResolve(rel)
        if (!Files.exists(p)) throw BizExceptionCompat.notFound("附件文件已丢失")
        return Files.readAllBytes(p)
    }

    fun delete(rel: String) {
        runCatching { Files.deleteIfExists(safeResolve(rel)) }
    }

    /** 防路径穿越：解析后必须仍在 baseDir 内 */
    private fun safeResolve(rel: String): Path {
        val p = baseDir.resolve(rel).normalize()
        if (!p.startsWith(baseDir)) throw BizExceptionCompat.notFound("非法的附件路径")
        return p
    }
}

/** 避免 system 包与 common 的异常类循环依赖，此处内联错误抛出 */
private object BizExceptionCompat {
    fun notFound(msg: String): Nothing =
        throw com.workeasy.erp.common.BizException(com.workeasy.erp.common.ErrorCode.BIZ_NOT_FOUND, msg)
}
