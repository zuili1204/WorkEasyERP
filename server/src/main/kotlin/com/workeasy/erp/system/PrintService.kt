package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.UserContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PrintLine(
    val name: String,
    val spec: String? = null,
    val unit: String? = null,
    val qty: String,
    val price: String,
    val amount: String,
    val remark: String? = null,
)

/** 打印数据：前端据此渲染 A4 打印页，后端据此生成 PDF */
data class PrintBill(
    val title: String,
    val kind: String,
    val no: String,
    val partyLabel: String,
    val partyName: String,
    val date: String,
    val meta: List<List<String>>,
    val items: List<PrintLine>,
    val totalQty: String,
    val totalAmount: String,
    val amountUpper: String,
    val remark: String?,
    val printedBy: String,
    val printedAt: String,
    val company: String,
)

private val BILL_TYPES = setOf("sales_order", "purchase_order", "purchase_inbound", "sales_outbound", "payment")

/** 单据打印：统一取数 → 渲染 PDF（A4 + 中文字体） */
@Service
class PrintService(private val jdbc: JdbcTemplate) {

    private val moneyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    @Transactional(readOnly = true)
    fun bill(type: String, id: String): PrintBill {
        if (type !in BILL_TYPES) throw BizException(ErrorCode.BIZ_PARAM, "不支持打印的单据类型：$type")
        val me = UserContext.get()
        val (title, partyLabel, partyName, no, date, meta, items, totalQty, totalAmount, remark) = load(type, id)

        return PrintBill(
            title = title,
            kind = type,
            no = no,
            partyLabel = partyLabel,
            partyName = partyName,
            date = date,
            meta = meta,
            items = items,
            totalQty = totalQty,
            totalAmount = totalAmount,
            amountUpper = MoneyUpper.toUpper(bdOf(totalAmount)),
            remark = remark,
            printedBy = me.displayName,
            printedAt = LocalDateTime.now().format(moneyFmt),
            company = "WorkEasy ERP 演示公司",
        )
    }

    // ---------- 取数 ----------

