-- 条件分支：金额阈值路由（向后兼容：无 condition 节点时仍按 order_idx 顺序流转）

ALTER TABLE workflow_node
    ADD COLUMN IF NOT EXISTS condition     JSONB,        -- {"field":"amount","op":"gte","value":100000}
    ADD COLUMN IF NOT EXISTS next_node_key VARCHAR(40),  -- 条件成立 → 走向该 node_key
    ADD COLUMN IF NOT EXISTS else_node_key VARCHAR(40);  -- 条件不成立 → 走向该 node_key

-- 销售订单：金额 ≥ 10 万走老板审批，否则财务审批
-- 目标形态：主管审批(1) → 金额分支(2) → 老板审批(3) / 财务审批(4) → 结束(5)
DO $$
DECLARE def UUID;
BEGIN
    SELECT id INTO def FROM workflow_definition WHERE code = 'sales_order';

    IF def IS NOT NULL
       AND EXISTS (SELECT 1 FROM workflow_node WHERE definition_id = def AND node_key = 'finance_approve')
       AND NOT EXISTS (SELECT 1 FROM workflow_node WHERE definition_id = def AND node_key = 'amount_branch') THEN

        -- 原 finance_approve(2) → 4，end(3) → 5
        UPDATE workflow_node SET order_idx = order_idx + 2
        WHERE definition_id = def AND order_idx >= 2;

        INSERT INTO workflow_node(definition_id, node_key, name, node_type, approver_type,
                                  approver_rule, condition, next_node_key, else_node_key,
                                  can_transfer, order_idx)
        VALUES
            (def, 'amount_branch', '金额 ≥ 10 万？', 'condition', NULL, '{}',
             '{"field":"amount","op":"gte","value":100000}'::jsonb,
             'boss_approve', 'finance_approve', false, 2),
            (def, 'boss_approve', '老板审批', 'approve', 'role',
             '{"role":"boss","sameDept":false}'::jsonb, NULL, NULL, NULL, true, 3);
    END IF;
END $$;
