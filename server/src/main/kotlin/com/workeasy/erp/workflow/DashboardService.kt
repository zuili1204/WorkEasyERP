package com.workeasy.erp.workflow

import com.workeasy.erp.config.LoginUser
import com.workeasy.erp.user.ScopeResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class OverviewCard(val label: String, val value: String, val sub: String = "", val tone: String = "indigo")

data class RecentItem(
    val id: String,
    val title: String?,
    val bizType: String?,
    val status: String,
    val createdAt: String?,
)

data class TrendPoint(
    val month: String,
    val sales: String,
    val profit: String,
    val salesRaw: Double = 0.0,
    val profitRaw: Double = 0.0,
)

data class TopItem(val name: String, val amount: String)

data class AlertItem(val kind: String, val title: String, val detail: String)

data class DashboardOverview(
    val cards: List<OverviewCard>,
    val myTodo: Long,
    val unread: Long,
    val recent: List<RecentItem>,
    val trend: List<TrendPoint> = emptyList(),
    val topCustomers: List<TopItem> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
)

/**
 * 工作台 / 老板看板概览。
 * M0~M1 阶段基于已有表聚合；M2 销售/库存/财务表就位后，
 * 再接入《数据库表结构设计.md》§14 的 v_boss_dashboard 视图。
 */
