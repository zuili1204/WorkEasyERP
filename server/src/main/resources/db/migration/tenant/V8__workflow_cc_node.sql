-- 请假流程插入「抄送老板」节点（node_type=cc，只发消息不阻塞）
-- 原顺序：主管审批(1) → HR 审批(2) → 结束(3)
-- 新顺序：主管审批(1) → 抄送老板(2) → HR 审批(3) → 结束(4)

UPDATE workflow_node
SET order_idx = order_idx + 1
WHERE definition_id = (SELECT id FROM workflow_definition WHERE code = 'leave')
  AND order_idx >= 2;

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, 'cc_boss', '抄送老板', 'cc', 'role', '{"role":"boss"}'::jsonb, false, 2
FROM workflow_definition d
WHERE d.code = 'leave'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = 'cc_boss'
  );