    @Suppress("LongMethod")
    private fun load(type: String, id: String): BillData {
        return when (type) {
            "sales_order" -> {
                val h = head("sales_order", id)
                val w = h["warehouse_id"]?.toString()?.let { nameOf("warehouse", it) } ?: "—"
                val items = jdbc.queryForList(
                    "SELECT product_name, spec, unit, qty, price, amount, remark FROM sales_order_item WHERE order_id = ?::uuid ORDER BY line_no",
                    id,
                )
                BillData(
                    "销售订单", "客户", s(h["customer_name"]), s(h["order_no"]), s(h["order_date"]),
                    listOf(
                        listOf("仓库", w),
                        listOf("账期", "${s(h["credit_days"])} 天"),
                        listOf("状态", statusText(s(h["status"]))),
                        listOf("不含税", money(s(h["total_net"]))),
                        listOf("税额", money(s(h["total_tax"]))),
                        listOf("毛利", money(s(h["total_profit"]))),
                    ),
                    items.toLines(), money(s(h["total_qty"])), money(s(h["total_amount"])), s(h["remark"]),
                )
            }

            "purchase_order" -> {
                val h = head("purchase_order", id)
                val w = h["warehouse_id"]?.toString()?.let { nameOf("warehouse", it) } ?: "—"
                val items = jdbc.queryForList(
                    "SELECT product_name, spec, unit, qty, price, amount, remark FROM purchase_order_item WHERE order_id = ?::uuid ORDER BY line_no",
                    id,
                )
                BillData(
                    "采购订单", "供应商", s(h["supplier_name"]), s(h["order_no"]), s(h["order_date"]),
                    listOf(
                        listOf("仓库", w),
                        listOf("状态", statusText(s(h["status"]))),
                        listOf("不含税", money(s(h["total_net"]))),
                        listOf("税额", money(s(h["total_tax"]))),
                    ),
                    items.toLines(), money(s(h["total_qty"])), money(s(h["total_amount"])), s(h["remark"]),
                )
            }

            "purchase_inbound" -> {
                val h = head("purchase_inbound", id)
                val orderNo = h["order_id"]?.toString()?.let { noOf("purchase_order", it, "order_no") } ?: "—"
                val items = jdbc.queryForList(
                    "SELECT product_name, unit, qty, price, amount, remark FROM purchase_inbound_item WHERE inbound_id = ?::uuid",
                    id,
                )
                BillData(
                    "采购入库单", "供应商", s(h["supplier_name"]), s(h["inbound_no"]), s(h["inbound_date"]),
                    listOf(
                        listOf("关联订单", orderNo),
                        listOf("仓库", nameOf("warehouse", s(h["warehouse_id"]))),
                        listOf("状态", statusText(s(h["status"]))),
                    ),
                    items.map {
                        PrintLine(
                            s(it["product_name"]), null, s(it["unit"]),
                            money(s(it["qty"])), money(s(it["price"])), money(s(it["amount"])), s(it["remark"]),
                        )
                    },
                    money(s(h["total_qty"])), money(s(h["total_amount"])), s(h["remark"]),
                )
            }

            "sales_outbound" -> {
                val h = head("sales_outbound", id)
                val orderNo = h["order_id"]?.toString()?.let { noOf("sales_order", it, "order_no") } ?: "—"
                val items = jdbc.queryForList(
                    "SELECT product_name, unit, qty, price, amount, remark FROM sales_outbound_item WHERE outbound_id = ?::uuid",
                    id,
                )
                BillData(
                    "销售出库单", "客户", s(h["customer_name"]), s(h["outbound_no"]), s(h["outbound_date"]),
                    listOf(
                        listOf("关联订单", orderNo),
                        listOf("仓库", nameOf("warehouse", s(h["warehouse_id"]))),
                        listOf("状态", statusText(s(h["status"]))),
                    ),
                    items.map {
                        PrintLine(
                            s(it["product_name"]), null, s(it["unit"]),
                            money(s(it["qty"])), money(s(it["price"])), money(s(it["amount"])), s(it["remark"]),
                        )
                    },
                    money(s(h["total_qty"])), money(s(h["total_amount"])), s(h["remark"]),
                )
            }

            else -> { // payment
                val h = head("payment", id)
                BillData(
                    if (s(h["type"]) == "receive") "收款单" else "付款单",
                    if (s(h["counterparty_type"]) == "supplier") "供应商" else "客户",
                    s(h["counterparty_name"]), s(h["payment_no"]), s(h["pay_date"]),
                    listOf(
                        listOf("收付类型", if (s(h["type"]) == "receive") "收款" else "付款"),
                        listOf("结算方式", payMethodText(s(h["pay_method"]))),
                        listOf("币种", s(h["currency"])),
                        listOf("状态", statusText(s(h["status"]))),
                    ),
                    emptyList(), "—", money(s(h["amount"])), s(h["remark"]),
                )
            }
        }
    }

    private data class BillData(
        val title: String,
        val partyLabel: String,
        val partyName: String,
        val no: String,
        val date: String,
        val meta: List<List<String>>,
        val items: List<PrintLine>,
        val totalQty: String,
        val totalAmount: String,
        val remark: String?,
    )

    private fun List<Map<String, Any?>>.toLines(): List<PrintLine> = map {
        PrintLine(
            s(it["product_name"]), s(it["spec"]), s(it["unit"]),
            money(s(it["qty"])), money(s(it["price"])), money(s(it["amount"])), s(it["remark"]),
        )
    }

    private fun head(table: String, id: String): Map<String, Any?> {
        val rows = jdbc.queryForList("SELECT * FROM $table WHERE id = ?::uuid", id)
        if (rows.isEmpty()) throw BizException(ErrorCode.BIZ_NOT_FOUND, "单据不存在：$id")
        return rows.first()
    }

    private fun nameOf(table: String, id: String): String = runCatching {
        jdbc.queryForObject("SELECT name FROM $table WHERE id = ?::uuid", String::class.java, id)
    }.getOrNull() ?: "—"

    private fun noOf(table: String, id: String, col: String): String = runCatching {
        jdbc.queryForObject("SELECT $col FROM $table WHERE id = ?::uuid", String::class.java, id)
    }.getOrNull() ?: "—"

    private fun s(v: Any?): String = v?.toString() ?: "—"

    private fun money(v: String): String {
        val b = runCatching { BigDecimal(v) }.getOrNull() ?: return v
        return "%,.2f".format(b)
    }

    private fun bdOf(v: String): BigDecimal =
        runCatching { BigDecimal(v.replace(",", "")) }.getOrDefault(BigDecimal.ZERO)

