-- V20261012__agent_team.sql
-- 多代理团队协作（实验性，默认关闭）：团队花名册、共享任务板、团队消息

CREATE TABLE IF NOT EXISTS agent_team (
    team_id VARCHAR(64) PRIMARY KEY,
    lead VARCHAR(128) NOT NULL,
    teammates_json TEXT,
    message_strategy VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS team_task (
    task_id VARCHAR(64) PRIMARY KEY,
    team_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    assignee VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    INDEX idx_team_task_team (team_id)
);

CREATE TABLE IF NOT EXISTS team_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id VARCHAR(64) NOT NULL,
    from_agent VARCHAR(128) NOT NULL,
    to_agent VARCHAR(128) NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_team_message_team (team_id)
);