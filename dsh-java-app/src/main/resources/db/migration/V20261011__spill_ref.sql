-- V20261011__spill_ref.sql
-- 溢出引用表：持久化超出上下文阈值的内容，以 refId 引用替代内联

CREATE TABLE IF NOT EXISTS spill_ref (
    ref_id VARCHAR(64) PRIMARY KEY COMMENT '溢出引用ID（主键）',
    content LONGTEXT NULL COMMENT '溢出的完整内容',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='溢出引用表：持久化超出上下文阈值的内容，以 refId 引用替代内联';