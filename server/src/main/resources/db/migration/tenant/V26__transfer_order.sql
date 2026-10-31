-- 阶段二（7）：调拨单
-- 审核后两仓原子过账：同一事务内两条 inventory_txn（调出为负、调入为正），两仓数量合计守恒。

CREATE TABLE IF NOT EXISTS transfer_order (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_no         VARCHAR(40) UNIQUE,
    from_warehouse_id   UUID NOT NULL REFERENCES warehouse(id),
    from_warehouse_name VARCHAR(80),
    to_warehouse_id     UUID NOT NULL REFERENCES warehouse(id),
    to_warehouse_name   VARCHAR(80),
    status              VARCHAR(20) DEFAULT 'draft',   -- draft/done/void
    total_qty           NUMERIC(14,3) DEFAULT 0,
    remark              VARCHAR(500),
    created_by          UUID REFERENCES users(id),
    audited_by          UUID REFERENCES users(id),
    audited_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ DEFAULT now(),
    updated_at          TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_transfer_status ON transfer_order(status, created_at DESC);

CREATE TABLE IF NOT EXISTS transfer_order_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL REFERENCES transfer_order(id) ON DELETE CASCADE,
    product_id   UUID NOT NULL REFERENCES product(id),
    product_name VARCHAR(100),
    unit         VARCHAR(10),
    qty          NUMERIC(14,3) NOT NULL,
    cost         NUMERIC(16,4) DEFAULT 0,   -- 审核时记录的转出成本（移动加权）
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_transfer_item_order ON transfer_order_item(order_id);
