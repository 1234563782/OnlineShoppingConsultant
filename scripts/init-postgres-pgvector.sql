-- PostgreSQL + pgvector：商品向量索引（方案 A：结构化过滤 + 向量排序）
-- 库名默认：postgres（可在 JDBC URL 中指定）
-- 密码等敏感信息请在 IDE / 环境变量中配置，勿提交明文。
--
-- 维度说明：
--   DashScope「text-embedding-v2」常见输出为 1536 维（以控制台/文档一次调用返回长度为准）。
--   若你实际使用的接口返回维度不是 1536，请全局替换本文件中的 1536，并重建表或 ALTER COLUMN 类型。

-- 1) 扩展（需超级用户或有创建扩展权限；云托管可能已预装）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) 商品向量表：与 MySQL `product.sku_id` 对齐；过滤列冗余自 MySQL，便于 WHERE + ORDER BY
CREATE TABLE IF NOT EXISTS embedding_product (
    sku_id           VARCHAR(32)  PRIMARY KEY,
    category_id      VARCHAR(64)  NOT NULL,
    price            INTEGER      NOT NULL,
    -- 1536 须与 embedding 模型输出维度一致
    embedding        vector(1536),
    content_hash     VARCHAR(64)  NOT NULL,
    embedding_model  VARCHAR(64)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 未回填向量前可为 NULL；查询时无向量行可排后或剔除
    CONSTRAINT chk_embedding_product_price_non_negative CHECK (price >= 0)
);

COMMENT ON TABLE embedding_product IS 'MySQL product 的语义索引副本；检索以 category_id+price 过滤，向量精排';

CREATE INDEX IF NOT EXISTS idx_embedding_product_category_id
    ON embedding_product (category_id);

CREATE INDEX IF NOT EXISTS idx_embedding_product_category_price
    ON embedding_product (category_id, price);

-- 3) 向量近邻索引（数据量较小时可先不建，全表顺序扫亦可；上万级建议建 HNSW）
--    算子类与 ORDER BY 一致：余弦距离使用 vector_cosine_ops，对应 <=> 排序
CREATE INDEX IF NOT EXISTS idx_embedding_product_embedding_hnsw
    ON embedding_product
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 若表内尚无有效向量（全部为 NULL），部分版本下可先不建 HNSW，待首批数据写入后再建索引。
