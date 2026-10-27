-- V20260922__digital_human_endpoint.sql
-- 数字人端点表：持久化数字人的远端端点配置与健康检查状态

CREATE TABLE IF NOT EXISTS digital_human_endpoint (
    id VARCHAR(128) NOT NULL COMMENT '主键ID',
    digital_human_id VARCHAR(128) NOT NULL COMMENT '数字人ID',
    endpoint_type VARCHAR(64) NOT NULL COMMENT '端点类型',
    base_url VARCHAR(512) DEFAULT NULL COMMENT '服务端点基地址',
    credential_ref VARCHAR(128) DEFAULT NULL COMMENT '凭据引用（仅存引用不存明文）',
    protocol_version VARCHAR(32) DEFAULT NULL COMMENT '协议版本',
    agent_card TEXT COMMENT 'Agent Card 内容（JSON）',
    health_state VARCHAR(32) NOT NULL COMMENT '健康状态',
    last_checked_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '最近健康检查时间',
    latency_ms INT DEFAULT NULL COMMENT '最近健康检查延迟（毫秒）',
    PRIMARY KEY (id),
    INDEX idx_dhe_digital_human_id (digital_human_id),
    INDEX idx_dhe_health_state (health_state)
) COMMENT='数字人端点表：持久化数字人远端端点配置与健康检查状态';