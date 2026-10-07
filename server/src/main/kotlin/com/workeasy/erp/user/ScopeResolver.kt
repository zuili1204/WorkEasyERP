package com.workeasy.erp.user

import com.workeasy.erp.config.LoginUser
import org.springframework.stereotype.Component

/**
 * 数据范围求值：对应规划文档 §4.2 + 数据库文档 §16.2.1
 * 顺序：显式 scope 参数 → 职能域收窄 → 角色 data_scope 推导
 */
@Component
class ScopeResolver {

    /** 职能域页面清单（唯一数据源：数据库文档 §16.2.1） */
    private val domain: Map<String, Set<String>> = mapOf(
        "finance" to setOf("ar", "ap", "expense", "invoice", "payment", "writeoff", "sales_ar", "purchase_recon", "payroll"),
        "hr" to setOf(
            "dept", "employee", "hr_entry", "hr_regular", "hr_transfer", "hr_promo", "hr_dimission",
            "hr_contract", "att_shift", "att_schedule", "att_punch", "att_daily", "leave", "overtime",
            "appeal", "outing", "trip", "payroll"
        ),
        "sales" to setOf("lead", "customer", "opportunity", "contract", "sales_order", "sales_outbound", "sales_return", "sales_ar"),
        "purchase" to setOf("purchase_req", "purchase_order", "purchase_inbound", "purchase_return", "purchase_recon"),
        "warehouse" to setOf("warehouse", "inventory", "inv_txn", "stocktake", "batch"),
    )

    fun resolve(requested: String?, me: LoginUser, pageId: String = ""): String {
        if (requested in setOf("mine", "dept", "all")) return requested!!

        // 职能域：跨域页面默认收窄到 mine（软收窄，用户显式传 all 可放开）
        val ownDomain = me.roles.firstNotNullOfOrNull { domain[it] }
        if (ownDomain != null && pageId.isNotBlank() && pageId !in ownDomain) return "mine"

        return when (me.dataScope) {
            "all" -> "all"
            "dept" -> "dept"
            else -> "mine"
        }
    }
}
