-- 附件：报销发票、合同扫描件、请假证明等（文件落本地存储，元数据入库）

CREATE TABLE attachment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    biz_type      VARCHAR(32) NOT NULL,        -- leave/expense/contract/overtime ...
    biz_id        UUID,                        -- 关联业务单据 id（草稿期可为 NULL，先传后绑）
    file_name     VARCHAR(255) NOT NULL,       -- 原始文件名
    file_ext      VARCHAR(16),                 -- 扩展名
    file_size     BIGINT DEFAULT 0,
    content_type  VARCHAR(100),
    storage_path  VARCHAR(500) NOT NULL,       -- 相对存储目录的路径
    uploader_id   UUID REFERENCES users(id),
    uploader_name VARCHAR(64),                 -- 冗余快照
    remark        VARCHAR(255),
    created_at    TIMESTAMPTZ DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_att_biz ON attachment(biz_type, biz_id);
CREATE INDEX idx_att_uploader ON attachment(uploader_id);
