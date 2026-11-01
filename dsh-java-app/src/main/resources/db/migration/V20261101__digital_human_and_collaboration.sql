-- V20261101__digital_human_and_collaboration.sql
-- 数字人档案 + 协作房间表：此前只有 DAO 引用、无建表语句，
-- 导致 MySQL/H2 实例上访问数字人与协作功能时抛 "table doesn't exist"。

CREATE TABLE IF NOT EXISTS digital_human (
    id VARCHAR(128) NOT NULL COMMENT '主键ID',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    avatar_ref VARCHAR(512) DEFAULT NULL COMMENT '头像引用（emoji 或 URL）',
    purpose VARCHAR(1024) DEFAULT NULL COMMENT '用途描述',
    role_tags VARCHAR(512) DEFAULT NULL COMMENT '能力标签（逗号分隔）',
    theme_color VARCHAR(32) DEFAULT NULL COMMENT '主题色',
    approval_policy VARCHAR(32) DEFAULT NULL COMMENT '审批策略',
    concurrency_limit INT DEFAULT 1 COMMENT '并发任务上限',
    project_path VARCHAR(1024) DEFAULT NULL COMMENT '绑定项目路径',
    created_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
) COMMENT='数字人档案表：数字人基础信息与并发/审批策略';

CREATE TABLE IF NOT EXISTS collaboration_room (
    id VARCHAR(128) NOT NULL COMMENT '房间ID',
    workspace_id VARCHAR(128) NOT NULL COMMENT '工作区ID',
    title VARCHAR(256) DEFAULT NULL COMMENT '房间标题',
    objective TEXT COMMENT '协作目标',
    orchestration_mode VARCHAR(64) DEFAULT NULL COMMENT '编排模式',
    run_state VARCHAR(32) DEFAULT NULL COMMENT '运行状态',
    created_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_cr_workspace (workspace_id)
) COMMENT='协作房间表';

CREATE TABLE IF NOT EXISTS room_participant (
    id VARCHAR(128) NOT NULL COMMENT '主键ID',
    room_id VARCHAR(128) NOT NULL COMMENT '房间ID',
    digital_human_id VARCHAR(128) NOT NULL COMMENT '数字人ID',
    room_role VARCHAR(64) DEFAULT NULL COMMENT '房间角色',
    presence VARCHAR(32) DEFAULT NULL COMMENT '在场状态',
    active_task_label VARCHAR(512) DEFAULT NULL COMMENT '当前任务摘要',
    joined_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '加入时间',
    PRIMARY KEY (id),
    INDEX idx_rp_room (room_id),
    INDEX idx_rp_human (digital_human_id)
) COMMENT='协作房间参与者表';

CREATE TABLE IF NOT EXISTS collaboration_task (
    id VARCHAR(128) NOT NULL COMMENT '任务ID',
    room_id VARCHAR(128) NOT NULL COMMENT '房间ID',
    parent_task_id VARCHAR(128) DEFAULT NULL COMMENT '父任务ID',
    title VARCHAR(512) DEFAULT NULL COMMENT '任务标题',
    instruction TEXT COMMENT '任务指令',
    assigned_to VARCHAR(128) DEFAULT NULL COMMENT '指派给的数字人ID',
    requested_by VARCHAR(128) DEFAULT NULL COMMENT '发起方ID',
    state VARCHAR(32) DEFAULT NULL COMMENT '任务状态',
    depends_on TEXT COMMENT '依赖任务（JSON）',
    input_artifacts TEXT COMMENT '输入产物（JSON）',
    output_artifacts TEXT COMMENT '输出产物（JSON）',
    remote_session_id VARCHAR(128) DEFAULT NULL COMMENT '远端会话ID',
    trace_id VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    error_summary TEXT COMMENT '错误摘要',
    created_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_ct_room (room_id),
    INDEX idx_ct_state (state)
) COMMENT='协作任务表';

CREATE TABLE IF NOT EXISTS collaboration_artifact (
    id VARCHAR(128) NOT NULL COMMENT '产物ID',
    room_id VARCHAR(128) NOT NULL COMMENT '房间ID',
    task_id VARCHAR(128) DEFAULT NULL COMMENT '来源任务ID',
    producer_id VARCHAR(128) DEFAULT NULL COMMENT '生产者ID',
    kind VARCHAR(64) DEFAULT NULL COMMENT '产物类型',
    title VARCHAR(512) DEFAULT NULL COMMENT '产物标题',
    version INT DEFAULT NULL COMMENT '版本号',
    content TEXT COMMENT '产物内容',
    trace_id VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    created_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_ca_room (room_id),
    INDEX idx_ca_task (task_id)
) COMMENT='协作产物表';

CREATE TABLE IF NOT EXISTS room_event (
    id VARCHAR(128) NOT NULL COMMENT '事件ID',
    room_id VARCHAR(128) NOT NULL COMMENT '房间ID',
    seq BIGINT NOT NULL COMMENT '房间内事件序号',
    task_id VARCHAR(128) DEFAULT NULL COMMENT '关联任务ID',
    participant_id VARCHAR(128) DEFAULT NULL COMMENT '关联参与者ID',
    digital_human_id VARCHAR(128) DEFAULT NULL COMMENT '关联数字人ID',
    event_type VARCHAR(128) NOT NULL COMMENT '事件类型',
    occurred_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT '发生时间',
    visibility VARCHAR(32) DEFAULT NULL COMMENT '可见范围',
    payload TEXT COMMENT '事件负载（JSON）',
    trace_id VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
    PRIMARY KEY (id),
    INDEX idx_re_room_seq (room_id, seq)
) COMMENT='协作房间事件表';
