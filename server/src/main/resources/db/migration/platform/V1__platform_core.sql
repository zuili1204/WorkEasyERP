-- 平台库（平台统一小程序 / SaaS 多租户）：只放跨租户身份、租户注册与渠道配置，不放业务数据
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 租户注册表：连接信息指向各自独立 database；口令不落明文（仅存环境变量引用或密文）
CREATE TABLE IF NOT EXISTS tenant (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',   -- provisioning/active/suspended/failed
    db_host          VARCHAR(200),
    db_port          INT          NOT NULL DEFAULT 5432,
    db_name          VARCHAR(100),
    db_username      VARCHAR(100),
    db_password_ref  VARCHAR(200),                             -- 环境变量 / 配置项名
    db_password_enc  VARCHAR(500),                             -- AES-GCM 密文（开发/小规模部署用）
    plan             VARCHAR(50),
    expire_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  DEFAULT now(),
    updated_at       TIMESTAMPTZ  DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tenant_status ON tenant(status);

-- 开通流程日志：每步成功/失败可追踪、可重试
CREATE TABLE IF NOT EXISTS tenant_provision_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID REFERENCES tenant(id) ON DELETE CASCADE,
    step        VARCHAR(50) NOT NULL,                          -- create_db/migrate/seed/activate
    status      VARCHAR(20) NOT NULL,                          -- started/success/failed
    message     TEXT,
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_provision_log_tenant ON tenant_provision_log(tenant_id, created_at DESC);

-- 平台运营账号（平台侧，不进入租户库）
CREATE TABLE IF NOT EXISTS platform_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(64)  NOT NULL UNIQUE,
    password_hash  VARCHAR(200) NOT NULL,
    display_name   VARCHAR(100),
    role           VARCHAR(30)  NOT NULL DEFAULT 'operator',
    status         VARCHAR(20)  NOT NULL DEFAULT 'active',
    last_login     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

-- 租户支付渠道（平台统一 AppID + 租户商户号；密钥同样只存引用）
CREATE TABLE IF NOT EXISTS tenant_channel (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider     VARCHAR(20) NOT NULL DEFAULT 'wechat',
    app_id       VARCHAR(64),
    mch_id       VARCHAR(64),
    mch_key_ref  VARCHAR(200),
    status       VARCHAR(20) NOT NULL DEFAULT 'disabled',      -- disabled/enabled
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE (tenant_id, provider)
);
