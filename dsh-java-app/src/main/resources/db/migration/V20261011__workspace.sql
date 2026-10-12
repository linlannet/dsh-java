-- V20261011__workspace.sql
-- 工作空间表：持久化工作空间与关联会话的多对多关系

CREATE TABLE IF NOT EXISTS workspace (
    workspace_id VARCHAR(64) PRIMARY KEY,
    root_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS session_workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    INDEX idx_session_workspace (workspace_id, session_id)
);