    private fun statusText(s: String): String = mapOf(
        "draft" to "草稿", "approving" to "审批中", "approved" to "已审批", "rejected" to "已驳回",
        "partial" to "部分执行", "received" to "已收货", "shipped" to "已发货", "closed" to "已关闭",
        "void" to "已作废", "pending" to "待审核", "audited" to "已审核", "done" to "已完成",
    )[s] ?: s

    private fun payMethodText(s: String): String = mapOf(
        "cash" to "现金", "bank" to "银行转账", "acceptance" to "承兑汇票",
    )[s] ?: s

    // ---------- PDF 渲染 ----------

    /**
     * 生成 A4 PDF：标题 + 抬头信息 + 明细表 + 合计大写 + 签署栏。
     * 中文字体取系统字体（Windows 黑体优先），字体缺失时退化为英文已无意义，故直接抛错提示。
     */
    fun pdf(type: String, id: String): ByteArray {
        val bill = bill(type, id)
        val fontFile = ChineseFont.locate() ?: throw BizException(ErrorCode.SYS_ERROR, "未找到中文字体，无法生成 PDF")

        PDDocument().use { doc ->
            val font = PDType0Font.load(doc, fontFile)
            val bold = font
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = PDRectangle.A4.height - 50f

            // 标题
            cs.setFont(bold, 18f)
            cs.drawCentered(bill.title, PDRectangle.A4.width / 2, y, font, 18f)
            y -= 16f
            cs.setFont(font, 9f)
            cs.drawCentered(bill.company, PDRectangle.A4.width / 2, y, font, 9f)
            y -= 28f

            // 抬头：左侧往来单位，右侧单号/日期
            val leftX = 50f
            val rightX = 330f
            cs.setFont(font, 10f)
            cs.drawTextAt("${bill.partyLabel}：$bill.partyName", leftX, y)
            cs.drawTextAt("单据编号：${bill.no}", rightX, y)
            y -= 16f
            cs.drawTextAt("打印时间：${bill.printedAt}", leftX, y)
            cs.drawTextAt("业务日期：${bill.date}", rightX, y)
            y -= 16f

            // 补充信息（两列）
            var col = 0
            bill.meta.forEach { (label, value) ->
                val x = if (col % 2 == 0) leftX else rightX
                cs.drawTextAt("$label：$value", x, y)
                if (col % 2 == 1) y -= 15f
                col++
            }
            if (col % 2 == 1) y -= 15f
            y -= 12f

            // 明细表：仅在有明细时渲染
            if (bill.items.isNotEmpty()) {
                val cols = listOf("商品名称", "单位", "数量", "单价", "金额")
                val xs = listOf(50f, 250f, 300f, 370f, 450f)
                val rightEdge = 545f

                cs.drawLine(50f, y, rightEdge, y, font)
                y -= 4f
                cs.setFont(bold, 10f)
                cols.forEachIndexed { i, c -> cs.drawTextAt(c, xs[i], y - 12f) }
                y -= 20f
                cs.setFont(font, 9.5f)
                cs.drawLine(50f, y, rightEdge, y, font)
                y -= 4f

                bill.items.forEach { line ->
                    if (y < 150f) { // 换页
                        cs.drawLine(50f, y, rightEdge, y, font)
                        cs.close()
                        page = PDPage(PDRectangle.A4)
                        doc.addPage(page)
                        cs = PDPageContentStream(doc, page)
                        y = PDRectangle.A4.height - 60f
                        cs.drawLine(50f, y, rightEdge, y, font)
                        y -= 4f
                        cs.setFont(bold, 10f)
                        cols.forEachIndexed { i, c -> cs.drawTextAt(c, xs[i], y - 12f) }
                        y -= 20f
                        cs.setFont(font, 9.5f)
                        cs.drawLine(50f, y, rightEdge, y, font)
                        y -= 4f
                    }
                    y -= 16f
                    cs.drawTextAt(line.name.take(20), xs[0], y)
                    cs.drawTextAt(line.unit ?: "", xs[1], y)
                    cs.drawTextAt(line.qty, xs[2], y)
                    cs.drawTextAt(line.price, xs[3], y)
                    cs.drawTextAt(line.amount, xs[4], y)
                }
                y -= 6f
                cs.drawLine(50f, y, rightEdge, y, font)
                y -= 20f
                cs.setFont(bold, 10f)
                cs.drawTextAt("合计数量：${bill.totalQty}", xs[0], y)
                cs.drawTextAt("价税合计：${bill.totalAmount}", xs[3], y)
                y -= 20f
            } else {
                cs.setFont(bold, 12f)
                cs.drawTextAt("金额：${bill.totalAmount}", leftX, y)
                y -= 20f
            }

            cs.setFont(font, 10f)
            cs.drawTextAt("金额大写：${bill.amountUpper}", leftX, y)
            y -= 18f
            if (!bill.remark.isNullOrBlank()) {
                cs.drawTextAt("备注：${bill.remark.take(60)}", leftX, y)
                y -= 18f
            }

            // 签署栏
            val signY = 100f
            cs.drawTextAt("制单人：${bill.printedBy}", 50f, signY)
            cs.drawTextAt("审核人：____________", 200f, signY)
            cs.drawTextAt("签收人：____________", 380f, signY)
            cs.setFont(font, 8f)
            cs.drawTextAt("本单据由 WorkEasy ERP 生成 · ${bill.printedAt}", 50f, 70f)

            cs.close()
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    // ---------- PDF 绘制辅助 ----------

    private fun PDPageContentStream.drawTextAt(text: String, x: Float, y: Float) {
        beginText()
        newLineAtOffset(x, y)
        showText(text)
        endText()
    }

    private fun PDPageContentStream.drawCentered(
        text: String, cx: Float, y: Float, font: PDType0Font, size: Float,
    ) {
        val w = font.getStringWidth(text) / 1000f * size
        drawTextAt(text, cx - w / 2, y)
    }

    private fun PDPageContentStream.drawLine(x1: Float, y1: Float, x2: Float, y2: Float, @Suppress("UNUSED_PARAMETER") font: PDType0Font) {
        saveGraphicsState()
        setLineWidth(0.5f)
        moveTo(x1, y1)
        lineTo(x2, y2)
        stroke()
        restoreGraphicsState()
    }
}

/** 中文字体定位：优先纯 TTF 黑体，避免 TTC 集合在个别环境加载失败 */
object ChineseFont {
    private val CANDIDATES = listOf(
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/simfang.ttf",
        "C:/Windows/Fonts/simkai.ttf",
        "/usr/share/fonts/truetype/arphic/uming.ttc",
        "/System/Library/Fonts/PingFang.ttc",
    )

    fun locate(): File? = CANDIDATES.map { File(it) }.firstOrNull { it.exists() && it.length() > 0 }
}

/** 人民币金额大写 */
object MoneyUpper {
    private val DIGITS = arrayOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
    private val UNITS = arrayOf("", "拾", "佰", "仟")
    private val SECTIONS = arrayOf("", "万", "亿", "万亿")

    fun toUpper(amount: BigDecimal): String {
        if (amount.compareTo(BigDecimal.ZERO) == 0) return "零元整"
        val neg = amount < BigDecimal.ZERO
        val cents = amount.abs().multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        val yuan = cents / 100
        val jiao = (cents % 100) / 10
        val fen = cents % 10

        val sb = StringBuilder()
        if (yuan > 0L) {
            sb.append(sectionAll(yuan)).append("元")
            if (jiao == 0L && fen == 0L) sb.append("整")
        }
        if (jiao == 0L && fen != 0L) sb.append("零")
        if (jiao != 0L) sb.append(DIGITS[jiao.toInt()]).append("角")
        if (fen != 0L) sb.append(DIGITS[fen.toInt()]).append("分")
        if (yuan == 0L && jiao == 0L && fen == 0L) sb.append("零元整")

        return (if (neg) "负" else "") + sb.toString()
    }

    private fun sectionAll(yuan: Long): String {
        val out = StringBuilder()
        var rest = yuan
        var idx = 0
        while (rest > 0) {
            val sec = (rest % 10000).toInt()
            val secText = section(sec)
            if (secText.isNotEmpty()) {
                out.insert(0, secText + SECTIONS[idx])
            } else if (out.isNotEmpty() && !out.startsWith("零")) {
                out.insert(0, "零")
            }
            rest /= 10000
            idx++
        }
        return out.toString().trim('零').ifEmpty { "零" }
    }

    private fun section(sec: Int): String {
        if (sec == 0) return ""
        val sb = StringBuilder()
        val ds = sec.toString().padStart(4, '0')
        var zero = false
        for (i in 0..3) {
            val d = ds[i].digitToInt()
            val unit = UNITS[3 - i]
            if (d == 0) zero = true
            else {
                if (zero && sb.isNotEmpty()) sb.append("零")
                zero = false
                sb.append(DIGITS[d]).append(unit)
            }
        }
        return sb.toString()
    }
}
