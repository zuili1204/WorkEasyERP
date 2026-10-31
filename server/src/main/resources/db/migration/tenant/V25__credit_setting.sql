-- 阶段二（6）：授信闭环
-- 1) 租户级设置表（当前用于授信超限策略 credit.overflow_mode）
-- 2) 销售订单标记是否超授信，供审批页提示经理重点审核

CREATE TABLE IF NOT EXISTS sys_setting (
    key        VARCHAR(64) PRIMARY KEY,
    value      VARCHAR(500) NOT NULL,
    remark     VARCHAR(200),
    updated_at TIMESTAMPTZ DEFAULT now()
);

INSERT INTO sys_setting(key, value, remark)
VALUES ('credit.overflow_mode', 'approval', '授信超限处理：approval=转经理审批放行，block=直接拒绝')
ON CONFLICT (key) DO NOTHING;

ALTER TABLE sales_order
    ADD COLUMN IF NOT EXISTS credit_over BOOLEAN DEFAULT false;
