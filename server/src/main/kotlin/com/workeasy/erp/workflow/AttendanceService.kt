package com.workeasy.erp.workflow

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.ScopeResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 考勤：班次 / 排班 / 打卡 / 日结
 * 列表统一走 [listTable]，按数据范围注入过滤条件。
 */
@Service
class AttendanceService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
    private val scopeResolver: ScopeResolver,
) {

    /** 打卡：写入原始打卡记录（in/out） */
    @Transactional
    fun punch(type: String, location: String?) {
        val me = UserContext.get()
        val empId = me.employeeId ?: throw BizException(ErrorCode.BIZ_PARAM, "当前账号未关联员工档案")
        val t = if (type.lowercase() == "out") "out" else "in"
        jdbc.update(
            """
            INSERT INTO attendance_punch(employee_id, punch_time, punch_type, source, location)
            VALUES (?::uuid, now(), ?, 'app', ?)
            """.trimIndent(),
            empId.toString(),
            t,
            location,
        )
        audit.log("attendance", "打卡 $t", "attendance_punch", empId.toString())
    }

    @Transactional(readOnly = true)
    fun punches(q: String?, scope: String?, page: Int, size: Int) =
        listTable("attendance_punch", q, scope, page, size, "punch_time DESC", nameOnly = true)

    @Transactional(readOnly = true)
    fun daily(q: String?, scope: String?, page: Int, size: Int) =
        listTable("attendance_daily", q, scope, page, size, "work_date DESC", nameOnly = true)

    @Transactional(readOnly = true)
    fun shifts(q: String?, page: Int, size: Int) =
        listTable("attendance_shift", q, "all", page, size, "name ASC", nameOnly = false)

    @Transactional(readOnly = true)
    fun schedules(q: String?, scope: String?, page: Int, size: Int) =
        listTable("attendance_schedule", q, scope, page, size, "work_date DESC", nameOnly = true)

    @Transactional
    fun createShift(name: String, workStart: String?, workEnd: String?, lateThreshold: Int?) {
        if (name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "班次名称不能为空")
        jdbc.update(
            """
            INSERT INTO attendance_shift(name, work_start, work_end, late_threshold)
            VALUES (?, ?::time, ?::time, ?)
            """.trimIndent(),
            name,
            workStart,
            workEnd,
            lateThreshold ?: 0,
        )
        audit.log("attendance", "新增班次 $name", "attendance_shift", null)
    }

    @Transactional
    fun createSchedule(employeeId: String?, shiftId: String?, workDate: String?) {
        val emp = employeeId ?: UserContext.get().employeeId?.toString()
            ?: throw BizException(ErrorCode.BIZ_PARAM, "缺少员工")
        if (shiftId.isNullOrBlank() || workDate.isNullOrBlank()) {
            throw BizException(ErrorCode.BIZ_PARAM, "班次与日期不能为空")
        }
        jdbc.update(
            """
            INSERT INTO attendance_schedule(employee_id, shift_id, work_date)
            VALUES (?::uuid, ?::uuid, ?::date)
            ON CONFLICT (employee_id, work_date) DO UPDATE SET shift_id = EXCLUDED.shift_id
            """.trimIndent(),
            emp,
            shiftId,
            workDate,
        )
        audit.log("attendance", "排班 $workDate", "attendance_schedule", emp)
    }

    private fun listTable(
        table: String,
        q: String?,
        scope: String?,
        page: Int,
        size: Int,
        order: String,
        nameOnly: Boolean,
    ): PageResult<Map<String, Any?>> {
        val me = UserContext.get()
        val eff = if (nameOnly) scopeResolver.resolve(scope, me, "attendance") else "all"

        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        if (nameOnly) {
            when (eff) {
                "mine" -> {
                    cond.append("AND t.employee_id = ?::uuid ")
                    args.add(me.employeeId?.toString() ?: "00000000-0000-0000-0000-000000000000")
                }
                "dept" -> {
                    cond.append("AND e.department_id = ?::uuid ")
                    args.add(me.deptId?.toString() ?: "00000000-0000-0000-0000-000000000000")
                }
            }
        }
        if (!q.isNullOrBlank() && nameOnly) {
            cond.append("AND e.real_name ILIKE ? ")
            args.add("%$q%")
        } else if (!q.isNullOrBlank()) {
            cond.append("AND t.name ILIKE ? ")
            args.add("%$q%")
        }

        val join = if (nameOnly) "LEFT JOIN employee e ON e.id = t.employee_id" else ""
        val select = if (nameOnly) "t.*, e.real_name AS employee_name" else "t.*"
        val from = "FROM $table t $join $cond"

        val total = jdbc.queryForObject("SELECT count(*) $from", Long::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.queryForList(
            "SELECT $select $from ORDER BY $order LIMIT ? OFFSET ?",
            *(args + listOf(size, (page - 1) * size)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, page, size)
    }
}
