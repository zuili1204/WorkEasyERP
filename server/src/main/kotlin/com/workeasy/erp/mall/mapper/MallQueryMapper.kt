package com.workeasy.erp.mall.mapper

import org.apache.ibatis.annotations.Param

/** 商城复杂联表查询（MyBatis XML），简单单表读写仍走 JdbcTemplate */
interface MallQueryMapper {

    /** 上架商品列表：按关键词/分类过滤，带主图与商城属性 */
    fun onlineProducts(
        @Param("q") q: String?,
        @Param("categoryId") categoryId: String?,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<Map<String, Any?>>

    fun countOnlineProducts(
        @Param("q") q: String?,
        @Param("categoryId") categoryId: String?,
    ): Long

    /** 购物车明细：商品快照 + 主图 + 商城约束（价格由 PriceService 统一计算） */
    fun cartItems(@Param("memberId") memberId: String): List<Map<String, Any?>>

    /** 商城订单列表（会员维度） */
    fun orderList(
        @Param("memberId") memberId: String,
        @Param("status") status: String?,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<Map<String, Any?>>

    fun countOrders(@Param("memberId") memberId: String, @Param("status") status: String?): Long

    /** 对账单：应收台账 + 账龄 */
    fun arLedger(@Param("customerId") customerId: String): List<Map<String, Any?>>
}
