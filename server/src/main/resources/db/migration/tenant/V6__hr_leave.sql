-- 请假单（M0 审批闭环的业务载体，对应《数据库表结构设计.md》§11.3）
CREATE TABLE leave_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE,
    employee_id          UUID REFERENCES employee(id),
    leave_type           VARCHAR(20),              -- annual/sick/personal/marriage/maternity
    start_at             TIMESTAMPTZ,
    end_at               TIMESTAMPTZ,
    duration             NUMERIC(6,2),             -- 天数
    half_day             BOOLEAN DEFAULT false,
    reason               TEXT,
    status               VARCHAR(20) DEFAULT 'pending', -- pending/approved/rejected/canceled
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at           TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_leave_emp ON leave_request(employee_id);
CREATE INDEX idx_leave_status ON leave_request(status);
