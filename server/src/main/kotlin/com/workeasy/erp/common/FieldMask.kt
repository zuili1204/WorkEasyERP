package com.workeasy.erp.common

/**
 * 字段级权限：敏感字段按角色脱敏（服务端过滤，前端隐藏不等于后端放行）。
 *
 * 规则：字段 → 可见角色集合；不在集合内的角色，该字段值统一替换为 `***`。
 * 未列出的字段默认全员可见（白名单式只保护敏感项，避免误伤）。
 */
object FieldPolicy {

    private val RULES: Map<String, Set<String>> = mapOf(
        // 成本（进价 / 加权成本）：仅老板与财务可见
        "cost_price" to setOf("boss", "finance"),
        "cost_amount" to setOf("boss", "finance"),
        "avg_cost" to setOf("boss", "finance"),
        "last_cost" to setOf("boss", "finance"),
        "unit_cost" to setOf("boss", "finance"),
        "stock_amount" to setOf("boss", "finance"),   // 库存金额 = 数量 × 成本
        // 毛利：销售不应看到毛利，避免报价博弈与利润外泄
        "profit" to setOf("boss", "finance"),
        "total_profit" to setOf("boss", "finance"),
        "profit_rate" to setOf("boss", "finance"),
        // 薪资：仅老板、财务、HR 可见
        "net_pay" to setOf("boss", "finance", "hr"),
        "base_salary" to setOf("boss", "finance", "hr"),
        "bonus" to setOf("boss", "finance", "hr"),
        "allowance" to setOf("boss", "finance", "hr"),
        "deduction" to setOf("boss", "finance", "hr"),
        "social_security" to setOf("boss", "finance", "hr"),
        "tax" to setOf("boss", "finance", "hr"),
    )

    const val MASK = "***"

    /** 当前角色不可见的字段清单（供前端隐藏列 / 登录时下发） */
    fun hiddenFields(roles: List<String>): List<String> =
        RULES.filter { (_, allowed) -> roles.none { it in allowed } }.keys.sorted()

    fun maskRow(row: Map<String, Any?>, roles: List<String>): Map<String, Any?> {
        if (roles.any { it == "boss" }) return row
        val hidden = hiddenFields(roles)
        if (hidden.isEmpty()) return row
        return row.mapValues { (k, v) -> if (k in hidden && v != null) MASK else v }
    }

    fun mask(rows: List<Map<String, Any?>>, roles: List<String>): List<Map<String, Any?>> {
        if (roles.any { it == "boss" }) return rows
        val hidden = hiddenFields(roles)
        if (hidden.isEmpty()) return rows
        return rows.map { row ->
            row.mapValues { (k, v) -> if (k in hidden && v != null) MASK else v }
        }
    }
}
