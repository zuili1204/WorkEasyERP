-- S2 审批引擎 + 消息与待办（对应《数据库表结构设计.md》§4 §5）

CREATE TABLE workflow_definition (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(40) UNIQUE NOT NULL,  -- leave/expense/purchase/sales...
    name        VARCHAR(80) NOT NULL,
    biz_type    VARCHAR(40) NOT NULL,
    version     INT DEFAULT 1,
    form_schema JSONB,                        -- 表单字段定义
    status      VARCHAR(20) DEFAULT 'active',
    remark      VARCHAR(200),
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE workflow_node (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id UUID REFERENCES workflow_definition(id) ON DELETE CASCADE,
    node_key      VARCHAR(40) NOT NULL,
    name          VARCHAR(80),
    node_type     VARCHAR(20) NOT NULL,       -- start/approve/cc/end
    approver_type VARCHAR(20),                -- role/user/dept_manager/amount
    approver_rule JSONB NOT NULL,             -- 例: {"role":"manager"} / {"amountField":"total","gt":10000,"role":"boss"}
    can_transfer  BOOLEAN DEFAULT true,
    order_idx     INT NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_node_def ON workflow_node(definition_id, order_idx);

CREATE TABLE workflow_instance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id    UUID REFERENCES workflow_definition(id),
    biz_type         VARCHAR(40) NOT NULL,
    biz_id           UUID,                     -- 关联业务单据 id
    title            VARCHAR(200),
    priority         VARCHAR(10) DEFAULT 'normal',
    initiator_id     UUID REFERENCES users(id),
    dept_id          UUID REFERENCES department(id),
    status           VARCHAR(20) DEFAULT 'running', -- running/approved/rejected/canceled
    current_node_id  UUID REFERENCES workflow_node(id),
    form_data        JSONB,
    result           VARCHAR(20),              -- approved/rejected
    created_at       TIMESTAMPTZ DEFAULT now(),
    finished_at      TIMESTAMPTZ
);
CREATE INDEX idx_inst_biz ON workflow_instance(biz_type, biz_id);

CREATE TABLE workflow_task (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id    UUID REFERENCES workflow_instance(id) ON DELETE CASCADE,
    node_id        UUID REFERENCES workflow_node(id),
    assignee_id    UUID REFERENCES users(id),  -- 实际审批人
    assignee_role  VARCHAR(30),                -- 候选角色
    claimed_by     UUID REFERENCES users(id),  -- 代理人/签收人
    status         VARCHAR(20) DEFAULT 'pending', -- pending/approved/rejected/transferred/skipped
    sla_due_at     TIMESTAMPTZ,                -- 超时提醒
    acted_at       TIMESTAMPTZ,
    comment        TEXT,
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_task_assignee ON workflow_task(assignee_id, status);

CREATE TABLE workflow_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID REFERENCES workflow_instance(id) ON DELETE CASCADE,
    task_id     UUID REFERENCES workflow_task(id),
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(20),                   -- approve/reject/transfer
    content     TEXT,
    attachment  VARCHAR(255),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE notification (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          VARCHAR(20) NOT NULL,        -- todo/approve/cc/system
    template_code VARCHAR(40),
    title         VARCHAR(200),
    content       TEXT,
    sender_id     UUID REFERENCES users(id),
    biz_type      VARCHAR(40),
    biz_id        UUID,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE notification_recipient (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID REFERENCES notification(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    channel         VARCHAR(20) DEFAULT 'inapp', -- inapp/im(企微等)
    status          VARCHAR(20) DEFAULT 'unread', -- unread/read
    read_at         TIMESTAMPTZ
);
CREATE INDEX idx_notif_user ON notification_recipient(user_id, status);
