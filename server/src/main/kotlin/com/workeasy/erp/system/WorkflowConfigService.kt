package com.workeasy.erp.system

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.config.UserContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class NodeInput(
    val nodeKey: String = "",
    val name: String = "",
    val nodeType: String = "approve",      // approve / cc / end
    val approverType: String? = null,      // role / user / dept_manager
    val role: String? = null,
    val sameDept: Boolean? = null,
    val userId: String? = null,
    val canTransfer: Boolean = true,

    // 条件分支节点（nodeType = condition）
    val conditionField: String? = null,   // 表单字段，如 amount
    val conditionOp: String? = null,      // gte/gt/lte/lt/eq/neq
    val conditionValue: String? = null,   // 阈值
    val nextNodeKey: String? = null,      // 成立走向
    val elseNodeKey: String? = null,      // 不成立走向
)

data class DefInput(
    val code: String = "",
    val name: String = "",
    val bizType: String? = null,
    val remark: String? = null,
)

/**
 * 审批流程可视化配置的后端：全量保存节点（画布上是什么样就存成什么样）。
 * 安全约束：有在途单据的流程禁止改节点——否则运行中的实例会指向已删除的节点。
 */
@Service
class WorkflowConfigService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun defs(): List<Map<String, Any?>> = jdbc.queryForList(
        """
        SELECT d.id, d.code, d.name, d.biz_type, d.version, d.status, d.remark,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id) AS node_count,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id AND n.node_type='approve') AS approve_count,
               (SELECT count(*) FROM workflow_node n WHERE n.definition_id = d.id AND n.node_type='cc') AS cc_count
        FROM workflow_definition d ORDER BY d.code
        """.trimIndent()
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    @Transactional(readOnly = true)
    fun detail(code: String): Map<String, Any?> {
        val def = defId(code)
        val nodes = jdbc.queryForList(
            """
            SELECT id, node_key, name, node_type, approver_type, approver_rule,
                   condition, next_node_key, else_node_key, can_transfer, order_idx
            FROM workflow_node WHERE definition_id = ?::uuid ORDER BY order_idx
            """.trimIndent(),
            def,
        ).map { it.mapValues { (_, v) -> v?.toString() } }
        val running = jdbc.queryForObject(
            "SELECT count(*) FROM workflow_instance WHERE definition_id = ?::uuid AND status = 'running'",
            Long::class.java, def,
        ) ?: 0
        return mapOf("nodes" to nodes, "runningCount" to running.toString())
    }

    /** 保存节点：全量覆盖（先删后插） */
    @Transactional
    fun saveNodes(code: String, nodes: List<NodeInput>) {
        if (nodes.isEmpty()) throw BizException(ErrorCode.BIZ_PARAM, "流程至少需要一个节点")
        if (nodes.none { it.nodeType == "approve" }) throw BizException(ErrorCode.BIZ_PARAM, "流程至少需要一个审批节点")
        if (nodes.any { it.nodeKey.isBlank() }) throw BizException(ErrorCode.BIZ_PARAM, "节点标识不能为空")
        if (nodes.map { it.nodeKey }.distinct().size != nodes.size) throw BizException(ErrorCode.BIZ_PARAM, "节点标识重复")

        val defId = defId(code)
        val running = jdbc.queryForObject(
            "SELECT count(*) FROM workflow_instance WHERE definition_id = ?::uuid AND status = 'running'",
            Long::class.java, defId,
        ) ?: 0
        if (running > 0) {
            throw BizException(ErrorCode.BIZ_CONFLICT, "该流程有 $running 条在途单据，暂停修改；请先处理完或新建版本")
        }

        // 条件节点的分支目标必须指向画布内已有节点
        val keys = nodes.map { it.nodeKey }.toSet()
        nodes.filter { it.nodeType == "condition" }.forEach { c ->
            listOfNotNull(c.nextNodeKey, c.elseNodeKey).filter { it.isNotBlank() }.forEach { k ->
                if (k !in keys) throw BizException(ErrorCode.BIZ_PARAM, "条件节点「${c.name}」的分支目标 $k 不存在")
            }
        }

        jdbc.update("DELETE FROM workflow_node WHERE definition_id = ?::uuid", defId)
        nodes.forEachIndexed { i, n ->
            val rule = buildRule(n)
            val cond = if (n.nodeType == "condition") conditionJson(n) else null
            jdbc.update(
                """
                INSERT INTO workflow_node(definition_id, node_key, name, node_type, approver_type, approver_rule,
                                         condition, next_node_key, else_node_key, can_transfer, order_idx)
                VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """.trimIndent(),
                defId, n.nodeKey, n.name.ifBlank { n.nodeKey }, n.nodeType,
                if (n.nodeType == "condition") null
                else n.approverType ?: if (n.nodeType == "end") null else "role",
                rule, cond,
                n.nextNodeKey?.takeIf { it.isNotBlank() },
                n.elseNodeKey?.takeIf { it.isNotBlank() },
                n.canTransfer, i + 1,
            )
        }
        audit.log("system", "配置审批流程 $code（${nodes.size} 个节点）", "workflow_definition", code)
    }

    @Transactional
    fun createDef(req: DefInput) {
        val code = req.code.trim()
        if (code.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "流程标识不能为空")
        if (req.name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "流程名称不能为空")
        val exists = jdbc.queryForObject(
            "SELECT count(*) FROM workflow_definition WHERE code = ?", Long::class.java, code
        ) ?: 0
        if (exists > 0) throw BizException(ErrorCode.BIZ_CONFLICT, "流程 $code 已存在")
        jdbc.update(
            """
            INSERT INTO workflow_definition(code, name, biz_type, version, status, remark, created_by)
            VALUES (?, ?, ?, 1, 'active', ?, ?::uuid)
            """.trimIndent(),
            code, req.name, req.bizType ?: code, req.remark, UserContext.get().userId.toString(),
        )
        audit.log("system", "新建审批流程 $code", "workflow_definition", code)
    }

    @Transactional
    fun setStatus(code: String, status: String) {
        if (status !in setOf("active", "disabled")) throw BizException(ErrorCode.BIZ_PARAM, "状态无效")
        jdbc.update("UPDATE workflow_definition SET status = ? WHERE code = ?", status, code)
        audit.log("system", "流程 $code 状态改为 $status", "workflow_definition", code)
    }

    /** 审批人候选角色 */
    @Transactional(readOnly = true)
    fun candidateRoles(): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT code, name, data_scope FROM role ORDER BY id"
    ).map { it.mapValues { (_, v) -> v?.toString() } }

    private fun buildRule(n: NodeInput): String = when (n.nodeType) {
        "end" -> "{}"
        "condition" -> conditionJson(n)
        else -> when (n.approverType) {
            "user" -> """{"user":"${n.userId ?: ""}"}"""
            "dept_manager" -> """{"role":"manager","sameDept":true}"""
            else -> {
                val role = n.role?.takeIf { it.isNotBlank() } ?: "manager"
                """{"role":"$role","sameDept":${n.sameDept ?: false}}"""
            }
        }
    }

    /** 条件 JSON：数值型阈值不带引号，字符串带引号 */
    private fun conditionJson(n: NodeInput): String {
        val field = n.conditionField?.takeIf { it.isNotBlank() } ?: "amount"
        val op = n.conditionOp?.takeIf { it.isNotBlank() } ?: "gte"
        val raw = n.conditionValue?.takeIf { it.isNotBlank() } ?: "0"
        val value = if (raw.toBigDecimalOrNull() != null) raw else "\"$raw\""
        return """{"field":"$field","op":"$op","value":$value}"""
    }

    private fun defId(code: String): String = runCatching {
        jdbc.queryForObject("SELECT id FROM workflow_definition WHERE code = ?", String::class.java, code)
    }.getOrNull() ?: throw BizException(ErrorCode.BIZ_NOT_FOUND, "流程不存在：$code")
}
