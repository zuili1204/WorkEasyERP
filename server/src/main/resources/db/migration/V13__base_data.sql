-- 贸易基础数据（对应《数据库表结构设计.md》§6）：分类 / 商品 / 供应商 / 仓库 / 汇率
-- 为 M2 进销存做准备；client 侧「基础数据」页面即维护这些表

CREATE TABLE product_category (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(30) UNIQUE,
    name       VARCHAR(100) NOT NULL,
    parent_id  UUID REFERENCES product_category(id),
    sort_no    INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE product (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku            VARCHAR(40) UNIQUE,
    barcode        VARCHAR(40),
    name           VARCHAR(100) NOT NULL,
    short_name     VARCHAR(50),
    spec           VARCHAR(100),                -- 规格
    brand          VARCHAR(50),
    origin         VARCHAR(50),                 -- 产地
    category_id    UUID REFERENCES product_category(id),
    unit           VARCHAR(10),                 -- 主单位
    aux_unit       VARCHAR(10),                 -- 辅助单位
    convert_rate   NUMERIC(14,4) DEFAULT 1,     -- 主单位/辅助单位 换算率
    purchase_price NUMERIC(16,4),               -- 采购价（参考）
    sale_price     NUMERIC(16,4),               -- 标准售价
    cost_price     NUMERIC(16,4) DEFAULT 0,     -- 当前成本（移动加权，冗余自 inventory）
    tax_rate       NUMERIC(6,4) DEFAULT 0.13,   -- 默认税率
    status         VARCHAR(20) DEFAULT 'active',
    remark         VARCHAR(500),
    ext            JSONB,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

CREATE TABLE supplier (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(30) UNIQUE,
    name            VARCHAR(100) NOT NULL,
    short_name      VARCHAR(50),
    type            VARCHAR(20),
    level           VARCHAR(10),
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_mobile  VARCHAR(20),
    contact_email   VARCHAR(100),
    address         VARCHAR(200),
    tax_no          VARCHAR(30),
    invoice_title   VARCHAR(100),
    bank_name       VARCHAR(50),
    bank_account    VARCHAR(40),
    payment_terms   INT DEFAULT 0,
    settle_type     VARCHAR(20),
    currency        VARCHAR(8) DEFAULT 'CNY',
    status          VARCHAR(20) DEFAULT 'active',
    remark          VARCHAR(500),
    ext             JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE warehouse (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20) UNIQUE,
    name        VARCHAR(80) NOT NULL,
    location    VARCHAR(100),
    type        VARCHAR(20) DEFAULT 'normal',   -- normal/bonded
    manager_id  UUID REFERENCES employee(id),   -- 库管
    status      VARCHAR(20) DEFAULT 'active',
    remark      VARCHAR(200),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE currency_rate (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_from VARCHAR(8) NOT NULL,
    currency_to   VARCHAR(8) DEFAULT 'CNY',
    rate          NUMERIC(12,6) NOT NULL,
    rate_date     DATE NOT NULL,
    source        VARCHAR(20),
    created_at    TIMESTAMPTZ DEFAULT now(),
    UNIQUE (currency_from, currency_to, rate_date)
);
