-- M2 订单层 + 退货 + 发票（对应《数据库表结构设计.md》§7 §8 §9.1 §10 §16.1.2）
-- 订单与履约分离：订单表达"意向与条款"，收发由执行单据承载并回写 received_qty / shipped_qty。

CREATE TABLE purchase_order (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE NOT NULL,
    supplier_id          UUID REFERENCES supplier(id),
    supplier_name        VARCHAR(100),                  -- 快照
    dept_id              UUID REFERENCES department(id),
    owner_id             UUID REFERENCES employee(id),  -- 采购员
    order_date           DATE DEFAULT CURRENT_DATE,
    expect_date          DATE,                          -- 到货日期
    currency             VARCHAR(8) DEFAULT 'CNY',
    pay_terms            VARCHAR(30),
    warehouse_id         UUID REFERENCES warehouse(id), -- 收货仓
    status               VARCHAR(20) DEFAULT 'draft',   -- draft/approved/received/partial/closed/void
    total_qty            NUMERIC(14,3) DEFAULT 0,
    total_net            NUMERIC(18,2) DEFAULT 0,       -- 不含税合计
    total_tax            NUMERIC(18,2) DEFAULT 0,       -- 税额合计
    total_amount         NUMERIC(18,2) DEFAULT 0,       -- 价税合计
    remark               TEXT,
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_po_dept ON purchase_order(dept_id);

CREATE TABLE purchase_order_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    line_no         INT NOT NULL,
    product_id      UUID REFERENCES product(id),
    product_name    VARCHAR(100) NOT NULL,            -- 快照
    spec            VARCHAR(100),
    unit            VARCHAR(10),
    qty             NUMERIC(14,3) NOT NULL DEFAULT 0,
    price           NUMERIC(16,4) DEFAULT 0,          -- 采购单价（不含税）
    tax_rate        NUMERIC(6,4) DEFAULT 0,
    tax_amount      NUMERIC(16,2) DEFAULT 0,
    net_amount      NUMERIC(18,2) DEFAULT 0,
    amount          NUMERIC(18,2) DEFAULT 0,          -- 价税合计
    received_qty    NUMERIC(14,3) DEFAULT 0,          -- 已收数量（履约回写）
    expect_date     DATE,
    warehouse_id    UUID REFERENCES warehouse(id),
    remark          VARCHAR(500),
    ext             JSONB
);
CREATE INDEX idx_poi_order ON purchase_order_item(order_id);

CREATE TABLE sales_order (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no             VARCHAR(40) UNIQUE NOT NULL,
    customer_id          UUID REFERENCES customer(id),
    customer_name        VARCHAR(100),                 -- 快照
    contract_no          VARCHAR(40),
    dept_id              UUID REFERENCES department(id),
    owner_id             UUID REFERENCES employee(id), -- 业务员（数据范围 owner）
    order_date           DATE DEFAULT CURRENT_DATE,
    deliver_date         DATE,                         -- 交期
    currency             VARCHAR(8) DEFAULT 'CNY',
    pay_terms            VARCHAR(30),
    credit_days          INT DEFAULT 0,                -- 账期天数
    due_date             DATE,
    status               VARCHAR(20) DEFAULT 'draft',  -- draft/approved/shipped/partial/closed/void
    total_qty            NUMERIC(14,3) DEFAULT 0,
    total_net            NUMERIC(18,2) DEFAULT 0,
    total_tax            NUMERIC(18,2) DEFAULT 0,
    total_amount         NUMERIC(18,2) DEFAULT 0,      -- 价税合计
    total_cost           NUMERIC(18,2) DEFAULT 0,      -- 总成本
    total_profit         NUMERIC(18,2) DEFAULT 0,      -- 总毛利 = 不含税 - 成本
    profit_rate          NUMERIC(6,4) DEFAULT 0,
    receivable           NUMERIC(18,2) DEFAULT 0,      -- 应收
    received             NUMERIC(18,2) DEFAULT 0,      -- 已收
    remark               TEXT,
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ DEFAULT now(),
    updated_at           TIMESTAMPTZ DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_so_owner ON sales_order(owner_id);
CREATE INDEX idx_so_cust ON sales_order(customer_id);

CREATE TABLE sales_order_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    line_no         INT NOT NULL,
    product_id      UUID REFERENCES product(id),
    product_name    VARCHAR(100) NOT NULL,            -- 快照
    spec            VARCHAR(100),
    unit            VARCHAR(10),
    qty             NUMERIC(14,3) NOT NULL DEFAULT 0,
    price           NUMERIC(16,4) DEFAULT 0,          -- 销售单价（不含税）
    tax_rate        NUMERIC(6,4) DEFAULT 0,
    tax_amount      NUMERIC(16,2) DEFAULT 0,
    net_amount      NUMERIC(18,2) DEFAULT 0,
    amount          NUMERIC(18,2) DEFAULT 0,
    cost_price      NUMERIC(16,4) DEFAULT 0,          -- 成本单价（冻结：下单时加权成本快照）
    cost_amount     NUMERIC(18,2) DEFAULT 0,
    profit          NUMERIC(18,2) DEFAULT 0,          -- 毛利 = 不含税净额 - 成本
    profit_rate     NUMERIC(6,4) DEFAULT 0,
    shipped_qty     NUMERIC(14,3) DEFAULT 0,          -- 已发数量（履约回写）
    unship_qty      NUMERIC(14,3) DEFAULT 0,          -- 未发数量
    deliver_date    DATE,
    warehouse_id    UUID REFERENCES warehouse(id),
    remark          VARCHAR(500),
    ext             JSONB
);
CREATE INDEX idx_soi_order ON sales_order_item(order_id);

