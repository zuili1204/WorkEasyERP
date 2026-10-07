package com.workeasy.erp.system

import com.workeasy.erp.common.PageResult
import com.workeasy.erp.common.Paging
import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 系统管理：操作日志 / 角色权限 / 审批流程配置 */
@Service
class SystemService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun auditLogs(q: String?, module: String?, page: Int, size: Int): PageResult<Map<String, Any?>> {
        // 日志表增长最快，分页与关键词更要钳制，避免全表扫描
        val (pg, pgSize) = Paging.clamp(page, size)
        val kw = Paging.keyword(q)
        val cond = StringBuilder("WHERE 1=1 ")
        val args = mutableListOf<Any>()
        if (kw != null) {
            cond.append("AND (module ILIKE ? OR action ILIKE ? OR user_name ILIKE ?) ")
            args.add("%$kw%")
            args.add("%$kw%")
            args.add("%$kw%")
        }
        if (!module.isNullOrBlank()) {
            cond.append("AND module = ? ")
            args.add(module)
        }
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM audit_log $cond", Long::class.java, *args.toTypedArray()
        ) ?: 0
        val rows = jdbc.queryForList(
            "SELECT * FROM audit_log $cond ORDER BY id DESC LIMIT ? OFFSET ?",
            *(args + listOf(pgSize, (pg - 1) * pgSize)).toTypedArray(),
        )
        return PageResult.of(rows.map { it.mapValues { (_, v) -> v?.toString() } }, total, pg, pgSize)
    }

    @Transactional(readOnly = true)
    fun roles(): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT id, code, name, data_scope, description FROM role ORDER BY id"
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional(readOnly = true)
    fun permissions(): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT id, code, module, action, description FROM permission ORDER BY module, code"
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional(readOnly = true)
    fun rolePermissionCodes(roleCode: String): List<String> = jdbc.queryForList(
        """
        SELECT p.code FROM role_permission rp
        JOIN role r ON r.id = rp.role_id
        JOIN permission p ON p.id = rp.permission_id
        WHERE r.code = ?
        """.trimIndent(),
        String::class.java,
        roleCode,
    )

    /** 角色授权：先清空再批量写入（全量覆盖） */
    @Transactional
    fun assignPermissions(roleCode: String, codes: List<String>) {
        jdbc.update(
            """
            DELETE FROM role_permission
            WHERE role_id = (SELECT id FROM role WHERE code = ?)
            """.trimIndent(),
            roleCode,
        )
        if (codes.isEmpty()) {
            audit.log("system", "清空角色 $roleCode 的权限", "role_permission", null)
            return
        }
        codes.distinct().forEach { code ->
            jdbc.update(
                """
                INSERT INTO role_permission(role_id, permission_id)
                SELECT r.id, p.id FROM role r, permission p
                WHERE r.code = ? AND p.code = ?
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                roleCode,
                code,
            )
        }
        audit.log("system", "角色 $roleCode 授权 ${codes.size} 项", "role_permission", roleCode)
    }

    /** 流程定义列表（含节点数） */
    @Transactional(readOnly = true)
    fun workflowDefs(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT d.id, d.code, d.name, d.biz_type, d.version, d.status, d.remark,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id) AS node_count,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id AND n.node_type='approve') AS approve_count,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id AND n.node_type='cc') AS cc_count
        FROM workflow_definition d
        ORDER BY d.code
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional(readOnly = true)
    fun workflowNodes(definitionId: String): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT id, node_key, name, node_type, approver_type, approver_rule, order_idx
        FROM workflow_node WHERE definition_id = ?::uuid ORDER BY order_idx
        """.trimIndent(),
        definitionId,
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    /** 模块清单（供日志筛选下拉） */
    fun modules(): List<String> = jdbc.queryForList(
        "SELECT DISTINCT module FROM audit_log WHERE module IS NOT NULL ORDER BY module",
        String::class.java,
    )
}
