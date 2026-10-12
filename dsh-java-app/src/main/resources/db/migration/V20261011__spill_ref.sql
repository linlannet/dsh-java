-- V20261011__spill_ref.sql
-- 溢出引用表：持久化超出上下文阈值的内容，以 refId 引用替代内联

CREATE TABLE IF NOT EXISTS spill_ref (
    ref_id VARCHAR(64) PRIMARY KEY,
    content CLOB,
    created_at TIMESTAMP NOT NULL
);
