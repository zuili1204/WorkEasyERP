-- M2 履约单据 + 财务台账（对应《数据库表结构设计.md》§9.1 §10 §10.1）
-- 说明：订单表（purchase_order / sales_order）尚未建立，故履约单据的 order_id 暂不加外键，
--       待订单表落地后再补 ALTER TABLE 添加约束。

CREATE TABLE purchase_inbound (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_no      VARCHAR(40) UNIQUE NOT NULL,
    order_id        UUID,                         -- 关联采购订单（订单表落地后补外键）
    supplier_id     UUID REFERENCES supplier(id),
    supplier_name   VARCHAR(100),                 -- 快照
    warehouse_id    UUID REFERENCES warehouse(id),
    inbound_date    DATE DEFAULT CURRENT_DATE,
    status          VARCHAR(20) DEFAULT 'draft',  -- draft/partial/done/void
    total_qty       NUMERIC(14,3) DEFAULT 0,
    total_amount    NUMERIC(18,2) DEFAULT 0,
    remark          TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_pin_order ON purchase_inbound(order_id);

CREATE TABLE purchase_inbound_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_id    UUID NOT NULL REFERENCES purchase_inbound(id) ON DELETE CASCADE,
    order_item_id UUID,
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100),                   -- 快照
    unit          VARCHAR(10),
    qty           NUMERIC(14,3) NOT NULL,         -- 本次入库数量
    price         NUMERIC(16,4),                  -- 单价（不含税）
    tax_rate      NUMERIC(6,4) DEFAULT 0,
    amount        NUMERIC(18,2),
    batch_no      VARCHAR(40),
    location      VARCHAR(50),
    remark        VARCHAR(500)
);
CREATE INDEX idx_pini_inbound ON purchase_inbound_item(inbound_id);

CREATE TABLE sales_outbound (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_no    VARCHAR(40) UNIQUE NOT NULL,
    order_id       UUID,                          -- 关联销售订单（订单表落地后补外键）
    customer_id    UUID REFERENCES customer(id),
    customer_name  VARCHAR(100),                  -- 快照
    warehouse_id   UUID REFERENCES warehouse(id),
    outbound_date  DATE DEFAULT CURRENT_DATE,
    status         VARCHAR(20) DEFAULT 'draft',   -- draft/partial/done/void
    total_qty      NUMERIC(14,3) DEFAULT 0,
    total_amount   NUMERIC(18,2) DEFAULT 0,
    remark         TEXT,
    created_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_sout_order ON sales_outbound(order_id);

CREATE TABLE sales_outbound_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    outbound_id   UUID NOT NULL REFERENCES sales_outbound(id) ON DELETE CASCADE,
    order_item_id UUID,
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100),                   -- 快照
    unit          VARCHAR(10),
    qty           NUMERIC(14,3) NOT NULL,         -- 本次出库数量
    price         NUMERIC(16,4),
    tax_rate      NUMERIC(6,4) DEFAULT 0,
    amount        NUMERIC(18,2),
    batch_no      VARCHAR(40),
    location      VARCHAR(50),
    remark        VARCHAR(500)
);
CREATE INDEX idx_souti_outbound ON sales_outbound_item(outbound_id);

-- 应收 / 应付台账
CREATE TABLE ar_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID REFERENCES customer(id),
    biz_type        VARCHAR(20) NOT NULL,         -- sales_outbound/return
    biz_id          UUID,
    biz_no          VARCHAR(40),
    currency        VARCHAR(8) DEFAULT 'CNY',
    amount          NUMERIC(18,2) NOT NULL,       -- 应收发生额
    settled_amount  NUMERIC(18,2) DEFAULT 0,      -- 已核销
    remain_amount   NUMERIC(18,2) DEFAULT 0,      -- 余额
    due_date        DATE,
    status          VARCHAR(20) DEFAULT 'open',   -- open/partial/closed
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_ar_cust ON ar_ledger(customer_id, status);

CREATE TABLE ap_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id     UUID REFERENCES supplier(id),
    biz_type        VARCHAR(20) NOT NULL,
    biz_id          UUID,
    biz_no          VARCHAR(40),
    currency        VARCHAR(8) DEFAULT 'CNY',
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) DEFAULT 0,
    remain_amount   NUMERIC(18,2) DEFAULT 0,
    due_date        DATE,
    status          VARCHAR(20) DEFAULT 'open',
    created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_ap_sup ON ap_ledger(supplier_id, status);

-- 收付款与核销明细
CREATE TABLE payment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_no        VARCHAR(40) UNIQUE,
    type              VARCHAR(20) NOT NULL,       -- receive(收)/pay(付)
    counterparty_id   UUID,
    counterparty_type VARCHAR(20),                -- customer/supplier
    counterparty_name VARCHAR(100),               -- 快照
    amount            NUMERIC(18,2) DEFAULT 0,
    currency          VARCHAR(8) DEFAULT 'CNY',
    pay_method        VARCHAR(20),                -- cash/bank/acceptance
    status            VARCHAR(20) DEFAULT 'done',
    pay_date          DATE DEFAULT CURRENT_DATE,
    operator_id       UUID REFERENCES users(id),
    remark            VARCHAR(500),
    created_at        TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE payment_writeoff (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID REFERENCES payment(id) ON DELETE CASCADE,
    ledger_type     VARCHAR(10) NOT NULL,         -- ar/ap
    ledger_id       UUID NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,       -- 本次核销金额
    writeoff_at     TIMESTAMPTZ DEFAULT now(),
    operator_id     UUID REFERENCES users(id)
);
CREATE INDEX idx_wo_payment ON payment_writeoff(payment_id);
