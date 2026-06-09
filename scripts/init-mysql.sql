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
('SKU1001', '手机', 'cat_phone',     '手机', '小米 14',              'Xiaomi',   3999, '旗舰直屏，徕卡影像'),
('SKU1002', '手机', 'cat_phone',     '手机', 'iPhone 15',            'Apple',    5999, 'A16 芯片，生态完善'),
('SKU1003', '手机', 'cat_phone',     '手机', '荣耀 200',             'Honor',    2699, '轻薄长续航'),
('SKU2001', '耳机', 'cat_headphone', '耳机', '索尼 WH-1000XM5',      'Sony',     2299, '头戴降噪旗舰'),
('SKU2002', '耳机', 'cat_headphone', '耳机', 'AirPods Pro 2',        'Apple',    1899, '主动降噪，通透模式'),
('SKU2003', '耳机', 'cat_headphone', '耳机', '漫步者 NeoBuds Pro',   'Edifier',   799, '入门降噪'),
('SKU3001', '电脑', 'cat_computer',  '电脑', '联想小新 Pro 14',      'Lenovo',   5699, '轻薄高性能'),
('SKU3002', '电脑', 'cat_computer',  '电脑', 'MacBook Air M3',       'Apple',    8999, '续航优秀'),
('SKU3003', '电脑', 'cat_computer',  '电脑', '机械革命 无界 14X',    'MECHREVO', 4299, '高性价比'),
('SKU4001', '平板', 'cat_tablet',    '平板', 'iPad Air',             'Apple',    4799, '学习办公通用'),
('SKU4002', '平板', 'cat_tablet',    '平板', '小米平板 6S Pro',      'Xiaomi',   3299, '大屏娱乐'),
('SKU5001', '手表', 'cat_watch',     '手表', '华为 WATCH GT 4',      'Huawei',   1488, '健康监测'),
('SKU5002', '手表', 'cat_watch',     '手表', 'Apple Watch S9',       'Apple',    2999, 'iOS 生态搭配')
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    category_id = VALUES(category_id),
    category_name = VALUES(category_name),
    name = VALUES(name),
    brand = VALUES(brand),
    price = VALUES(price),
    description = VALUES(description);

-- ============================================================
-- 3) 用户账号：user_account（orchestrator 登录注册）
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
-- 4) 长期画像：user_memory（memory-service 用，与 catalog 同库）
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
SELECT 'user_memory', COUNT(*) FROM user_memory;

SELECT category_id, name, LEFT(aliases, 80) AS aliases_preview
FROM product_category
WHERE enabled = 1;
