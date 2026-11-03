-- V20261102__feishu_bot.sql
-- 飞书 Bot 表：持久化飞书长连接 Bot 配置与启停状态；
-- app_secret 不落库，仅保存 secret_ref 凭据引用键。

CREATE TABLE IF NOT EXISTS feishu_bot (
    bot_id VARCHAR(64) NOT NULL COMMENT 'Bot ID（主键）',
    bot_name VARCHAR(128) DEFAULT NULL COMMENT 'Bot 展示名',
    app_id VARCHAR(128) NOT NULL COMMENT '飞书应用 app_id',
    secret_ref VARCHAR(128) DEFAULT NULL COMMENT '凭据引用键（app_secret 不落库）',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用（0=禁用，1=启用）',
    agent_base_id VARCHAR(128) DEFAULT NULL COMMENT 'Agent 基 ID（用于派生会话 agentId）',
    channel_code VARCHAR(64) DEFAULT NULL COMMENT '模型渠道编码（IM 渠道不写入）',
    cwd VARCHAR(1024) DEFAULT NULL COMMENT 'Agent 工作目录',
    group_require_mention TINYINT NOT NULL DEFAULT 1 COMMENT '群聊是否需 @（0=否，1=是）',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (bot_id)
) COMMENT='飞书 Bot 表：持久化飞书长连接 Bot 配置与启停状态';