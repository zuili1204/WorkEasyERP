-- 阶段三：平台统一会员（平台 AppID 下 openid 全局唯一）+ 会员与租户客户的绑定关系
-- 业务数据仍落各租户库；平台库只保存跨租户身份。

CREATE TABLE IF NOT EXISTS platform_member (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wx_openid      VARCHAR(64)  NOT NULL UNIQUE,          -- 平台 AppID 换取的 openid
    wx_unionid     VARCHAR(64),
    phone          VARCHAR(20),
    nickname       VARCHAR(100),
    password_hash  VARCHAR(200),                          -- 可选：账号密码兜底登录
    status         VARCHAR(20) NOT NULL DEFAULT 'active', -- active/disabled
    last_login     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_platform_member_phone ON platform_member(phone);

-- 会员 ↔ 租户客户（customer 在租户库，这里只存 id 与审核状态）
CREATE TABLE IF NOT EXISTS member_tenant_binding (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id    UUID NOT NULL REFERENCES platform_member(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    tenant_code  VARCHAR(64) NOT NULL,
    customer_id  UUID,                                    -- 绑定的租户客户；待审核时为空
    status       VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending/active/disabled
    invite_code  VARCHAR(40),
    remark       VARCHAR(500),
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE (member_id, tenant_id)
);
CREATE INDEX IF NOT EXISTS idx_binding_tenant ON member_tenant_binding(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_binding_member ON member_tenant_binding(member_id);

-- 租户渠道表在 V1 已建；这里只补商城渠道开关所需字段（幂等）
ALTER TABLE tenant_channel
    ADD COLUMN IF NOT EXISTS pay_enabled BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS credit_enabled BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
