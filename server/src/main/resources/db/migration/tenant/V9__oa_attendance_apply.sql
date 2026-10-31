-- M1 考勤与假勤（对应《数据库表结构设计.md》§11.2 §11.3 §11.4）

-- 班次定义
CREATE TABLE attendance_shift (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL,
    work_start      TIME,
    work_end        TIME,
    break_start     TIME,
    break_end       TIME,
    late_threshold  INT DEFAULT 0,                -- 迟到容忍分钟
    early_threshold INT DEFAULT 0,
    overtime_rule   VARCHAR(50),
    remark          VARCHAR(200)
);

-- 排班
CREATE TABLE attendance_schedule (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID REFERENCES employee(id),
    shift_id    UUID REFERENCES attendance_shift(id),
    work_date   DATE,
    remark      VARCHAR(200),
    UNIQUE (employee_id, work_date)
);
CREATE INDEX idx_sched_date ON attendance_schedule(work_date);

-- 原始打卡记录
CREATE TABLE attendance_punch (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID REFERENCES employee(id),
    punch_time  TIMESTAMPTZ NOT NULL,
    punch_type  VARCHAR(10) NOT NULL,            -- in/out
    source      VARCHAR(20) DEFAULT 'app',       -- app/device/wecom
    location    VARCHAR(100),
    device_id   VARCHAR(40),
    photo       VARCHAR(255),
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_punch_emp ON attendance_punch(employee_id, punch_time);

-- 日考勤结果
CREATE TABLE attendance_daily (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id    UUID REFERENCES employee(id),
    work_date      DATE,
    shift_id       UUID REFERENCES attendance_shift(id),
    check_in       TIMESTAMPTZ,
    check_out      TIMESTAMPTZ,
    status         VARCHAR(20) DEFAULT 'normal', -- normal/late/early/absent/leave
    late_min       INT DEFAULT 0,
    early_min      INT DEFAULT 0,
    overtime_hours NUMERIC(6,2) DEFAULT 0,
    remark         VARCHAR(200),
    UNIQUE (employee_id, work_date)
);

-- 加班
CREATE TABLE overtime_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE,
    employee_id          UUID REFERENCES employee(id),
    apply_date           DATE,
    start_at             TIMESTAMPTZ,
    end_at               TIMESTAMPTZ,
    duration             NUMERIC(6,2),
    reason               TEXT,
    status               VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at           TIMESTAMPTZ DEFAULT now()
);

-- 补卡 / 打卡申诉
CREATE TABLE punch_appeal (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id          UUID REFERENCES employee(id),
    work_date            DATE,
    punch_type           VARCHAR(10),            -- in/out
    appeal_reason        TEXT,
    status               VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at           TIMESTAMPTZ DEFAULT now()
);

-- 外出
CREATE TABLE outing_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE,
    employee_id          UUID REFERENCES employee(id),
    start_at             TIMESTAMPTZ,
    end_at               TIMESTAMPTZ,
    destination          VARCHAR(200),
    reason               TEXT,
    status               VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at           TIMESTAMPTZ DEFAULT now()
);

-- 出差
CREATE TABLE business_trip (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE,
    employee_id          UUID REFERENCES employee(id),
    start_date           DATE,
    end_date             DATE,
    destination          VARCHAR(200),
    reason               TEXT,
    status               VARCHAR(20) DEFAULT 'pending',
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_at           TIMESTAMPTZ DEFAULT now()
);

-- 薪资（按月）
CREATE TABLE payroll (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID REFERENCES employee(id),
    period          VARCHAR(7) NOT NULL,         -- 2026-10
    base_salary     NUMERIC(14,2) DEFAULT 0,
    bonus           NUMERIC(14,2) DEFAULT 0,
    allowance       NUMERIC(14,2) DEFAULT 0,
    deduction       NUMERIC(14,2) DEFAULT 0,
    social_security NUMERIC(14,2) DEFAULT 0,
    tax             NUMERIC(14,2) DEFAULT 0,
    net_pay         NUMERIC(14,2) DEFAULT 0,
    pay_date        DATE,
    status          VARCHAR(20) DEFAULT 'draft', -- draft/paid
    remark          TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_payroll_emp ON payroll(employee_id, period);
