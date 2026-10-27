-- V20261011__workspace.sql
-- 工作空间表：持久化工作空间与关联会话的多对多关系

CREATE TABLE IF NOT EXISTS workspace (
    workspace_id VARCHAR(64) PRIMARY KEY COMMENT '工作空间ID（主键）',
    root_path VARCHAR(1024) NOT NULL COMMENT '工作空间根目录路径',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='工作空间表：持久化工作空间定义';

CREATE TABLE IF NOT EXISTS session_workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作空间ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    INDEX idx_session_workspace (workspace_id, session_id)
) COMMENT='会话-工作空间关联表：持久化工作空间与会话的多对多关系';