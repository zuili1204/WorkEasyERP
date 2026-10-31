-- 采购申请（原型 purchase_req）：采购流程起点，审批通过后可转为采购订单

CREATE TABLE purchase_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no                   VARCHAR(32) UNIQUE,                  -- PR{yyyyMMdd}{seq}
    applicant_id         UUID REFERENCES employee(id),
    applicant_name       VARCHAR(64),
    dept_id              UUID REFERENCES department(id),
    supplier_id          UUID REFERENCES supplier(id),
    supplier_name        VARCHAR(128),                        -- 快照
    expect_date          DATE,
    reason               VARCHAR(500),
    status               VARCHAR(16) DEFAULT 'draft',         -- draft/approving/approved/rejected/converted/void
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    remark               VARCHAR(500),
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_pr_dept ON purchase_request(dept_id);

CREATE TABLE purchase_request_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   UUID REFERENCES purchase_request(id) ON DELETE CASCADE,
    product_id   UUID REFERENCES product(id),
    product_name VARCHAR(100),
    unit         VARCHAR(10),
    qty          NUMERIC(14,3) NOT NULL DEFAULT 0,
    remark       VARCHAR(255)
);
CREATE INDEX idx_pri_request ON purchase_request_item(request_id);

-- 采购申请审批：主管审批 → 财务审批 → 结束
INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT 'purchase_request', '采购申请审批', 'purchase_request', 1, 'active', '采购申请需主管与财务审批'
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition d WHERE d.code = 'purchase_request');

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('finance_approve', '财务审批',     'approve', 'role', '{"role":"finance"}',                 2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code = 'purchase_request'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
