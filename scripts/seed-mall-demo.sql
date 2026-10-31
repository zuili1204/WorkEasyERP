-- 商城演示种子数据（仅用于本地/演示租户库，不放进 Flyway 迁移，避免生产租户被灌入演示数据）
-- 用法（default 租户库）：
--   docker cp scripts/seed-mall-demo.sql workeasy-pg:/tmp/seed.sql
--   docker exec -e PGPASSWORD=workeasy123 workeasy-pg psql -U workeasy -d workeasy -f /tmp/seed.sql
-- 幂等：重复执行只会刷新演示商品/价格/配置，不会重复插入库存与图片。
-- 演示邀请码：DEMO2026（配合 mall_config.bind_auto_approve=true，会员加入即自动通过）

-- 1. 仓库
INSERT INTO warehouse(code, name, location, type, status)
VALUES ('WH-DEMO', '演示主仓', '演示中心仓', 'normal', 'active')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, status = 'active';

-- 2. 商品分类
INSERT INTO product_category(code, name, sort_no)
VALUES ('DM-BEV', '饮料酒水', 1)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
INSERT INTO product_category(code, name, sort_no)
VALUES ('DM-SNK', '休闲零食', 2)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
INSERT INTO product_category(code, name, sort_no)
VALUES ('DM-DLY', '日用百货', 3)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
INSERT INTO product_category(code, name, sort_no)
VALUES ('DM-OFF', '办公耗材', 4)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

-- 3. 演示客户（授信 2 万、账期 30 天，便于演示账期下单）
INSERT INTO customer(code, name, short_name, type, level, contact_name, contact_phone, contact_mobile,
                     credit_limit, credit_used, payment_terms, status)
VALUES ('DEMO001', '演示客户·鲜果商贸', '鲜果商贸', 'trade', 'A', '王采购', '0755-88880000', '13800001111',
        20000, 0, 30, 'active')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, level = 'A', status = 'active';

-- 4. 演示商品（上架 online，起订 1、步长 1）
INSERT INTO product(sku, name, short_name, spec, brand, origin, unit, tax_rate, sale_price, cost_price,
                    status, online_status, online_title, online_desc, min_order_qty, step_qty, category_id)
