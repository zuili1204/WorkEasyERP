-- 阶段二（9）：多单位换算在订单 / 入库 / 出库生效
-- 说明：qty、price 始终按主单位落库（库存、成本、报表口径不变）；
--       aux_unit / aux_qty / convert_rate 记录录入时的辅助单位快照，便于单据与打印回显。

ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS aux_unit     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS aux_qty      NUMERIC(14,3),
    ADD COLUMN IF NOT EXISTS convert_rate NUMERIC(14,4) DEFAULT 1;

ALTER TABLE purchase_order_item
    ADD COLUMN IF NOT EXISTS aux_unit     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS aux_qty      NUMERIC(14,3),
    ADD COLUMN IF NOT EXISTS convert_rate NUMERIC(14,4) DEFAULT 1;

ALTER TABLE purchase_inbound_item
    ADD COLUMN IF NOT EXISTS aux_unit     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS aux_qty      NUMERIC(14,3),
    ADD COLUMN IF NOT EXISTS convert_rate NUMERIC(14,4) DEFAULT 1;

ALTER TABLE sales_outbound_item
    ADD COLUMN IF NOT EXISTS aux_unit     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS aux_qty      NUMERIC(14,3),
    ADD COLUMN IF NOT EXISTS convert_rate NUMERIC(14,4) DEFAULT 1;
