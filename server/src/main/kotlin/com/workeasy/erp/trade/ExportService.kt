package com.workeasy.erp.trade

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream

/**
 * 报表导出：查询 → 生成 xlsx（表头加粗 + 冻结首行 + 自适应列宽）。
 * 导出内容受调用方的数据范围约束（由各查询自行带 scope 条件）。
 */
private data class ExportDef(val title: String, val headers: List<String>, val sql: String)

private val EXPORTS: Map<String, ExportDef> = mapOf(
    "customers" to ExportDef("客户", listOf("编号", "名称", "等级", "授信额度", "账期(天)", "状态", "归属销售"),
        """
        SELECT c.code, c.name, c.level, c.credit_limit, c.payment_terms, c.status, e.real_name
        FROM customer c LEFT JOIN employee e ON e.id = c.owner_id
        WHERE c.deleted_at IS NULL ORDER BY c.code
        """.trimIndent()),
    "sales_orders" to ExportDef("销售订单", listOf("订单号", "客户", "日期", "数量", "不含税", "税额", "价税合计", "毛利", "状态"),
        """
        SELECT order_no, customer_name, order_date, total_qty, total_net, total_tax, total_amount, total_profit, status
        FROM sales_order WHERE deleted_at IS NULL ORDER BY order_date DESC
        """.trimIndent()),
    "purchase_orders" to ExportDef("采购订单", listOf("订单号", "供应商", "日期", "数量", "不含税", "税额", "价税合计", "状态"),
        """
        SELECT order_no, supplier_name, order_date, total_qty, total_net, total_tax, total_amount, status
        FROM purchase_order WHERE deleted_at IS NULL ORDER BY order_date DESC
        """.trimIndent()),
    "inventory" to ExportDef("库存现量", listOf("SKU", "商品", "仓库", "单位", "现量", "可用", "加权成本", "库存金额"),
        """
        SELECT p.sku, p.name, w.name, p.unit, i.qty, i.available_qty, i.avg_cost, (i.qty * i.avg_cost)
        FROM inventory i JOIN product p ON p.id = i.product_id JOIN warehouse w ON w.id = i.warehouse_id
        ORDER BY p.sku
        """.trimIndent()),
    "ar" to ExportDef("应收台账", listOf("客户", "来源单", "发生额", "已核销", "余额", "到期日", "状态"),
        """
        SELECT c.name, l.biz_no, l.amount, l.settled_amount, l.remain_amount, l.due_date, l.status
        FROM ar_ledger l LEFT JOIN customer c ON c.id = l.customer_id ORDER BY l.created_at DESC
        """.trimIndent()),
    "ap" to ExportDef("应付台账", listOf("供应商", "来源单", "发生额", "已付", "余额", "到期日", "状态"),
        """
        SELECT s.name, l.biz_no, l.amount, l.settled_amount, l.remain_amount, l.due_date, l.status
        FROM ap_ledger l LEFT JOIN supplier s ON s.id = l.supplier_id ORDER BY l.created_at DESC
        """.trimIndent()),
    "inv_txns" to ExportDef("出入库流水", listOf("流水号", "类型", "商品", "数量", "单价", "金额", "结存", "时间"),
        """
        SELECT txn_no, txn_type, product_name, qty, price, amount, balance_qty, created_at
        FROM inventory_txn ORDER BY created_at DESC
        """.trimIndent()),
)

@Service
class ExportService(private val jdbc: JdbcTemplate) {

    val types: List<String> get() = EXPORTS.keys.toList()

    @Transactional(readOnly = true)
    fun export(type: String): ByteArray {
        val def = EXPORTS[type] ?: throw BizException(ErrorCode.BIZ_PARAM, "不支持的导出类型：$type")
        val rows = jdbc.queryForList(def.sql)

        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet(def.title)
            val headStyle = wb.createCellStyle().apply {
                val f = wb.createFont()
                f.bold = true
                setFont(f)
                alignment = HorizontalAlignment.CENTER
            }

            val header = sheet.createRow(0)
            def.headers.forEachIndexed { i, h ->
                val c = header.createCell(i)
                c.setCellValue(h)
                c.cellStyle = headStyle
            }

            rows.forEachIndexed { r, row ->
                val line = sheet.createRow(r + 1)
                def.headers.indices.forEach { i ->
                    val key = row.keys.elementAt(i)
                    line.createCell(i).setCellValue(row[key]?.toString() ?: "")
                }
            }

            def.headers.indices.forEach { sheet.autoSizeColumn(it) }
            sheet.createFreezePane(0, 1)

            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }
}