VALUES
 ('DEMO-001', '气泡水 500ml*24瓶', '气泡水整箱', '500ml*24', '清泉', '广东', '箱', 0.13, 89.00, 62.00,
  'active', 'online', '清泉气泡水整箱 24 瓶', '整箱起订，仓库现货，48 小时发货', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-BEV')),
 ('DEMO-002', '鲜榨橙汁 1L*6瓶', '橙汁六联装', '1L*6', '果町', '福建', '箱', 0.13, 129.00, 95.00,
  'active', 'online', '果町鲜榨橙汁 6 瓶装', '冷链直发，保质期 21 天', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-BEV')),
 ('DEMO-003', '每日坚果混合装 750g', '每日坚果', '750g/袋', '坚果部落', '浙江', '袋', 0.13, 59.90, 41.00,
  'active', 'online', '每日坚果 30 小包混合装', '独立小包装，办公零食首选', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-SNK')),
 ('DEMO-004', '手撕面包 1kg', '手撕面包', '1kg/箱', '麦香源', '江苏', '箱', 0.13, 39.90, 27.00,
  'active', 'online', '麦香源手撕面包整箱', '早餐代餐，整箱更划算', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-SNK')),
 ('DEMO-005', '抽取式纸巾 3层*24包', '抽纸整箱', '3层*24包', '洁润', '河北', '箱', 0.13, 69.00, 48.00,
  'active', 'online', '洁润抽取式纸巾整箱', '商用家用通用，整箱包邮', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-DLY')),
 ('DEMO-006', '洗衣液 2kg*4瓶', '洗衣液四联装', '2kg*4', '净白', '广州', '箱', 0.13, 79.00, 55.00,
  'active', 'online', '净白洗衣液 4 瓶装', '低泡易漂洗，适合商用洗涤', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-DLY')),
 ('DEMO-007', 'A4 复印纸 70g*5包', 'A4复印纸', '70g*5包', '优印', '山东', '箱', 0.13, 119.00, 88.00,
  'active', 'online', '优印 A4 复印纸 5 包装', '白度高，不卡纸，办公常备', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-OFF')),
 ('DEMO-008', '中性签字笔 0.5mm*12支', '签字笔', '0.5mm*12', '书写', '上海', '盒', 0.13, 24.00, 15.00,
  'active', 'online', '书写中性签字笔 12 支装', '速干墨水，会议办公通用', 1, 1,
  (SELECT id FROM product_category WHERE code = 'DM-OFF'))
ON CONFLICT (sku) DO UPDATE SET
    name = EXCLUDED.name,
    spec = EXCLUDED.spec,
    unit = EXCLUDED.unit,
    sale_price = EXCLUDED.sale_price,
    cost_price = EXCLUDED.cost_price,
    tax_rate = EXCLUDED.tax_rate,
    status = 'active',
    online_status = 'online',
    online_title = EXCLUDED.online_title,
    online_desc = EXCLUDED.online_desc,
    min_order_qty = 1,
    step_qty = 1,
    category_id = EXCLUDED.category_id,
    deleted_at = NULL;

-- 5. 库存（已存在则不动，避免覆盖真实库存与锁定量）
INSERT INTO inventory(product_id, warehouse_id, qty, locked_qty, avg_cost, last_cost, safety_stock)
SELECT p.id, w.id, s.qty, 0, s.cost, s.cost, 0
FROM (VALUES
    ('DEMO-001', 800::numeric, 62.00::numeric),
    ('DEMO-002', 500, 95.00),
    ('DEMO-003', 300, 41.00),
    ('DEMO-004', 200, 27.00),
    ('DEMO-005', 120, 48.00),
    ('DEMO-006', 100, 55.00),
    ('DEMO-007', 90, 88.00),
    ('DEMO-008', 150, 15.00)
) AS s(sku, qty, cost)
JOIN product p ON p.sku = s.sku
JOIN warehouse w ON w.code = 'WH-DEMO'
ON CONFLICT (product_id, warehouse_id) DO NOTHING;

-- 6. 商品主图（重复执行先清后插）
DELETE FROM product_image
WHERE product_id IN (SELECT id FROM product WHERE sku LIKE 'DEMO-%');

INSERT INTO product_image(product_id, url, sort_no, is_primary)
SELECT id, 'https://trae-api-cn.mchost.guru/api/ide/v1/tool_text_to_image?prompt=' ||
           CASE
             WHEN sku IN ('DEMO-001', 'DEMO-002') THEN
               'Photorealistic%20product%20photo%20of%20premium%20beverage%20bottles%20on%20clean%20white%20background%20studio%20lighting%2C%20ecommerce%20packshot'
             WHEN sku IN ('DEMO-003', 'DEMO-004') THEN
               'Photorealistic%20product%20photo%20of%20snack%20packaging%20on%20clean%20white%20background%20studio%20lighting%2C%20ecommerce%20packshot'
             WHEN sku IN ('DEMO-005', 'DEMO-006') THEN
               'Photorealistic%20product%20photo%20of%20household%20daily%20use%20products%20on%20clean%20white%20background%20studio%20lighting'
             ELSE
               'Photorealistic%20product%20photo%20of%20office%20supplies%20notebooks%20and%20pens%20on%20clean%20white%20background%20studio%20lighting'
           END ||
           '&image_size=square_hd',
       0, true
FROM product
WHERE sku LIKE 'DEMO-%';

-- 7. 演示邀请码（加入商户时绑定到演示客户）
INSERT INTO mall_invite(code, customer_id, status, remark)
SELECT 'DEMO2026', c.id, 'active', '演示邀请码'
FROM customer c WHERE c.code = 'DEMO001'
ON CONFLICT (code) DO UPDATE SET customer_id = EXCLUDED.customer_id, status = 'active';

-- 8. 首页轮播（重复执行先清后插）
DELETE FROM mall_banner WHERE title IN ('整箱批发 直降 15%', '新客首单 立减 30 元');

INSERT INTO mall_banner(title, image_url, link_url, sort_no, status) VALUES
 ('整箱批发 直降 15%',
  'https://trae-api-cn.mchost.guru/api/ide/v1/tool_text_to_image?prompt=Modern%20B2B%20wholesale%20ordering%20promotion%20banner%20blue%20gradient%20background%20clean%20minimal%20design%20large%20text%20space&image_size=landscape_16_9',
  '', 10, 'active'),
 ('新客首单 立减 30 元',
  'https://trae-api-cn.mchost.guru/api/ide/v1/tool_text_to_image?prompt=Fresh%20beverage%20wholesale%20promotion%20banner%20clean%20ecommerce%20design%20white%20and%20green%20palette&image_size=landscape_16_9',
  '', 20, 'active');

-- 9. 商城配置
INSERT INTO mall_config(key, value, remark) VALUES
 ('default_warehouse_id', (SELECT id::text FROM warehouse WHERE code = 'WH-DEMO'), '商城默认发货仓（为空时取第一个启用仓库）'),
 ('notice', '整箱批发 · 48 小时发货 · 满 299 元包邮', '首页公告'),
 ('service_phone', '400-800-1234', '客服电话'),
 ('credit_enabled', 'true', '是否开放账期支付'),
 ('wechat_enabled', 'true', '是否开放微信支付（需租户商户号授权）'),
 ('auto_confirm_days', '7', '自动确认收货天数'),
 ('bind_auto_approve', 'true', '邀请码加入是否自动通过'),
 ('min_order_amount', '0', '起送金额（0=不限）')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, remark = EXCLUDED.remark, updated_at = now();
