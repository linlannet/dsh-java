-- V20261011__anonymous_user_id.sql
-- 匿名用户身份表：为未认证用户生成稳定匿名标识

CREATE TABLE IF NOT EXISTS anonymous_user_id (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fingerprint VARCHAR(255) NOT NULL UNIQUE,
    anonymous_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_anonymous_fingerprint (fingerprint)
);
