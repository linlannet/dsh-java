-- V20261011__anonymous_user_id.sql
-- 匿名用户身份表：为未认证用户生成稳定匿名标识

CREATE TABLE IF NOT EXISTS anonymous_user_id (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    fingerprint VARCHAR(255) NOT NULL UNIQUE COMMENT '客户端指纹（唯一，用于稳定关联匿名标识）',
    anonymous_id VARCHAR(128) NOT NULL COMMENT '匿名标识（匿名 UUID，不含机器或账户数据）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_anonymous_fingerprint (fingerprint)
) COMMENT='匿名用户身份表：为未认证用户生成稳定匿名标识';