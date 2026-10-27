-- V20261012__agent_team.sql
-- 多代理团队协作（实验性，默认关闭）：团队花名册、共享任务板、团队消息

CREATE TABLE IF NOT EXISTS agent_team (
    team_id VARCHAR(64) PRIMARY KEY COMMENT '团队ID（主键）',
    leader VARCHAR(128) NOT NULL COMMENT 'Leader 代理名称',
    teammates_json TEXT NULL COMMENT '团队成员花名册 JSON',
    message_strategy VARCHAR(32) NOT NULL COMMENT '团队消息投递策略',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='多代理团队表：团队花名册与消息投递策略';

CREATE TABLE IF NOT EXISTS team_task (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID（主键）',
    team_id VARCHAR(64) NOT NULL COMMENT '所属团队ID',
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    status VARCHAR(32) NOT NULL COMMENT '任务状态',
    assignee VARCHAR(128) NULL COMMENT '任务指派代理名称',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_team_task_team (team_id)
) COMMENT='团队共享任务板表：持久化团队任务及状态';

CREATE TABLE IF NOT EXISTS team_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    team_id VARCHAR(64) NOT NULL COMMENT '所属团队ID',
    from_agent VARCHAR(128) NOT NULL COMMENT '发送方代理名称',
    to_agent VARCHAR(128) NOT NULL COMMENT '接收方代理名称',
    content TEXT NULL COMMENT '消息内容',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_team_message_team (team_id)
) COMMENT='团队消息表：持久化团队内代理间消息';