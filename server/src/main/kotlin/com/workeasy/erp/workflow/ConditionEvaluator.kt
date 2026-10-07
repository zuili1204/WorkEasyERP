package com.workeasy.erp.workflow

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 条件求值：支持单据表单字段（form_data）上的数值/字符串比较。
 * 未配置条件或字段缺失时返回 false（走「否则」分支），避免误判为成立而漏批。
 */
object ConditionEvaluator {

    private val om = ObjectMapper()

    /**
     * @param condition 条件 JSON，如 {"field":"amount","op":"gte","value":100000}
     * @param data      表单数据（含 amount / total_amount 等业务字段）
     */
    fun eval(condition: String?, data: Map<String, Any?>): Boolean {
        if (condition.isNullOrBlank() || condition == "{}") return false
        return runCatching {
            val j = om.readTree(condition)
            val field = j.path("field").asText("").takeIf { it.isNotBlank() } ?: "amount"
            val op = j.path("op").asText("gte")
            val expect = j.path("value").asText("")
            val actual = data[field]?.toString()
                ?: data["amount"]?.toString()
                ?: data["total_amount"]?.toString()
                ?: return false
            compare(actual, op, expect)
        }.getOrDefault(false)
    }

    private fun compare(actual: String, op: String, expect: String): Boolean {
        val an = actual.toBigDecimalOrNull()
        val en = expect.toBigDecimalOrNull()
        if (an != null && en != null) {
            return when (op) {
                "gte" -> an >= en
                "gt" -> an > en
                "lte" -> an <= en
                "lt" -> an < en
                "eq" -> an.compareTo(en) == 0
                "neq" -> an.compareTo(en) != 0
                else -> false
            }
        }
        return when (op) {
            "eq" -> actual == expect
            "neq" -> actual != expect
            else -> false
        }
    }

    /** 条件节点的可读描述，用于设计器与日志 */
    fun describe(condition: String?): String {
        if (condition.isNullOrBlank() || condition == "{}") return "未配置条件"
        return runCatching {
            val j = om.readTree(condition)
            val field = j.path("field").asText("amount")
            val op = when (j.path("op").asText("gte")) {
                "gt" -> ">"; "gte" -> "≥"; "lt" -> "<"; "lte" -> "≤"
                "eq" -> "="; "neq" -> "≠"; else -> "?"
            }
            "$field $op ${j.path("value").asText("")}"
        }.getOrDefault("条件异常")
    }
}
