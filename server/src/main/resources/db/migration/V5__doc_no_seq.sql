-- 单据编号序列表（NoGenerator 依赖；按 前缀+日期 每日递增）
CREATE TABLE doc_no_seq (
    prefix VARCHAR(16) NOT NULL,
    day    VARCHAR(8)  NOT NULL,
    seq    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (prefix, day)
);
