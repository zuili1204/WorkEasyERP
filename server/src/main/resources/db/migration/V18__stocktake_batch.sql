-- 盘点与批次库位（对应《数据库表结构设计.md》§16.1.3）

CREATE TABLE stocktake (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no          VARCHAR(32) UNIQUE,                     -- PD{yyyyMMdd}{seq}
    warehouse_id UUID REFERENCES warehouse(id),
    take_date   DATE DEFAULT CURRENT_DATE,
    status      VARCHAR(16) DEFAULT 'draft',            -- draft/counting/adjusted/void
    diff_qty    NUMERIC(14,3) DEFAULT 0,                -- 差异合计（正=盘盈，负=盘亏）
    remark      TEXT,
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_st_wh ON stocktake(warehouse_id);

CREATE TABLE stocktake_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stocktake_id  UUID REFERENCES stocktake(id) ON DELETE CASCADE,
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100),
    unit          VARCHAR(10),
    batch_no      VARCHAR(32),
    location_code VARCHAR(32),
    book_qty      NUMERIC(14,3) DEFAULT 0,              -- 账面
    actual_qty    NUMERIC(14,3),                        -- 实盘
    diff_qty      NUMERIC(14,3) DEFAULT 0,              -- 差异；审核后生成 inventory_txn(type='adjust')
    cost_price    NUMERIC(16,4) DEFAULT 0
);
CREATE INDEX idx_sti_st ON stocktake_item(stocktake_id);

CREATE TABLE inventory_batch (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_no      VARCHAR(32) NOT NULL,                 -- B{yyyyMMdd}{seq}
    product_id    UUID REFERENCES product(id),
    product_name  VARCHAR(100),
    warehouse_id  UUID REFERENCES warehouse(id),
    location_code VARCHAR(32),                          -- 库位编码，如 A-01-03
    qty           NUMERIC(14,3) DEFAULT 0,
    cost_price    NUMERIC(16,4) DEFAULT 0,
    inbound_date  DATE DEFAULT CURRENT_DATE,
    product_date  DATE,
    expire_date   DATE,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_batch_no ON inventory_batch(batch_no);
CREATE INDEX idx_batch_loc ON inventory_batch(location_code);
CREATE INDEX idx_batch_product ON inventory_batch(product_id);
