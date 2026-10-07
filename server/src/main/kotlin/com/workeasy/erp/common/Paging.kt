package com.workeasy.erp.common

/**
 * 入参钳制：防止异常分页（size=100000）拖垮数据库、超长关键词拖慢 ILIKE 查询。
 * 各列表服务在查询前统一调用，避免每个方法各写一遍校验。
 */
object Paging {
    const val MAX_SIZE = 100
    private const val MAX_Q = 100

    /** 返回 (page, size)，保证 page >= 1、1 <= size <= 100 */
    fun clamp(page: Int, size: Int): Pair<Int, Int> =
        page.coerceAtLeast(1) to size.coerceIn(1, MAX_SIZE)

    fun clampPage(page: Int): Int = page.coerceAtLeast(1)

    fun clampSize(size: Int): Int = size.coerceIn(1, MAX_SIZE)

    /** 搜索词去空并限长 */
    fun keyword(q: String?): String? = q?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_Q)
}
