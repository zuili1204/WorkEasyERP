package com.workeasy.erp.report.mapper

import org.apache.ibatis.annotations.Param
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 商品动销（按商品汇总销量/销售额/毛利）。
 * 只读查询，SQL 见 resources/mapper/report/ProductMovementMapper.xml。
 */
data class ProductMovementRow(
    val productId: String?,
    val productName: String,
    val spec: String?,
    val qty: BigDecimal,
    val amount: BigDecimal,
    val profit: BigDecimal,
    val profitRate: BigDecimal,
)

interface ProductMovementMapper {
    fun topProducts(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("limit") limit: Int,
    ): List<ProductMovementRow>
}
