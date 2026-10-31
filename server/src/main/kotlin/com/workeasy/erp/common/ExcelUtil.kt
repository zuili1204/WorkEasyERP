package com.workeasy.erp.common

import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

/**
 * 统一 xlsx 生成：表头加粗 + 冻结首行 + 自适应列宽。
 * 导出与报表共用，保证各处下载文件的样式一致。
 */
object ExcelUtil {

    fun write(title: String, headers: List<String>, rows: List<List<Any?>>): ByteArray {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet(title)
            val headStyle = wb.createCellStyle().apply {
                val f = wb.createFont()
                f.bold = true
                setFont(f)
                alignment = HorizontalAlignment.CENTER
            }

            val header = sheet.createRow(0)
            headers.forEachIndexed { i, h ->
                val c = header.createCell(i)
                c.setCellValue(h)
                c.cellStyle = headStyle
            }

            rows.forEachIndexed { r, row ->
                val line = sheet.createRow(r + 1)
                headers.indices.forEach { i -> line.createCell(i).setCellValue(row.getOrNull(i)?.toString() ?: "") }
            }

            headers.indices.forEach { sheet.autoSizeColumn(it) }
            sheet.createFreezePane(0, 1)

            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }
}
