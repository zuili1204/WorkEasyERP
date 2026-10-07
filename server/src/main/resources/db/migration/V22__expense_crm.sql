-- 补齐原型中缺失的模块：报销（财务）+ 线索 / 商机 / 合同（CRM）
-- 均带 dept_id / owner_id 以支撑「我的 / 本部门 / 全部」行级过滤

CREATE TABLE expense (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no                   VARCHAR(32) UNIQUE,                  -- EXP{yyyyMMdd}{seq}
    employee_id          UUID REFERENCES employee(id),
    employee_name        VARCHAR(64),                         -- 快照
    dept_id              UUID REFERENCES department(id),
    dept_name            VARCHAR(64),
    category             VARCHAR(24) DEFAULT 'other',         -- travel/office/meal/transport/entertain/train/other
    amount               NUMERIC(18,2) DEFAULT 0,
    currency             VARCHAR(8) DEFAULT 'CNY',
    happen_date          DATE DEFAULT CURRENT_DATE,
    reason               VARCHAR(500),
    status               VARCHAR(16) DEFAULT 'draft',         -- draft/approving/approved/rejected/paid/void
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    pay_date             DATE,
    remark               VARCHAR(500),
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_exp_emp ON expense(employee_id);
CREATE INDEX idx_exp_status ON expense(status);

CREATE TABLE lead (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no                    VARCHAR(32) UNIQUE,                 -- LD{yyyyMMdd}{seq}
    name                  VARCHAR(128) NOT NULL,
    source                VARCHAR(24) DEFAULT 'other',        -- web/referral/exhibition/call/other
    contact_name          VARCHAR(64),
    contact_phone         VARCHAR(32),
    owner_id              UUID REFERENCES employee(id),
    dept_id               UUID REFERENCES department(id),
    status                VARCHAR(16) DEFAULT 'following',    -- following/converted/dropped
    converted_customer_id UUID REFERENCES customer(id),
    remark                VARCHAR(500),
    created_by            UUID REFERENCES users(id),
    created_at            TIMESTAMPTZ DEFAULT now(),
    updated_at            TIMESTAMPTZ DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);
CREATE INDEX idx_lead_owner ON lead(owner_id);

CREATE TABLE opportunity (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no                VARCHAR(32) UNIQUE,                     -- OP{yyyyMMdd}{seq}
    name              VARCHAR(128) NOT NULL,
    customer_id       UUID REFERENCES customer(id),
    customer_name     VARCHAR(128),                           -- 快照
    owner_id          UUID REFERENCES employee(id),
    dept_id           UUID REFERENCES department(id),
    stage             VARCHAR(24) DEFAULT 'contact',          -- contact/quote/solution/negotiation/won/lost
    amount            NUMERIC(18,2) DEFAULT 0,
    expect_close_date DATE,
    status            VARCHAR(16) DEFAULT 'open',             -- open/won/lost
    lost_reason       VARCHAR(255),
    remark            VARCHAR(500),
    created_by        UUID REFERENCES users(id),
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_opp_customer ON opportunity(customer_id);
CREATE INDEX idx_opp_owner ON opportunity(owner_id);

-- 业务合同（与人事 hr_contract 区分命名）
CREATE TABLE biz_contract (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no             VARCHAR(32) UNIQUE,                        -- CT{yyyyMMdd}{seq}
    name           VARCHAR(128),
    customer_id    UUID REFERENCES customer(id),
    customer_name  VARCHAR(128),                              -- 快照
    opportunity_id UUID,
    amount         NUMERIC(18,2) DEFAULT 0,
    sign_date      DATE DEFAULT CURRENT_DATE,
    start_date     DATE,
    end_date       DATE,
    payment_terms  INTEGER DEFAULT 0,                         -- 账期（天）
    owner_id       UUID REFERENCES employee(id),
    dept_id        UUID REFERENCES department(id),
    status         VARCHAR(16) DEFAULT 'effective',           -- draft/effective/expiring/expired/terminated
    remark         VARCHAR(500),
    created_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_bizcontract_customer ON biz_contract(customer_id);
CREATE INDEX idx_bizcontract_end ON biz_contract(end_date);

-- 报销审批流程：主管审批 → 财务审批 → 结束
INSERT INTO workflow_definition (code, name, biz_type, version, status, remark)
SELECT 'expense', '报销审批', 'expense', 1, 'active', '报销需主管与财务审批后付款'
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition d WHERE d.code = 'expense');

INSERT INTO workflow_node (definition_id, node_key, name, node_type, approver_type, approver_rule, can_transfer, order_idx)
SELECT d.id, v.node_key, v.name, v.node_type, v.approver_type, v.approver_rule::jsonb, true, v.order_idx
FROM workflow_definition d
CROSS JOIN (VALUES
    ('manager_approve', '直属主管审批', 'approve', 'role', '{"role":"manager","sameDept":true}', 1),
    ('finance_approve', '财务审批',     'approve', 'role', '{"role":"finance"}',                 2),
    ('end',             '结束',          'end',     NULL,   '{}',                                 3)
) AS v(node_key, name, node_type, approver_type, approver_rule, order_idx)
WHERE d.code = 'expense'
  AND NOT EXISTS (
      SELECT 1 FROM workflow_node n WHERE n.definition_id = d.id AND n.node_key = v.node_key
  );
