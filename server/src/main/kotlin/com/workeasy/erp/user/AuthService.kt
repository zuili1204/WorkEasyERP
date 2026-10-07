package com.workeasy.erp.user

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.FieldPolicy
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.JwtProvider
import com.workeasy.erp.config.LoginUser
import com.workeasy.erp.user.repository.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class LoginRequest(val username: String = "", val password: String = "")

data class LoginUserInfo(
    val id: String,
    val username: String,
    val displayName: String,
    val deptName: String?,
    val roles: List<String>,
    val dataScope: String,
    /** 当前角色不可见的敏感字段（前端据此隐藏列；后端已同步脱敏） */
    val hiddenFields: List<String> = emptyList(),
)

data class LoginResponse(val token: String, val user: LoginUserInfo)

@Service
class AuthService(
    private val userRepo: UserRepository,
    private val jdbc: JdbcTemplate,
    private val jwt: JwtProvider,
) {
    private val encoder = BCryptPasswordEncoder()

    @Transactional
    fun login(req: LoginRequest): LoginResponse {
        val user = userRepo.findByUsername(req.username.trim())
            ?: throw BizException(ErrorCode.AUTH_LOGIN_FAIL)
        if (user.status != "active") throw BizException(ErrorCode.AUTH_DISABLED)
        if (user.passwordHash.isNullOrBlank() || !encoder.matches(req.password, user.passwordHash)) {
            throw BizException(ErrorCode.AUTH_LOGIN_FAIL)
        }
        val uid = user.id ?: throw BizException(ErrorCode.AUTH_LOGIN_FAIL)
        jdbc.update("UPDATE users SET last_login = now() WHERE id = ?", uid)
        val lu = loadLoginUser(uid)
        return LoginResponse(
            token = jwt.generate(uid.toString(), lu.username),
            user = LoginUserInfo(
                id = uid.toString(),
                username = lu.username,
                displayName = lu.displayName,
                deptName = lu.deptName,
                roles = lu.roles,
                dataScope = lu.dataScope,
                hiddenFields = FieldPolicy.hiddenFields(lu.roles),
            ),
        )
    }

    /** 加载登录用户（含角色与部门），供拦截器注入 UserContext */
    fun loadLoginUser(userId: UUID): LoginUser {
        val row = jdbc.queryForMap(
            """
            SELECT u.username,
                   e.id AS emp_id, e.real_name,
                   e.department_id AS dept_id, d.name AS dept_name
            FROM users u
            LEFT JOIN employee e ON e.id = u.employee_id AND e.deleted_at IS NULL
            LEFT JOIN department d ON d.id = e.department_id
            WHERE u.id = ?::uuid AND u.deleted_at IS NULL
            """.trimIndent(),
            userId.toString(),
        )

        val roleRows = jdbc.queryForList(
            """
            SELECT r.code, r.data_scope
            FROM user_role ur JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = ?::uuid
            """.trimIndent(),
            userId.toString(),
        )
        val roles = roleRows.mapNotNull { it["code"]?.toString() }
        val scopes = roleRows.mapNotNull { it["data_scope"]?.toString() }

        return LoginUser(
            userId = userId,
            username = row["username"]?.toString() ?: "",
            displayName = row["real_name"]?.toString() ?: row["username"]?.toString() ?: "",
            employeeId = row["emp_id"] as? UUID,
            deptId = row["dept_id"] as? UUID,
            deptName = row["dept_name"]?.toString(),
            roles = roles,
            dataScope = widestScope(scopes),
        )
    }

    /** 多角色取最宽范围：all > dept > customer/warehouse > self */
    private fun widestScope(list: List<String>): String {
        val rank = mapOf("all" to 4, "dept" to 3, "customer" to 2, "warehouse" to 2, "self" to 1)
        return list.maxByOrNull { rank[it] ?: 0 } ?: "self"
    }
}
