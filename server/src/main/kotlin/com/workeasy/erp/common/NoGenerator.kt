package com.workeasy.erp.common

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 单据编号服务：{prefix}{yyyyMMdd}{seq}
 * 并发安全：依赖 doc_no_seq 主键 + ON CONFLICT 原子自增，禁止应用内存自增（多实例会重复）。
 */
@Service
class NoGenerator(private val jdbc: JdbcTemplate) {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun next(prefix: String, date: LocalDate = LocalDate.now()): String {
        val day = date.format(fmt)
        val seq = jdbc.queryForObject(
            """
            INSERT INTO doc_no_seq(prefix, day, seq) VALUES (?, ?, 1)
            ON CONFLICT(prefix, day) DO UPDATE SET seq = doc_no_seq.seq + 1
            RETURNING seq
            """.trimIndent(),
            Long::class.java,
            prefix,
            day,
        ) ?: throw BizException(ErrorCode.SYS_ERROR, "编号生成失败")
        return "$prefix$day${seq.toString().padStart(4, '0')}"
    }
}
