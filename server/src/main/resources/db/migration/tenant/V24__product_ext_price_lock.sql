-- 阶段二（3–5）：商品扩展 + 价格体系 + 库存锁定
-- 沿用「一行 = 一个 SKU」，不引入 SPU；所有语句幂等，便于存量库补跑。

-- 商品线上属性（商城展示与起订约束）
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS online_status VARCHAR(20) DEFAULT 'offline',  -- online/offline
    ADD COLUMN IF NOT EXISTS online_title  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS online_desc   TEXT,
    ADD COLUMN IF NOT EXISTS min_order_qty NUMERIC(14,3) DEFAULT 1,        -- 起订量
    ADD COLUMN IF NOT EXISTS step_qty      NUMERIC(14,3) DEFAULT 1,        -- 购买步长
    ADD COLUMN IF NOT EXISTS min_sale_price NUMERIC(16,4) DEFAULT 0;       -- 最低售价（0=不限制）

-- 商品图片（主图置顶，按 sort_no 排序）
CREATE TABLE IF NOT EXISTS product_image (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    sort_no    INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_product_image ON product_image(product_id, sort_no);

-- 等级价：A/B/C 客户等级对应价格
CREATE TABLE IF NOT EXISTS product_price (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    price_level VARCHAR(2) NOT NULL,              -- A/B/C
    price       NUMERIC(16,4) NOT NULL,
    status      VARCHAR(20) DEFAULT 'active',
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE (product_id, price_level)
);

-- 协议价：按客户+商品，支持生效期（同日多档时取最新维护的一条）
CREATE TABLE IF NOT EXISTS customer_price (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    product_id   UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    price        NUMERIC(16,4) NOT NULL,
    start_date   DATE,
    end_date     DATE,
    status       VARCHAR(20) DEFAULT 'active',
    remark       VARCHAR(200),
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_customer_price_lookup ON customer_price(customer_id, product_id);