@Service
class DashboardService(
    private val jdbc: JdbcTemplate,
    private val scopeResolver: ScopeResolver,
) {

    private val ZERO = UUID(0, 0).toString()

    fun overview(me: LoginUser): DashboardOverview {
        val scope = scopeResolver.resolve(null, me, "dashboard")

        // 员工 / 部门
        val empArgs = mutableListOf<Any>()
        val empCond = StringBuilder("WHERE e.deleted_at IS NULL ")
        if (scope == "dept") {
            empCond.append("AND e.department_id = ?::uuid ")
            empArgs.add(me.deptId?.toString() ?: ZERO)
        }
        val empCount = count("SELECT count(*) FROM employee e $empCond", *empArgs.toTypedArray())
        val activeCount = count(
            "SELECT count(*) FROM employee e $empCond AND e.status = 'active'",
            *empArgs.toTypedArray(),
        )
        val deptCount = count("SELECT count(*) FROM department WHERE deleted_at IS NULL")

        // ---- 经营指标（M2 订单/库存/台账就位后接入）----
        val salesCond = when (scope) {
            "mine" -> "AND (SELECT owner_id FROM sales_order x WHERE x.id = sales_order.id) = '${me.employeeId ?: ZERO}'::uuid "
            "dept" -> "AND dept_id = '${me.deptId ?: ZERO}'::uuid "
            else -> ""
        }
        val monthSales = sum(
            """
            SELECT COALESCE(SUM(total_amount),0) FROM sales_order
            WHERE deleted_at IS NULL $salesCond
              AND date_trunc('month', order_date) = date_trunc('month', now())
            """.trimIndent(),
        )
        val monthProfit = sum(
            """
            SELECT COALESCE(SUM(total_profit),0) FROM sales_order
            WHERE deleted_at IS NULL $salesCond
              AND date_trunc('month', order_date) = date_trunc('month', now())
            """.trimIndent(),
        )
        val arRemain = sum("SELECT COALESCE(SUM(remain_amount),0) FROM ar_ledger WHERE status <> 'closed'")
        val apRemain = sum("SELECT COALESCE(SUM(remain_amount),0) FROM ap_ledger WHERE status <> 'closed'")
        val stockAmount = sum("SELECT COALESCE(SUM(qty * avg_cost),0) FROM inventory")
        val lowStock = count("SELECT count(*) FROM inventory WHERE safety_stock > 0 AND qty <= safety_stock")

        // 待办与消息
        val myTodo = count(
            "SELECT count(*) FROM workflow_task WHERE assignee_id = ?::uuid AND status = 'pending'",
            me.userId.toString(),
        )
        val unread = count(
            "SELECT count(*) FROM notification_recipient WHERE user_id = ?::uuid AND status = 'unread'",
            me.userId.toString(),
        )

        // 请假（按数据范围）
        val lvArgs = mutableListOf<Any>()
        val lvCond = StringBuilder("WHERE 1=1 ")
        when (scope) {
            "mine" -> {
                lvCond.append("AND l.employee_id = ?::uuid ")
                lvArgs.add(me.employeeId?.toString() ?: ZERO)
            }
            "dept" -> {
                lvCond.append(
                    "AND EXISTS (SELECT 1 FROM employee e WHERE e.id = l.employee_id AND e.department_id = ?::uuid) "
                )
                lvArgs.add(me.deptId?.toString() ?: ZERO)
            }
        }
        val leavePending = count(
            "SELECT count(*) FROM leave_request l $lvCond AND l.status = 'pending'",
            *lvArgs.toTypedArray(),
        )
        val leaveMonth = count(
            "SELECT count(*) FROM leave_request l $lvCond AND date_trunc('month', l.created_at) = date_trunc('month', now())",
            *lvArgs.toTypedArray(),
        )

        // 与我相关的流程（我发起的 或 我审批过的）
        val recentRows = jdbc.queryForList(
            """
            SELECT i.id, i.title, i.biz_type, i.status, i.created_at
            FROM workflow_instance i
            WHERE i.initiator_id = ?::uuid
               OR EXISTS (SELECT 1 FROM workflow_task t WHERE t.instance_id = i.id AND t.assignee_id = ?::uuid)
            ORDER BY i.created_at DESC
            LIMIT 6
            """.trimIndent(),
            me.userId.toString(),
            me.userId.toString(),
        )

        // ---- 近 6 月营收 / 毛利趋势 ----
        val trend: List<TrendPoint> = jdbc.queryForList(
            """
            SELECT to_char(date_trunc('month', order_date), 'YYYY-MM') AS m,
                   COALESCE(SUM(total_amount), 0) AS sales,
                   COALESCE(SUM(total_profit), 0) AS profit
            FROM sales_order
            WHERE deleted_at IS NULL $salesCond
              AND order_date >= date_trunc('month', now()) - interval '5 months'
            GROUP BY 1 ORDER BY 1
            """.trimIndent()
        ).map {
            TrendPoint(
                month = it["m"]?.toString() ?: "",
                sales = money(bd(it["sales"])),
                profit = money(bd(it["profit"])),
                salesRaw = bd(it["sales"]).toDouble(),
                profitRaw = bd(it["profit"]).toDouble(),
            )
        }

        // ---- 应收余额 Top5 客户 ----
        val topCustomers: List<TopItem> = jdbc.queryForList(
            """
            SELECT c.name AS name, COALESCE(SUM(l.remain_amount), 0) AS amount
            FROM ar_ledger l JOIN customer c ON c.id = l.customer_id
            WHERE l.status <> 'closed'
            GROUP BY c.name ORDER BY amount DESC LIMIT 5
            """.trimIndent()
        ).map { TopItem(it["name"]?.toString() ?: "—", money(bd(it["amount"]))) }

        // ---- 预警：低库存 / 批次效期 / 应收逾期 ----
        val alerts = mutableListOf<AlertItem>()
        jdbc.queryForList(
            """
            SELECT p.name AS name, i.qty, i.safety_stock
            FROM inventory i JOIN product p ON p.id = i.product_id
            WHERE i.safety_stock > 0 AND i.qty <= i.safety_stock
            """.trimIndent()
        ).forEach {
            alerts += AlertItem("stock", "库存低于安全库存：${it["name"]}", "现量 ${it["qty"]} / 安全 ${it["safety_stock"]}")
        }
        jdbc.queryForList(
            """
            SELECT batch_no, product_name, expire_date
            FROM inventory_batch
            WHERE expire_date IS NOT NULL AND expire_date <= CURRENT_DATE + 30
            ORDER BY expire_date LIMIT 5
            """.trimIndent()
        ).forEach {
            alerts += AlertItem("expire", "批次即将到期：${it["batch_no"]} ${it["product_name"]}", "到期 ${it["expire_date"]}")
        }
        jdbc.queryForList(
            """
            SELECT c.name AS name, COUNT(*) AS cnt, COALESCE(SUM(l.remain_amount), 0) AS amount
            FROM ar_ledger l JOIN customer c ON c.id = l.customer_id
            WHERE l.status <> 'closed' AND l.due_date < CURRENT_DATE
            GROUP BY c.name
            """.trimIndent()
        ).forEach {
            alerts += AlertItem("overdue", "应收逾期：${it["name"]}", "${it["cnt"]} 笔，共 ${money(bd(it["amount"]))}")
        }

        return DashboardOverview(
            trend = trend,
            topCustomers = topCustomers,
            alerts = alerts,
            cards = listOf(
                OverviewCard("员工总数", empCount.toString(), "在职 $activeCount 人", "blue"),
                OverviewCard("部门数", deptCount.toString(), "组织架构", "indigo"),
                OverviewCard("待我审批", myTodo.toString(), "待办中心", "orange"),
                OverviewCard("未读消息", unread.toString(), "消息中心", "green"),
                OverviewCard("请假待审批", leavePending.toString(), "本月提交 $leaveMonth 单", "red"),
                OverviewCard("本月营收", money(monthSales), "销售订单价税合计", "blue"),
                OverviewCard("本月毛利", money(monthProfit), "营收 − 成本", "green"),
                OverviewCard("应收余额", money(arRemain), "未结清应收", "orange"),
                OverviewCard("应付余额", money(apRemain), "未结清应付", "red"),
                OverviewCard("库存金额", money(stockAmount), "低库存预警 $lowStock 项", "indigo"),
            ),
            myTodo = myTodo,
            unread = unread,
            recent = recentRows.map {
                RecentItem(
                    id = it["id"].toString(),
                    title = it["title"]?.toString(),
                    bizType = it["biz_type"]?.toString(),
                    status = it["status"]?.toString() ?: "running",
                    createdAt = it["created_at"]?.toString(),
                )
            },
        )
    }

    private fun count(sql: String, vararg args: Any): Long =
        jdbc.queryForObject(sql, Long::class.java, *args) ?: 0

    private fun sum(sql: String, vararg args: Any): BigDecimal =
        jdbc.queryForObject(sql, BigDecimal::class.java, *args) ?: BigDecimal.ZERO

    private fun bd(v: Any?): BigDecimal = when (v) {
        null -> BigDecimal.ZERO
        is BigDecimal -> v
        else -> runCatching { BigDecimal(v.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    private fun money(v: BigDecimal): String {
        val wan = BigDecimal("10000")
        return if (v >= wan) {
            "${v.divide(wan, 1, RoundingMode.HALF_UP)} 万"
        } else {
            v.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
    }
}
