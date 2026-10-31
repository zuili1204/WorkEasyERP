-- S0 扩展 + S1 组织与账号 + 权限（对应《数据库表结构设计.md》§2 §3）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 部门（树形：parent_id 自引用 + path 冗余便于查询）
CREATE TABLE department (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    parent_id    UUID REFERENCES department(id),
    path         TEXT,                         -- 冗余路径，如 /1/3/5
    level        INT DEFAULT 1,
    manager_id   UUID,                         -- 关联 employee.id（建表后补 FK）
    sort_no      INT DEFAULT 0,
    phone        VARCHAR(20),
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_dept_parent ON department(parent_id);

-- 员工档案
CREATE TABLE employee (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID UNIQUE,                -- 关联 users.id（未开通账号可空）
    department_id   UUID REFERENCES department(id),
    employee_no     VARCHAR(30) UNIQUE,
    real_name       VARCHAR(50) NOT NULL,
    gender          VARCHAR(10),
    birthday        DATE,
    id_card         VARCHAR(20),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    position        VARCHAR(50),               -- 岗位
    job_title       VARCHAR(50),               -- 职称
    manager_id      UUID,                       -- 直属上级 employee.id
    employment_type VARCHAR(20) DEFAULT 'fulltime', -- fulltime/parttime/intern
    hire_date       DATE,
    probation_end   DATE,
    regular_date    DATE,                       -- 转正日期
    entry_status    VARCHAR(20) DEFAULT 'probation', -- probation/regular
    leave_date      DATE,
    status          VARCHAR(20) DEFAULT 'active', -- active/probation/resigned
    education       VARCHAR(20),
    native_place    VARCHAR(50),
    emergency_contact VARCHAR(50),
    emergency_phone VARCHAR(20),
    bank_name       VARCHAR(50),
    bank_account    VARCHAR(40),
    salary          NUMERIC(14,2),             -- 基本工资
    ext             JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_emp_dept ON employee(department_id);

-- 登录账号
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50) UNIQUE,
    password_hash VARCHAR(100),
    im_openid     VARCHAR(100) UNIQUE,         -- 企微/钉钉/飞书 openid
    im_type       VARCHAR(20),                 -- wecom/dingtalk/feishu
    employee_id   UUID REFERENCES employee(id),
    status        VARCHAR(20) DEFAULT 'active',
    last_login    TIMESTAMPTZ,
    login_fail    INT DEFAULT 0,
    locked_until  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

-- 权限（RBAC）
CREATE TABLE role (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(30) UNIQUE NOT NULL,  -- sys_admin/boss/manager/finance/hr/sales/purchase/warehouse/employee
    name        VARCHAR(50) NOT NULL,
    data_scope  VARCHAR(20) NOT NULL,         -- all/dept/self/customer/warehouse
    description VARCHAR(200),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE permission (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(60) UNIQUE NOT NULL,  -- 例: sales:create / finance:approve
    module      VARCHAR(30) NOT NULL,         -- purchase/sales/inventory/finance/hr/...
    action      VARCHAR(20) NOT NULL,         -- view/create/approve/export
    description VARCHAR(200)
);

CREATE TABLE role_permission (
    role_id       UUID REFERENCES role(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role (
    user_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id  UUID REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
