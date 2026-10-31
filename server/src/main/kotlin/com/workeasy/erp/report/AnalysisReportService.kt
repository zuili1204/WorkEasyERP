package com.workeasy.erp.report

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.ExcelUtil
import com.workeasy.erp.report.mapper.CustomerContributionRow
import com.workeasy.erp.report.mapper.InventoryTurnoverRow
import com.workeasy.erp.report.mapper.ProductMovementMapper
import com.workeasy.erp.report.mapper.ReportMapper
import com.workeasy.erp.report.mapper.SalesProfitRow
import com.workeasy.erp.report.mapper.SlowMovingRow
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 经营分析报表（MyBatis 只读查询）：
 * 库存周转、滞销 Top、客户贡献、销售毛利，并统一提供 xlsx 导出。
 * 商品动销沿用 ProductMovementMapper，保证口径一致。
 */
@Service
class AnalysisReportService(
    private val mapper: ReportMapper,
    private val movementMapper: ProductMovementMapper,
) {

    private data class Period(val from: LocalDate, val to: LocalDate, val days: Int)

    private fun period(from: LocalDate?, to: LocalDate?, defaultDays: Int): Period {
        val end = to ?: LocalDate.now()
        val start = from ?: end.minusDays((defaultDays - 1).toLong())
        if (start.isAfter(end)) throw BizException(ErrorCode.BIZ_PARAM, "开始日期不能晚于结束日期")
        return Period(start, end, ChronoUnit.DAYS.between(start, end).toInt() + 1)
    }

    private fun clampLimit(limit: Int): Int = limit.coerceIn(1, 500)

    fun inventoryTurnover(from: LocalDate?, to: LocalDate?, limit: Int): List<InventoryTurnoverRow> {
        val p = period(from, to, 30)
        return mapper.inventoryTurnover(p.from, p.to, p.days, clampLimit(limit))
    }

    /** 滞销默认观察 90 天，避免 30 天窗口把慢销品误判成滞销 */
    fun slowMoving(from: LocalDate?, to: LocalDate?, limit: Int): List<SlowMovingRow> {
        val p = period(from, to, 90)
        return mapper.slowMoving(p.from, p.to, clampLimit(limit))
    }

    fun customerContribution(from: LocalDate?, to: LocalDate?, limit: Int): List<CustomerContributionRow> {
        val p = period(from, to, 30)
        return mapper.customerContribution(p.from, p.to, clampLimit(limit))
    }

    fun salesProfit(from: LocalDate?, to: LocalDate?, limit: Int): List<SalesProfitRow> {
        val p = period(from, to, 30)
        return mapper.salesProfit(p.from, p.to, clampLimit(limit))
    }

    /** 报表导出：与页面查询共用同一 SQL，保证导出与界面数字一致 */
    fun export(type: String, from: LocalDate?, to: LocalDate?, limit: Int): ByteArray {
        val n = clampLimit(limit)
        return when (type) {
            "product_movement" -> {
                val p = period(from, to, 30)
                val rows = movementMapper.topProducts(p.from, p.to, n)
                ExcelUtil.write(
                    "商品动销",
                    listOf("商品", "规格", "销量", "销售额(不含税)", "毛利", "毛利率"),
                    rows.map { listOf(it.productName, it.spec, it.qty, it.amount, it.profit, it.profitRate) },
                )
            }

            "inventory_turnover" -> {
                val rows = inventoryTurnover(from, to, n)
                ExcelUtil.write(
                    "库存周转",
                    listOf("SKU", "商品", "单位", "期间出库数量", "期间出库成本", "期末库存数量",
                        "期末库存金额", "平均库存金额", "周转次数", "周转天数"),
                    rows.map {
                        listOf(it.sku, it.productName, it.unit, it.outQty, it.outCost, it.stockQty,
                            it.stockAmount, it.avgStockAmount, it.turnoverRate, it.turnoverDays)
                    },
                )
            }

            "slow_moving" -> {
                val rows = slowMoving(from, to, n)
                ExcelUtil.write(
                    "滞销Top",
                    listOf("SKU", "商品", "单位", "期末库存数量", "库存金额", "期间销量", "最后销售出库日", "呆滞天数"),
                    rows.map {
                        listOf(it.sku, it.productName, it.unit, it.stockQty, it.stockAmount,
                            it.salesQty, it.lastOutDate, it.stagnantDays)
                    },
                )
            }

            "customer_contribution" -> {
                val rows = customerContribution(from, to, n)
                ExcelUtil.write(
                    "客户贡献",
                    listOf("客户编号", "客户", "等级", "订单数", "数量", "不含税销售额", "价税合计",
                        "毛利", "毛利率", "应收余额", "逾期应收", "最近下单日"),
                    rows.map {
                        listOf(it.customerCode, it.customerName, it.level, it.orderCount, it.totalQty,
                            it.totalNet, it.totalAmount, it.totalProfit, it.profitRate,
                            it.arBalance, it.overdueAmount, it.lastOrderDate)
                    },
                )
            }

            "sales_profit" -> {
                val rows = salesProfit(from, to, n)
                ExcelUtil.write(
                    "销售毛利",
                    listOf("业务员", "订单数", "不含税销售额", "成本", "毛利", "毛利率"),
                    rows.map {
                        listOf(it.ownerName, it.orderCount, it.totalNet, it.totalCost, it.totalProfit, it.profitRate)
                    },
                )
            }

            else -> throw BizException(ErrorCode.BIZ_PARAM, "不支持的报表类型：$type")
        }
    }
}
