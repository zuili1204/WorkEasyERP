package com.workeasy.erp.common

/**
 * 统一响应体：{ code, msg, data }
 * 列表接口 data 使用 [PageResult]，与原型列表页（搜索/排序/分页）一一对应。
 */
data class ApiResponse<T>(
    val code: String = "0",
    val msg: String = "ok",
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T? = null): ApiResponse<T> = ApiResponse("0", "ok", data)

        fun <T> fail(code: String, msg: String): ApiResponse<T> = ApiResponse(code, msg, null)
    }
}

data class PageResult<T>(
    val list: List<T> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val size: Int = 20,
) {
    companion object {
        fun <T> of(list: List<T>, total: Long, page: Int, size: Int): PageResult<T> =
            PageResult(list, total, page, size)
    }
}
