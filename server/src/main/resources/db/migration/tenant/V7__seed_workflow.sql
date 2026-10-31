-- 请假审批流程：主管审批 → HR 审批（M0 首版：固定节点 + 角色规则，可视化配置器放 M3）
INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT 'leave', '请假审批', 'leave', 1, 'active', '主管审批后转 HR 审批'
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition WHERE code = 'leave');

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('hr_approve',      'HR 审批',      'approve', 'role', '{"role":"hr"}',                    2),
    ('end',             '结束',          'end',     NULL,   '{}',                               3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code = 'leave'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
