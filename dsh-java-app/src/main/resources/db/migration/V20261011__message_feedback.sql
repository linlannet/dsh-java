-- V20261011__message_feedback.sql
-- 消息反馈表：持久化用户对消息的评分与备注

CREATE TABLE IF NOT EXISTS message_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID（唯一）',
    rating INT NULL COMMENT '用户评分',
    note TEXT NULL COMMENT '用户备注',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL COMMENT '更新时间',
    UNIQUE INDEX uk_message_feedback (message_id)
) COMMENT='消息反馈表：持久化用户对消息的评分与备注';