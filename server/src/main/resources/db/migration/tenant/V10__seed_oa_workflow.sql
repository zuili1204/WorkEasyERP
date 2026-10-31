-- 假勤审批流程：主管审批 → 抄送 HR（code 必须与 bizType 一致，引擎按 code 查找）
-- overtime 加班 / appeal 补卡 / outing 外出 / trip 出差

INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT v.code, v.name, v.code, 1, 'active', '主管审批后抄送 HR'
FROM (VALUES
    ('overtime', '加班申请审批'),
    ('appeal',   '补卡申诉审批'),
    ('outing',   '外出申请审批'),
    ('trip',     '出差申请审批')
) AS v(code, name)
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition d WHERE d.code = v.code);

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, false, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('cc_hr',           '抄送 HR',      'cc',      'role', '{"role":"hr"}',                      2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code IN ('overtime', 'appeal', 'outing', 'trip')
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
