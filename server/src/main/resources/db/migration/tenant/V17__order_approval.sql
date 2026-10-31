-- 采购 / 销售订单审批流程（code 必须与 bizType 一致，引擎按 code 查找）
-- 采购订单：主管审批 → 抄送财务；销售订单：主管审批 → 财务审批（涉及授信与账期）

INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT v.code, v.name, v.code, 1, 'active', v.remark
FROM (VALUES
    ('purchase_order', '采购订单审批', '订单审批通过后才能收货'),
    ('sales_order',    '销售订单审批', '订单审批通过后才能发货')
) AS v(code, name, remark)
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition d WHERE d.code = v.code);

-- 采购订单：主管 → 抄送财务 → 结束
INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('cc_finance',      '抄送财务',     'cc',      'role', '{"role":"finance"}',                 2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code = 'purchase_order'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );

-- 销售订单：主管 → 财务 → 结束
INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('finance_approve', '财务审批',     'approve', 'role', '{"role":"finance"}',                 2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code = 'sales_order'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
