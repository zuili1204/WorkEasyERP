-- 阶段二（10）：多单位换算在库存流水生效
-- 库存流水仍以主单位记录 qty / price / amount / balance_*；
-- 另存录入单位快照，便于流水查询、对账与打印回显（例：10 箱 = 120 件）。

ALTER TABLE inventory_txn
    ADD COLUMN IF NOT EXISTS aux_unit     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS aux_qty      NUMERIC(14,3),
    ADD COLUMN IF NOT EXISTS convert_rate NUMERIC(14,4) DEFAULT 1;
