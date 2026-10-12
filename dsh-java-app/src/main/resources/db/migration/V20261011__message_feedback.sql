-- V20261011__message_feedback.sql
-- 消息反馈表：持久化用户对消息的评分与备注

CREATE TABLE IF NOT EXISTS message_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    rating INT,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE INDEX uk_message_feedback (message_id)
);
