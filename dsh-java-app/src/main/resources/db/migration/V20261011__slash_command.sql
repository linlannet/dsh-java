-- V20261011__slash_command.sql
-- Slash 命令表：持久化已注册的 Slash 命令及其启停状态

CREATE TABLE IF NOT EXISTS slash_command (
    name VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '命令名称（主键，不含前导斜杠）',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '命令描述',
    disabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否禁用（TRUE=禁用）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='Slash 命令表：持久化已注册的 Slash 命令及其启停状态';