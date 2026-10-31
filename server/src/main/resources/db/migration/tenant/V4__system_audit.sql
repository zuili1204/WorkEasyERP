-- S8 系统：审计日志 / 自定义报表（对应《数据库表结构设计.md》§16.1.4）

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID REFERENCES users(id),
    user_name    VARCHAR(64),                  -- 冗余快照，避免 join
    module       VARCHAR(32),                  -- 模块：sales_order / inventory / hr ...
    action       VARCHAR(128),                 -- 动作描述：审批通过 SO202610030010
    target_table VARCHAR(64),
    target_id    VARCHAR(64),
    before_value JSONB,
    after_value  JSONB,
    ip           VARCHAR(45),
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_module ON audit_log(module);

CREATE TABLE report_definition (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(128) NOT NULL,
    module            VARCHAR(24),             -- sales/purchase/inventory/finance/hr
    dimensions        JSONB,
    metrics           JSONB,
    frequency         VARCHAR(16) DEFAULT 'none', -- none/daily/weekly/monthly
    last_generated_at TIMESTAMPTZ,
    created_by        UUID REFERENCES users(id),
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);
