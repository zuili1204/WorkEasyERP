package com.workeasy.erp.system

import com.workeasy.erp.config.LoginUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 个人中心：档案 + 角色 + 部门 + 个人数据统计（严格 self 范围） */
@Service
class ProfileService(private val jdbc: JdbcTemplate) {

    @Transactional(readOnly = true)
    fun profile(me: LoginUser): Map<String, Any?> {
        val uid = me.userId.toString()
        val empId = me.employeeId?.toString()

        val emp = if (empId == null) {
            null
        } else {
            jdbc.queryForMap(
                """
                SELECT e.employee_no, e.real_name, e.position, e.job_title, e.hire_date,
                       e.entry_status, e.status, e.phone, e.email,
                       d.name AS dept_name
                FROM employee e LEFT JOIN department d ON d.id = e.department_id
                WHERE e.id = ?::uuid
                """.trimIndent(),
                empId,
            ).mapValues { (_, v) -> v?.toString() }
        }

        val zero = "00000000-0000-0000-0000-000000000000"
        val eid = empId ?: zero
        val stats = mapOf(
            "leaveCount" to count("SELECT count(*) FROM leave_request WHERE employee_id = ?::uuid", eid),
            "punchCount" to count("SELECT count(*) FROM attendance_punch WHERE employee_id = ?::uuid", eid),
            "dailyCount" to count("SELECT count(*) FROM attendance_daily WHERE employee_id = ?::uuid", eid),
            "payrollCount" to count("SELECT count(*) FROM payroll WHERE employee_id = ?::uuid", eid),
            "todoCount" to count("SELECT count(*) FROM workflow_task WHERE assignee_id = ?::uuid AND status = 'pending'", uid),
            "submittedCount" to count("SELECT count(*) FROM workflow_instance WHERE initiator_id = ?::uuid", uid),
            "unreadCount" to count("SELECT count(*) FROM notification_recipient WHERE user_id = ?::uuid AND status = 'unread'", uid),
        )

        return mapOf(
            "user" to mapOf(
                "id" to uid,
                "username" to me.username,
                "displayName" to me.displayName,
                "deptName" to me.deptName,
                "roles" to me.roles,
                "dataScope" to me.dataScope,
            ),
            "employee" to emp,
            "stats" to stats,
        )
    }

    private fun count(sql: String, vararg args: Any): Long =
        jdbc.queryForObject(sql, Long::class.java, *args) ?: 0
}
