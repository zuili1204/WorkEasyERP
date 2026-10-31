-- 阶段三：B2B 订货商城（渠道态数据）
-- 价格 / 库存 / 授信 / 应收的权威计算仍在 ERP 侧，商城表只保存渠道状态。
-- 全部语句幂等，存量租户库可直接补跑。

CREATE TABLE IF NOT EXISTS mall_member_profile (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID NOT NULL UNIQUE,               -- 平台会员 id（平台库）
    customer_id       UUID REFERENCES customer(id),
    company_name      VARCHAR(120),
    contact_name      VARCHAR(50),
    contact_phone     VARCHAR(20),
    default_address_id UUID,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mall_address (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   UUID NOT NULL,
    receiver    VARCHAR(50) NOT NULL,
    phone       VARCHAR(20) NOT NULL,
    province    VARCHAR(50),
    city        VARCHAR(50),
    district    VARCHAR(50),
    detail      VARCHAR(200) NOT NULL,
    is_default  BOOLEAN DEFAULT false,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mall_address_member ON mall_address(member_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mall_cart_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   UUID NOT NULL,
    product_id  UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    qty         NUMERIC(14,3) NOT NULL,                   -- 主单位数量
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE (member_id, product_id)
);

CREATE TABLE IF NOT EXISTS mall_order (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no          VARCHAR(40) UNIQUE NOT NULL,
    member_id         UUID NOT NULL,
    customer_id       UUID NOT NULL REFERENCES customer(id),
    customer_name     VARCHAR(100),
    status            VARCHAR(20) NOT NULL DEFAULT 'pending_pay',
    -- pending_pay/pending_confirm/pending_ship/shipped/completed/cancelled/closed
    pay_type          VARCHAR(20) NOT NULL DEFAULT 'wechat',  -- wechat/credit/offline
    total_qty         NUMERIC(14,3) DEFAULT 0,
    total_net         NUMERIC(18,2) DEFAULT 0,
    total_tax         NUMERIC(18,2) DEFAULT 0,
    total_amount      NUMERIC(18,2) DEFAULT 0,
    warehouse_id      UUID REFERENCES warehouse(id),
    receiver          VARCHAR(50),
    receiver_phone    VARCHAR(20),
    address           VARCHAR(300),
    remark            VARCHAR(500),
    sales_order_id    UUID REFERENCES sales_order(id),
    out_trade_no      VARCHAR(64),
    credit_over       BOOLEAN DEFAULT false,
    paid_at           TIMESTAMPTZ,
    shipped_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mall_order_member ON mall_order(member_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mall_order_status ON mall_order(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mall_order_so ON mall_order(sales_order_id);

CREATE TABLE IF NOT EXISTS mall_order_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES mall_order(id) ON DELETE CASCADE,
    line_no       INT NOT NULL,
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100) NOT NULL,
    spec          VARCHAR(100),
    unit          VARCHAR(10),
    qty           NUMERIC(14,3) NOT NULL,
    price         NUMERIC(16,4) DEFAULT 0,
    tax_rate      NUMERIC(6,4) DEFAULT 0,
    net_amount    NUMERIC(18,2) DEFAULT 0,
    tax_amount    NUMERIC(18,2) DEFAULT 0,
    amount        NUMERIC(18,2) DEFAULT 0,
    image_url     VARCHAR(500),
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mall_order_item_order ON mall_order_item(order_id);

CREATE TABLE IF NOT EXISTS mall_payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_no      VARCHAR(40) UNIQUE,
    order_id        UUID NOT NULL REFERENCES mall_order(id),
    member_id       UUID NOT NULL,
    out_trade_no    VARCHAR(64) NOT NULL UNIQUE,           -- 幂等键
    provider        VARCHAR(20) NOT NULL DEFAULT 'wechat', -- wechat/offline/manual
    amount          NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',-- pending/success/failed/refunded
    transaction_id  VARCHAR(64),
    raw             JSONB,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mall_payment_order ON mall_payment(order_id);

CREATE TABLE IF NOT EXISTS mall_refund (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_no       VARCHAR(40) UNIQUE NOT NULL,
    order_id        UUID NOT NULL REFERENCES mall_order(id),
    member_id       UUID NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'applied', -- applied/approved/rejected/done
    sales_return_id UUID REFERENCES sales_return(id),
    remark          VARCHAR(500),
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mall_refund_order ON mall_refund(order_id);

CREATE TABLE IF NOT EXISTS mall_config (
    key        VARCHAR(64) PRIMARY KEY,
    value      VARCHAR(500) NOT NULL,
    remark     VARCHAR(200),
    updated_at TIMESTAMPTZ DEFAULT now()
);

INSERT INTO mall_config(key, value, remark) VALUES
    ('default_warehouse_id', '', '商城默认发货仓（为空时取第一个启用仓库）'),
    ('notice', '', '首页公告'),
    ('service_phone', '', '客服电话'),
    ('credit_enabled', 'true', '是否开放账期支付'),
    ('wechat_enabled', 'false', '是否开放微信支付（需租户商户号授权）'),
    ('auto_confirm_days', '7', '自动确认收货天数'),
    ('bind_auto_approve', 'false', '邀请码加入是否自动通过'),
    ('min_order_amount', '0', '起送金额（0=不限）')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS mall_banner (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(100),
    image_url   VARCHAR(500) NOT NULL,
    link_url    VARCHAR(500),
    sort_no     INT DEFAULT 0,
    status      VARCHAR(20) DEFAULT 'active',   -- active/inactive
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mall_invite (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(40) UNIQUE NOT NULL,
    customer_id UUID NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    owner_id    UUID REFERENCES employee(id),
    status      VARCHAR(20) DEFAULT 'active',   -- active/disabled
    remark      VARCHAR(200),
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- 销售订单渠道标记：erp=线下/员工建单，mall=商城来源
ALTER TABLE sales_order
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20) DEFAULT 'erp',
    ADD COLUMN IF NOT EXISTS mall_order_id UUID;
CREATE INDEX IF NOT EXISTS idx_so_channel ON sales_order(channel);
