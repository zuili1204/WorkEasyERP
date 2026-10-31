package com.workeasy.erp.report

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.report.mapper.ProductMovementMapper
import com.workeasy.erp.report.mapper.ProductMovementRow
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 商品动销报表（MyBatis 试点）：只读查询，不参与写事务。
 */
@Service
class ProductMovementService(private val mapper: ProductMovementMapper) {

    fun top(from: LocalDate?, to: LocalDate?, limit: Int): List<ProductMovementRow> {
        val end = to ?: LocalDate.now()
        val start = from ?: end.minusDays(29)
        if (start.isAfter(end)) throw BizException(ErrorCode.BIZ_PARAM, "开始日期不能晚于结束日期")
        return mapper.topProducts(start, end, limit.coerceIn(1, 100))
    }
}
