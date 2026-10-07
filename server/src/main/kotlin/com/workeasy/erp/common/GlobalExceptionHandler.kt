package com.workeasy.erp.common

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BizException::class)
    fun handleBiz(e: BizException): ResponseEntity<ApiResponse<Unit>> {
        val status = when {
            e.errCode.startsWith("AUTH_401") -> HttpStatus.UNAUTHORIZED
            e.errCode.startsWith("AUTH_403") -> HttpStatus.FORBIDDEN
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(e.errCode, e.message ?: ""))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val msg = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "参数校验失败"
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BIZ_PARAM.code, msg))
    }

    /**
     * 数据约束冲突：多为调用方传入非法 ID / 重复数据 / 关联不存在，
     * 属于 400 参数问题，不应暴露为 500（避免前端看到"系统异常"却不知错在哪）。
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrity(e: DataIntegrityViolationException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("数据约束冲突：{}", e.message)
        val msg = when {
            e.message?.contains("invalid input syntax for type uuid") == true -> "参数中的 ID 为空或格式不正确"
            e.message?.contains("violates unique constraint") == true -> "数据重复，违反唯一约束"
            e.message?.contains("violates foreign key constraint") == true -> "关联数据不存在或正被引用"
            e.message?.contains("null value in column") == true -> "必填字段为空"
            else -> "数据校验未通过"
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BIZ_PARAM.code, msg))
    }

    /**
     * 请求体序列化失败（日期格式不对、类型不匹配等）属于调用方问题，
     * 按 400 参数错误返回并给出字段线索，避免笼统报 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        val field = e.message?.let { msg ->
            Regex("of type `([^`]+)`").find(msg)?.groupValues?.getOrNull(1)?.substringAfterLast('.')
        }
        // 不回显 Java/Kotlin 类型名：映射为业务侧可读的字段名，避免技术细节外泄
        val label = when {
            field == null -> null
            field.contains("Instant") || field.contains("LocalDate") -> "日期时间"
            field.contains("UUID") -> "ID"
            field.contains("BigDecimal") || field.contains("Number") || field.contains("Int") -> "数值"
            field.contains("Boolean") -> "布尔值"
            else -> "字段"
        }
        val msg = when {
            label == null -> "请求参数格式不正确"
            label == "日期时间" -> "日期时间格式不正确（如 2026-11-03T00:00:00Z）"
            label == "ID" -> "ID 格式不正确或为空"
            label == "数值" -> "该字段需为数字"
            label == "布尔值" -> "该字段需为 true / false"
            else -> "参数格式不正确，请检查字段名与类型"
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BIZ_PARAM.code, msg))
    }

    @ExceptionHandler(Exception::class)
    fun handleOther(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("未处理异常", e)
        // 不向前端暴露堆栈
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(ErrorCode.SYS_ERROR.code, ErrorCode.SYS_ERROR.msg))
    }
}
