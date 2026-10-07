package com.workeasy.erp.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import com.workeasy.erp.config.LoginUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 审批人规则求值（M0 首版：角色 + 同部门优先；金额/天数阈值与可视化配置器放 M3）
 * 规则示例：{"role":"manager","sameDept":true} / {"role":"hr"}
 */
@Component
class ApproverResolver(private val jdbc: JdbcTemplate) {

    private val om = ObjectMapper()

    fun resolve(ruleJson: String, initiator: LoginUser): List<UUID> {
        val rule = runCatching { om.readValue(ruleJson, Map::class.java) as Map<*, *> }.getOrNull()
            ?: return emptyList()
        val role = rule["role"]?.toString() ?: return emptyList()
        val sameDept = rule["sameDept"]?.toString()?.toBoolean() ?: false

        val base = """
            SELECT u.id FROM users u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN role r ON r.id = ur.role_id
            LEFT JOIN employee e ON e.id = u.employee_id
            WHERE r.code = ? AND u.deleted_at IS NULL AND u.status = 'active'
        """.trimIndent()

        // 优先本部门同角色；找不到再退化为该角色任意用户
        val inDept = if (sameDept && initiator.deptId != null) {
            jdbc.queryForList(
                "$base AND e.department_id = ?::uuid LIMIT 5",
                String::class.java,
                role,
                initiator.deptId.toString(),
            )
        } else {
            emptyList()
        }

        val ids = if (inDept.isNotEmpty()) {
            inDept
        } else {
            jdbc.queryForList("$base LIMIT 5", String::class.java, role)
        }

        return ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
