-- Online Shopping Consultant - MySQL init
-- Database: shopping_consultant (same as SHOPPING_MEMORY_DB_URL)
-- Run: mysql -u root -p < scripts/init-mysql.sql

CREATE DATABASE IF NOT EXISTS shopping_consultant
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE shopping_consultant;

-- ============================================================
-- 1) 类目归一化：product_category（Orchestrator normalize 查这张表）
-- ============================================================
CREATE TABLE IF NOT EXISTS product_category (
    category_id VARCHAR(64)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    parent_id   VARCHAR(64)  NULL,
    aliases     VARCHAR(512) NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO product_category (category_id, name, parent_id, aliases, enabled) VALUES
('cat_phone',     '手机', NULL, '智能手机,安卓手机,iPhone,苹果手机', 1),
('cat_headphone', '耳机', NULL, '蓝牙耳机,降噪耳机,头戴耳机,无线耳机,入耳式,TWS,真无线', 1),
('cat_computer',  '电脑', NULL, '笔记本,笔记本电脑,轻薄本,游戏本,台式机', 1),
('cat_tablet',    '平板', NULL, '平板电脑,iPad,安卓平板', 1),
('cat_watch',     '手表', NULL, '智能手表,运动手表,Apple Watch,华为手表', 1),
('cat_tv',        '电视', NULL, '电视机,智能电视,大屏电视,客厅电视', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    aliases = VALUES(aliases),
    enabled = VALUES(enabled);

-- ============================================================
-- 2) 商品检索：product（searchProduct 查这张表）
-- ============================================================
CREATE TABLE IF NOT EXISTS product (
    sku_id        VARCHAR(32)  NOT NULL PRIMARY KEY,
    category      VARCHAR(64)  NOT NULL,
    category_id   VARCHAR(64)  NULL,
    category_name VARCHAR(64)  NULL,
    name          VARCHAR(128) NOT NULL,
    brand         VARCHAR(64)  NOT NULL,
    price         INT          NOT NULL,
    description   VARCHAR(512) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO product (sku_id, category, category_id, category_name, name, brand, price, description) VALUES
-- 手机 cat_phone（8）
('SKU1001', '手机', 'cat_phone',     '手机', '小米 14',                 'Xiaomi',    3999, '旗舰直屏，徕卡影像，骁龙8 Gen3'),
('SKU1002', '手机', 'cat_phone',     '手机', 'iPhone 15',               'Apple',     5999, 'A16 芯片，iOS 生态，USB-C'),
('SKU1003', '手机', 'cat_phone',     '手机', '荣耀 200',                'Honor',     2699, '轻薄长续航，人像摄影'),
('SKU1004', '手机', 'cat_phone',     '手机', '华为 Pura 70',            'Huawei',    6499, '鸿蒙系统，超聚光影像'),
('SKU1005', '手机', 'cat_phone',     '手机', 'OPPO Find X7',            'OPPO',      4299, '天玑9300，哈苏影像'),
('SKU1006', '手机', 'cat_phone',     '手机', 'vivo X100',               'vivo',      3999, '蔡司镜头，蓝海电池'),
('SKU1007', '手机', 'cat_phone',     '手机', 'Redmi K70',               'Redmi',     2499, '2K 直屏，性价比旗舰'),
('SKU1008', '手机', 'cat_phone',     '手机', 'Samsung Galaxy S24',      'Samsung',   5499, 'Galaxy AI，小屏旗舰'),
-- 耳机 cat_headphone（8）
('SKU2001', '耳机', 'cat_headphone', '耳机', '索尼 WH-1000XM5',         'Sony',      2299, '头戴降噪旗舰，30小时续航'),
('SKU2002', '耳机', 'cat_headphone', '耳机', 'AirPods Pro 2',           'Apple',     1899, '主动降噪，通透模式，H2芯片'),
('SKU2003', '耳机', 'cat_headphone', '耳机', '漫步者 NeoBuds Pro',      'Edifier',    799, '入门降噪，LHDC高清'),
('SKU2004', '耳机', 'cat_headphone', '耳机', 'Bose QC Ultra',           'Bose',      3299, '顶级消噪，沉浸式音频'),
('SKU2005', '耳机', 'cat_headphone', '耳机', '华为 FreeBuds Pro 3',     'Huawei',    1199, '星闪连接，智慧动态降噪'),
('SKU2006', '耳机', 'cat_headphone', '耳机', '小米 Buds 5 Pro',         'Xiaomi',     699, '入耳降噪，空间音频'),
('SKU2007', '耳机', 'cat_headphone', '耳机', '韶音 OpenRun Pro 2',      'Shokz',     1298, '骨传导，运动跑步专用'),
('SKU2008', '耳机', 'cat_headphone', '耳机', 'Nothing Ear (2)',         'Nothing',    599, '透明设计，轻量舒适'),
-- 电脑 cat_computer（8）
('SKU3001', '电脑', 'cat_computer',  '电脑', '联想小新 Pro 14',         'Lenovo',    5699, '轻薄高性能，2.8K OLED屏'),
('SKU3002', '电脑', 'cat_computer',  '电脑', 'MacBook Air M3',          'Apple',     8999, 'M3芯片，18小时续航，无风扇'),
('SKU3003', '电脑', 'cat_computer',  '电脑', '机械革命 无界 14X',       'MECHREVO',  4299, 'R7处理器，高性价比办公'),
('SKU3004', '电脑', 'cat_computer',  '电脑', '戴尔灵越 14 Plus',        'Dell',      6199, '商务轻薄，雷电接口'),
('SKU3005', '电脑', 'cat_computer',  '电脑', '华硕天选 4',              'ASUS',      7499, 'RTX4060游戏本，144Hz高刷'),
('SKU3006', '电脑', 'cat_computer',  '电脑', 'ThinkPad X1 Carbon',      'Lenovo',    9999, '旗舰商务本，军工级耐用'),
('SKU3007', '电脑', 'cat_computer',  '电脑', '惠普战 66 七代',          'HP',        5299, '军标测试，可扩展内存'),
('SKU3008', '电脑', 'cat_computer',  '电脑', '微软 Surface Laptop 6',   'Microsoft', 9788, '触控屏，Windows AI PC'),
-- 平板 cat_tablet（6）
('SKU4001', '平板', 'cat_tablet',    '平板', 'iPad Air',                'Apple',     4799, 'M1芯片，学习办公通用'),
('SKU4002', '平板', 'cat_tablet',    '平板', '小米平板 6S Pro',         'Xiaomi',    3299, '骁龙8 Gen2，大屏娱乐'),
('SKU4003', '平板', 'cat_tablet',    '平板', '华为 MatePad Pro',        'Huawei',    4999, '鸿蒙平板，PC级生产力'),
('SKU4004', '平板', 'cat_tablet',    '平板', '三星 Galaxy Tab S9',      'Samsung',   4599, 'S Pen手写，AMOLED屏'),
('SKU4005', '平板', 'cat_tablet',    '平板', '联想小新 Pad Pro',        'Lenovo',    2199, '2.5K屏，学习网课'),
('SKU4006', '平板', 'cat_tablet',    '平板', '荣耀平板 V8 Pro',         'Honor',     2499, '144Hz高刷，多屏协同'),
-- 手表 cat_watch（6）
('SKU5001', '手表', 'cat_watch',     '手表', '华为 WATCH GT 4',         'Huawei',    1488, '健康监测，两周续航'),
('SKU5002', '手表', 'cat_watch',     '手表', 'Apple Watch S9',          'Apple',     2999, 'iOS生态，血氧心电图'),
('SKU5003', '手表', 'cat_watch',     '手表', '小米手表 S3',             'Xiaomi',     999, 'eSIM版，HyperOS'),
('SKU5004', '手表', 'cat_watch',     '手表', 'Garmin Forerunner 265',   'Garmin',    3280, '专业跑步，训练负荷'),
('SKU5005', '手表', 'cat_watch',     '手表', '华为 WATCH FIT 3',        'Huawei',     899, '轻薄方表，100+运动模式'),
('SKU5006', '手表', 'cat_watch',     '手表', 'Samsung Galaxy Watch 6',  'Samsung',   1999, 'Wear OS，睡眠评分'),
-- 电视 cat_tv（6）
('SKU6001', '电视', 'cat_tv',        '电视', '小米电视 S Pro 65',       'Xiaomi',    4999, 'Mini LED，4K 144Hz，游戏模式'),
('SKU6002', '电视', 'cat_tv',        '电视', '索尼 XR-65A80L',          'Sony',     12999, 'OLED，XR认知芯片，影院级画质'),
('SKU6003', '电视', 'cat_tv',        '电视', 'TCL 75T7K',               'TCL',       6999, '75英寸，量子点Pro，杜比视界'),
('SKU6004', '电视', 'cat_tv',        '电视', '海信 E8N Pro 85',         'Hisense',  11999, '85英寸，U+Mini LED，高刷'),
('SKU6005', '电视', 'cat_tv',        '电视', 'Redmi 电视 MAX 86',       'Redmi',     7999, '86英寸大屏，120Hz MEMC'),
('SKU6006', '电视', 'cat_tv',        '电视', '华为 Vision 智慧屏 5',    'Huawei',    8999, '鸿蒙系统，240Hz鸿鹄芯片')
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    category_id = VALUES(category_id),
    category_name = VALUES(category_name),
    name = VALUES(name),
    brand = VALUES(brand),
    price = VALUES(price),
    description = VALUES(description);

-- ============================================================
-- 3) 库存：product_inventory（inventory MCP / checkInventory / 对比预取）
-- ============================================================
CREATE TABLE IF NOT EXISTS product_inventory (
    sku_id   VARCHAR(32) NOT NULL PRIMARY KEY,
    quantity INT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_product_inventory_quantity_non_negative CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO product_inventory (sku_id, quantity) VALUES
('SKU1001', 12), ('SKU1002',  5), ('SKU1003',  0), ('SKU1004',  8), ('SKU1005', 14),
('SKU1006', 11), ('SKU1007', 25), ('SKU1008',  6),
('SKU2001',  8), ('SKU2002', 15), ('SKU2003', 30), ('SKU2004',  4), ('SKU2005', 18),
('SKU2006', 22), ('SKU2007',  9), ('SKU2008', 35),
('SKU3001',  6), ('SKU3002',  3), ('SKU3003', 10), ('SKU3004',  7), ('SKU3005',  5),
('SKU3006',  2), ('SKU3007', 12), ('SKU3008',  4),
('SKU4001',  7), ('SKU4002',  9), ('SKU4003',  6), ('SKU4004',  5), ('SKU4005', 15),
('SKU4006', 13),
('SKU5001', 20), ('SKU5002',  4), ('SKU5003', 28), ('SKU5004',  6), ('SKU5005', 32),
('SKU5006',  8),
('SKU6001', 10), ('SKU6002',  2), ('SKU6003',  5), ('SKU6004',  3), ('SKU6005',  4),
('SKU6006',  6)
ON DUPLICATE KEY UPDATE
    quantity = VALUES(quantity);

-- ============================================================
-- 4) 优惠：product_promotion（promotion MCP / getPromotions / 对比预取）
--     同一 SKU 可有多条；discount=立减金额，discount_rate=折扣率（如 0.95=95 折）
-- ============================================================
CREATE TABLE IF NOT EXISTS product_promotion (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku_id        VARCHAR(32)  NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    label         VARCHAR(128) NOT NULL,
    discount      INT          NULL,
    discount_rate DOUBLE       NULL,
    KEY idx_product_promotion_sku_id (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELETE FROM product_promotion;

INSERT INTO product_promotion (sku_id, type, label, discount, discount_rate) VALUES
('SKU1001', 'coupon',   '满3000减200',           200,  NULL),
('SKU1001', 'member',   '会员再减50',             50,   NULL),
('SKU1003', 'instant',  '限时95折',               NULL, 0.95),
('SKU1004', 'coupon',   '满6000减400',           400,  NULL),
('SKU1005', 'bundle',   '以旧换新补贴300',       300,  NULL),
('SKU1007', 'new_user', '新客立减100',           100,  NULL),
('SKU1008', 'member',   '三星会员95折',           NULL, 0.95),
('SKU2001', 'coupon',   '满2000减150',           150,  NULL),
('SKU2002', 'bundle',   '搭配手机减100',         100,  NULL),
('SKU2003', 'new_user', '新客立减30',             30,   NULL),
('SKU2004', 'coupon',   '满3000减300',           300,  NULL),
('SKU2005', 'instant',  '限时减120',             120,  NULL),
('SKU2006', 'flash',    '秒杀减50',               50,   NULL),
('SKU3001', 'coupon',   '满5000减300',           300,  NULL),
('SKU3002', 'education','教育优惠95折',           NULL, 0.95),
('SKU3003', 'flash',    '秒杀立减200',           200,  NULL),
('SKU3004', 'coupon',   '满5500减250',           250,  NULL),
('SKU3005', 'bundle',   '游戏套装减500',         500,  NULL),
('SKU3007', 'coupon',   '企业采购减200',         200,  NULL),
('SKU4001', 'coupon',   '满4000减200',           200,  NULL),
('SKU4002', 'member',   '会员减100',             100,  NULL),
('SKU4003', 'bundle',   '手写笔套装减150',       150,  NULL),
('SKU4005', 'new_user', '学生认证减80',           80,   NULL),
('SKU5001', 'coupon',   '满1000减80',             80,   NULL),
('SKU5002', 'bundle',   'Apple生态套装减150',    150,  NULL),
('SKU5003', 'flash',    '限时减80',               80,   NULL),
('SKU5004', 'coupon',   '跑步季减200',           200,  NULL),
('SKU6001', 'coupon',   '满4500减300',           300,  NULL),
('SKU6002', 'bundle',   'Soundbar套装减800',     800,  NULL),
('SKU6003', 'instant',  '大屏节95折',             NULL, 0.95),
('SKU6004', 'coupon',   '满10000减800',          800,  NULL),
('SKU6005', 'flash',    '秒杀减400',             400,  NULL),
('SKU6006', 'member',   '华为会员减300',         300,  NULL);

-- ============================================================
-- 5) 用户账号：user_account（orchestrator 登录注册）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_account (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name  VARCHAR(64)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6) 长期画像：user_memory（memory-service 用，与 catalog 同库）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memory (
    user_id      VARCHAR(128) NOT NULL PRIMARY KEY,
    profile_json LONGTEXT     NULL,
    summary_md   LONGTEXT     NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 验证
-- ============================================================
SELECT 'product_category' AS tbl, COUNT(*) AS cnt FROM product_category
UNION ALL
SELECT 'product', COUNT(*) FROM product
UNION ALL
SELECT 'product_inventory', COUNT(*) FROM product_inventory
UNION ALL
SELECT 'product_promotion', COUNT(*) FROM product_promotion
UNION ALL
SELECT 'user_memory', COUNT(*) FROM user_memory;

SELECT category_id, name, LEFT(aliases, 80) AS aliases_preview
FROM product_category
WHERE enabled = 1;
