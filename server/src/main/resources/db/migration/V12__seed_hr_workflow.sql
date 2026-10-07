-- 人事事件审批流程：主管审批 → HR 审批
-- bizType（code）：regular 转正 / transfer 调岗 / promo 晋升 / dimission 离职

INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT v.code, v.name, v.code, 1, 'active', '主管审批后 HR 审批'
FROM (VALUES
    ('regular',    '转正审批'),
    ('transfer',   '调岗审批'),
    ('promo',      '晋升审批'),
    ('dimission',  '离职审批')
) AS v(code, name)
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition d WHERE d.code = v.code);

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('hr_approve',      'HR 审批',      'approve', 'role', '{"role":"hr"}',                      2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code IN ('regular', 'transfer', 'promo', 'dimission')
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
