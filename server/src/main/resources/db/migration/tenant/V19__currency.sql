-- 币种主数据（currency_rate 已于 V13 落地，此处补币种字典与本位币配置）

CREATE TABLE currency (
    code           VARCHAR(8) PRIMARY KEY,
    name           VARCHAR(40) NOT NULL,
    symbol         VARCHAR(8),
    is_base        BOOLEAN DEFAULT false,   -- 本位币（全表唯一）
    enabled        BOOLEAN DEFAULT true,
    decimal_places INT DEFAULT 2,
    created_at     TIMESTAMPTZ DEFAULT now()
);

INSERT INTO currency (code, name, symbol, is_base, enabled, decimal_places) VALUES
('CNY', '人民币',   '¥',  true,  true, 2),
('USD', '美元',     '$',  false, true, 2),
('EUR', '欧元',     '€',  false, true, 2),
('HKD', '港币',     'HK$',false, true, 2),
('JPY', '日元',     '¥',  false, true, 0)
ON CONFLICT (code) DO NOTHING;

-- 演示汇率（便于直接看到换算效果）
INSERT INTO currency_rate (currency_from, currency_to, rate, rate_date, source) VALUES
('USD', 'CNY', 7.1200, CURRENT_DATE, 'manual'),
('EUR', 'CNY', 7.8500, CURRENT_DATE, 'manual'),
('HKD', 'CNY', 0.9120, CURRENT_DATE, 'manual'),
('JPY', 'CNY', 0.0468, CURRENT_DATE, 'manual')
ON CONFLICT (currency_from, currency_to, rate_date) DO NOTHING;
