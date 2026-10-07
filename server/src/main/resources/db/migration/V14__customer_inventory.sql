-- M2 客户与库存（对应《数据库表结构设计.md》§6 §9）

CREATE TABLE customer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(30) UNIQUE,
    name            VARCHAR(100) NOT NULL,
    short_name      VARCHAR(50),
    type            VARCHAR(20) DEFAULT 'trade', -- trade/distributor/end
    industry        VARCHAR(50),
    level           VARCHAR(10),                 -- A/B/C
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_mobile  VARCHAR(20),
    contact_email   VARCHAR(100),
    address         VARCHAR(200),
    ship_address    VARCHAR(200),                -- 收货地址
    tax_no          VARCHAR(30),                 -- 税号
    invoice_title   VARCHAR(100),                -- 开票抬头
    invoice_bank    VARCHAR(50),
    invoice_account VARCHAR(40),
    credit_limit    NUMERIC(18,2) DEFAULT 0,     -- 授信额度
    credit_used     NUMERIC(18,2) DEFAULT 0,     -- 已用授信（建议视图计算）
    payment_terms   INT DEFAULT 0,               -- 账期天数
    settle_type     VARCHAR(20),                 -- monthly/cash
    currency        VARCHAR(8) DEFAULT 'CNY',
    owner_id        UUID REFERENCES employee(id),-- 负责销售（数据范围 owner）
    dept_id         UUID REFERENCES department(id),
    status          VARCHAR(20) DEFAULT 'active',
    remark          VARCHAR(500),
    ext             JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_cust_owner ON customer(owner_id);
CREATE INDEX idx_cust_dept ON customer(dept_id);

-- 库存现量（按 商品+仓库 唯一）
CREATE TABLE inventory (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID REFERENCES product(id),
    warehouse_id   UUID REFERENCES warehouse(id),
    qty            NUMERIC(14,3) DEFAULT 0,       -- 现量
    locked_qty     NUMERIC(14,3) DEFAULT 0,       -- 锁定（已售未发）
    available_qty  NUMERIC(14,3) GENERATED ALWAYS AS (qty - locked_qty) STORED, -- 可用
    avg_cost       NUMERIC(16,4) DEFAULT 0,       -- 移动平均成本
    last_cost      NUMERIC(16,4) DEFAULT 0,       -- 最近成本
    safety_stock   NUMERIC(14,3) DEFAULT 0,
    last_in_at     TIMESTAMPTZ,
    last_out_at    TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ DEFAULT now(),
    UNIQUE (product_id, warehouse_id)
);

-- 库存流水（出入库凭证）
CREATE TABLE inventory_txn (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    txn_no        VARCHAR(40) UNIQUE,
    txn_type      VARCHAR(20) NOT NULL,          -- in/out/transfer/adjust/initial
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100),
    warehouse_id  UUID REFERENCES warehouse(id),
    location      VARCHAR(50),                   -- 库位
    batch_no      VARCHAR(40),                   -- 批次
    unit          VARCHAR(10),
    qty           NUMERIC(14,3) NOT NULL,        -- 正入负出
    price         NUMERIC(16,4) DEFAULT 0,       -- 单价
    amount        NUMERIC(18,2) DEFAULT 0,
    balance_qty   NUMERIC(14,3),                 -- 结存数量
    balance_cost  NUMERIC(18,2),                 -- 结存成本
    ref_type      VARCHAR(20),                   -- purchase/sales/adjust
    ref_id        UUID,
    ref_no        VARCHAR(40),
    operator_id   UUID REFERENCES users(id),
    remark        VARCHAR(200),
    created_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_invtxn_prod ON inventory_txn(product_id, warehouse_id, created_at);

-- 客户可用授信视图（对应文档 §14）
CREATE OR REPLACE VIEW v_customer_credit AS
SELECT c.id, c.name, c.credit_limit,
       COALESCE(c.credit_used, 0) AS used_credit,
       c.credit_limit - COALESCE(c.credit_used, 0) AS available_credit
FROM customer c
WHERE c.deleted_at IS NULL;
