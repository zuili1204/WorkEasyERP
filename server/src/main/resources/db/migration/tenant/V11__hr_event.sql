-- M1 人事事件（对应《数据库表结构设计.md》§11.1）
-- 说明：转正 / 调岗 / 晋升 / 离职 带 workflow_instance_id 走审批；
--       入职（HR 确认后建档）与劳动合同（登记）为确认类，不走审批。

CREATE TABLE hr_entry (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    apply_no          VARCHAR(40) UNIQUE,
    candidate_name    VARCHAR(50) NOT NULL,
    id_card           VARCHAR(20),
    phone             VARCHAR(20),
    expected_dept_id  UUID REFERENCES department(id),
    expected_position VARCHAR(50),
    source_channel    VARCHAR(30),               -- 招聘渠道
    referrer          VARCHAR(50),
    expected_entry_date DATE,
    actual_entry_date DATE,
    handler_id        UUID REFERENCES employee(id), -- HR 经办
    employee_id       UUID REFERENCES employee(id), -- 入职后回填
    status            VARCHAR(20) DEFAULT 'pending', -- pending/confirmed/canceled
    remark            TEXT,
    created_at        TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE hr_regularization (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id   UUID REFERENCES employee(id),
    probation_end DATE,
    apply_date    DATE,
    regular_date  DATE,
    evaluation    TEXT,
    status        VARCHAR(20) DEFAULT 'pending',  -- pending/approved/rejected
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    remark        TEXT,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE hr_transfer (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id    UUID REFERENCES employee(id),
    from_dept_id   UUID REFERENCES department(id),
    to_dept_id     UUID REFERENCES department(id),
    from_position  VARCHAR(50),
    to_position    VARCHAR(50),
    from_manager_id UUID REFERENCES employee(id),
    to_manager_id   UUID REFERENCES employee(id),
    effective_date DATE,
    reason         TEXT,
    status         VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at     TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE hr_promotion (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id   UUID REFERENCES employee(id),
    from_position VARCHAR(50),
    to_position   VARCHAR(50),
    from_level    VARCHAR(20),
    to_level      VARCHAR(20),
    effective_date DATE,
    reason        TEXT,
    status        VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE hr_dimission (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID REFERENCES employee(id),
    type            VARCHAR(20) NOT NULL,         -- resign/terminate/retire
    apply_date      DATE,
    last_work_date  DATE,                         -- 最后工作日
    reason          TEXT,
    handover_to     UUID REFERENCES employee(id), -- 工作交接人
    status          VARCHAR(20) DEFAULT 'pending',-- pending/approved/rejected
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    remark          TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE hr_contract (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id  UUID REFERENCES employee(id),
    contract_no  VARCHAR(40) UNIQUE,
    type         VARCHAR(20) DEFAULT 'fixed',     -- fixed(固定期)/nonfixed(无固定期)
    start_date   DATE,
    end_date     DATE,
    sign_date    DATE,
    status       VARCHAR(20) DEFAULT 'active',    -- active/expired/terminated
    attachment   VARCHAR(255),
    remark       TEXT,
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_contract_emp ON hr_contract(employee_id);