-- 采购退货（红冲应付、回库/扣减库存）
CREATE TABLE purchase_return (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no                 VARCHAR(32) UNIQUE,             -- PRT{yyyyMMdd}{seq}
    purchase_order_id  UUID REFERENCES purchase_order(id),
    supplier_id        UUID REFERENCES supplier(id),
    warehouse_id       UUID REFERENCES warehouse(id),
    return_date        DATE DEFAULT CURRENT_DATE,
    total_amount       NUMERIC(18,2) DEFAULT 0,
    status             VARCHAR(16) DEFAULT 'draft',     -- draft/done/void
    ap_offset_amount   NUMERIC(18,2) DEFAULT 0,         -- 已红冲应付金额
    remark             TEXT,
    created_by         UUID REFERENCES users(id),
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);
CREATE TABLE purchase_return_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id   UUID REFERENCES purchase_return(id) ON DELETE CASCADE,
    product_id  UUID REFERENCES product(id),
    qty         NUMERIC(14,3) NOT NULL,
    cost_price  NUMERIC(16,4),
    amount      NUMERIC(18,2),
    batch_no    VARCHAR(32)
);

-- 销售退货（红冲应收、回库）
CREATE TABLE sales_return (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_no    VARCHAR(40) UNIQUE NOT NULL,
    order_id     UUID REFERENCES sales_order(id),
    outbound_id  UUID REFERENCES sales_outbound(id),
    customer_id  UUID REFERENCES customer(id),
    warehouse_id UUID REFERENCES warehouse(id),
    return_date  DATE DEFAULT CURRENT_DATE,
    reason       TEXT,
    status       VARCHAR(20) DEFAULT 'draft',          -- draft/done/void
    total_qty    NUMERIC(14,3) DEFAULT 0,
    total_amount NUMERIC(18,2) DEFAULT 0,
    ar_offset_amount NUMERIC(18,2) DEFAULT 0,          -- 已红冲应收金额
    created_by   UUID REFERENCES users(id),
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE sales_return_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id   UUID REFERENCES sales_return(id) ON DELETE CASCADE,
    product_id  UUID REFERENCES product(id),
    product_name VARCHAR(100),
    unit        VARCHAR(10),
    qty         NUMERIC(14,3) NOT NULL,
    price       NUMERIC(16,4) DEFAULT 0,               -- 退货单价（不含税）
    tax_rate    NUMERIC(6,4) DEFAULT 0,
    amount      NUMERIC(18,2) DEFAULT 0                -- 价税合计（红冲金额）
);

-- 发票（进/销项）
CREATE TABLE invoice (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_no     VARCHAR(40) UNIQUE,
    type           VARCHAR(20) NOT NULL,          -- sales(销项)/purchase(进项)
    invoice_type   VARCHAR(20),                   -- special/normal/electronic
    buyer_name     VARCHAR(100),
    buyer_tax_no   VARCHAR(30),
    seller_name    VARCHAR(100),
    seller_tax_no  VARCHAR(30),
    amount         NUMERIC(18,2) DEFAULT 0,       -- 价税合计
    net_amount     NUMERIC(18,2) DEFAULT 0,
    tax_amount     NUMERIC(18,2) DEFAULT 0,
    tax_rate       NUMERIC(6,4) DEFAULT 0,
    currency       VARCHAR(8) DEFAULT 'CNY',
    status         VARCHAR(20) DEFAULT 'issued',  -- issued/void/red
    issued_at      DATE DEFAULT CURRENT_DATE,
    biz_type       VARCHAR(20),
    biz_id         UUID,
    created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_inv_biz ON invoice(biz_type, biz_id);
