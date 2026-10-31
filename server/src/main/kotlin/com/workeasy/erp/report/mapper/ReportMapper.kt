package com.workeasy.erp.report.mapper

import org.apache.ibatis.annotations.Param
import java.math.BigDecimal
import java.time.LocalDate

/** 库存周转：期间出库成本 / 平均库存成本 */
data class InventoryTurnoverRow(
    val productId: String?,
    val sku: String?,
    val productName: String,
    val unit: String?,
    val outQty: BigDecimal,
    val outCost: BigDecimal,
    val stockQty: BigDecimal,
    val stockAmount: BigDecimal,
    val avgStockAmount: BigDecimal,
    val turnoverRate: BigDecimal,
    val turnoverDays: BigDecimal?,
)

/** 滞销 Top：有库存但期间销量低，按销量升序、库存金额降序 */
data class SlowMovingRow(
    val productId: String?,
    val sku: String?,
    val productName: String,
    val unit: String?,
    val stockQty: BigDecimal,
    val stockAmount: BigDecimal,
    val salesQty: BigDecimal,
    val lastOutDate: LocalDate?,
    val stagnantDays: Int?,
)

/** 客户贡献：期间销售贡献 + 当前应收余额（与 sales_order / ar_ledger 同源） */
data class CustomerContributionRow(
    val customerId: String?,
    val customerCode: String?,
    val customerName: String,
    val level: String?,
    val orderCount: Int,
    val totalQty: BigDecimal,
    val totalNet: BigDecimal,
    val totalAmount: BigDecimal,
    val totalProfit: BigDecimal,
    val profitRate: BigDecimal,
    val arBalance: BigDecimal,
    val overdueAmount: BigDecimal,
    val lastOrderDate: LocalDate?,
)

/** 销售毛利：按业务员汇总，数字全部取自 sales_order 头表 */
data class SalesProfitRow(
    val ownerId: String?,
    val ownerName: String,
    val orderCount: Int,
    val totalNet: BigDecimal,
    val totalCost: BigDecimal,
    val totalProfit: BigDecimal,
    val profitRate: BigDecimal,
)

interface ReportMapper {
    fun inventoryTurnover(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("days") days: Int,
        @Param("limit") limit: Int,
    ): List<InventoryTurnoverRow>

    fun slowMoving(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<SlowMovingRow>

    fun customerContribution(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<CustomerContributionRow>

    fun salesProfit(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<SalesProfitRow>
}
