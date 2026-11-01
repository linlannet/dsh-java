CREATE TABLE IF NOT EXISTS harness_session (
    session_id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(128) NOT NULL,
    profile_code VARCHAR(64) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    resolved_model VARCHAR(128) NOT NULL,
    channel_code VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS harness_task (
    task_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    prompt TEXT NOT NULL,
    enabled_tools_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS harness_session_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    KEY idx_harness_session_event_session (session_id)
);

CREATE TABLE IF NOT EXISTS harness_plugin_installation (
    plugin_id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    plugin_version VARCHAR(64) NOT NULL,
    runtime_type VARCHAR(64) NOT NULL,
    install_mode VARCHAR(64) NOT NULL,
    source_path VARCHAR(512) NOT NULL,
    entrypoint VARCHAR(512) NOT NULL,
    module_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    installed_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS harness_plugin_runtime_binding (
    plugin_id VARCHAR(128) PRIMARY KEY,
    package_type VARCHAR(64) NOT NULL,
    manifest_path VARCHAR(512) NULL,
    working_directory VARCHAR(512) NULL,
    runtime_command VARCHAR(1024) NULL,
    bridge_ready TINYINT(1) NOT NULL,
    message TEXT NOT NULL,
    checked_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS harness_plugin_config (
    plugin_id VARCHAR(128) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NULL,
    config_layer VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (plugin_id, config_key, config_layer)
);

CREATE TABLE IF NOT EXISTS harness_model_setting (
    channel_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    base_url VARCHAR(255) NULL,
    api_key_ref VARCHAR(128) NULL,
    protocol VARCHAR(32) NOT NULL DEFAULT 'openai',
    enabled BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (channel_code)
);

-- 开发阶段模型渠道表以 channel_code 为主键；channel_code 与 harness_session.channel_code
-- 均由 SchemaMigrationRunner 在启动时幂等补齐。

CREATE TABLE IF NOT EXISTS harness_goal_state (
    session_id VARCHAR(64) PRIMARY KEY,
    goal_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL,
    blocked_reason TEXT NULL,
    max_rounds INT NOT NULL,
    rounds_started INT NOT NULL,
    continuation_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);

-- ── New session event system tables ──────────────────────────
CREATE TABLE IF NOT EXISTS harness_session_header (
    session_id VARCHAR(64) PRIMARY KEY,
    version INT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    cwd VARCHAR(512) NULL,
    parent_session VARCHAR(64) NULL,
    seed_length INT NOT NULL DEFAULT 0,
    origin VARCHAR(32) NULL,
    delegation_depth INT NOT NULL DEFAULT 0,
    agent_preset VARCHAR(128) NULL
);

CREATE TABLE IF NOT EXISTS harness_session_event_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_time TIMESTAMP(3) NOT NULL,
    payload_json LONGTEXT NULL,
    surface_op VARCHAR(16) NULL,
    source_event_seqs VARCHAR(512) NULL,
    UNIQUE (session_id, seq),
    KEY idx_harness_session_event_log_session (session_id)
);

-- 模型配置的权威来源是本表；默认模型种子由 ModelSyncRunner 按 application.yml 初始化，
-- 不在 schema 中重复写入可能过期的 base_url、api_key 或模型编码。

-- ── Token snapshot ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS token_snapshot (
    session_id VARCHAR(64) PRIMARY KEY,
    last_event_step_index BIGINT NOT NULL,
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    cache_read_tokens INT NULL,
    cache_write_tokens INT NULL,
    reasoning_tokens INT NULL,
    model_calls BIGINT NOT NULL
);

-- ── Tool catalog & per-profile bindings ──────────────────────
CREATE TABLE IF NOT EXISTS harness_tool_catalog (
    tool_code VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    category VARCHAR(64) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS harness_tool_profile_binding (
    profile_code VARCHAR(64) NOT NULL,
    tool_code VARCHAR(128) NOT NULL,
    PRIMARY KEY (profile_code, tool_code)
);

-- ── Tool catalog seed data ───────────────────────────────────
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs.read', 'Read File', 'Read file contents from the workspace.', 'fs', FALSE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs.read');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs.write', 'Write File', 'Write or overwrite file contents in the workspace.', 'fs', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs.write');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'shell.exec', 'Execute Shell', 'Run a shell command in the sandbox.', 'shell', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'shell.exec');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'web.search', 'Web Search', 'Search the web for information.', 'web', FALSE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'web.search');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'mcp.call', 'MCP Tool', 'Invoke an MCP server tool.', 'mcp', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'mcp.call');

-- 数字人档案表（此前缺建表语句，数字人/协作功能在空库上会报 table doesn't exist）
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

-- 协作房间表
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

-- 协作房间参与者表
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

-- 协作任务表
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

-- 协作产物表
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

-- 协作房间事件表
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

-- Default: enable read-only tools for headless and web profiles
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'web.search');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'web.search');

-- ── Digital human endpoint ───────────────────────────────────
-- 数据来源：db/migration/V20260922__digital_human_endpoint.sql
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

-- ── Workspace & session binding ───────────────────────────────
-- 数据来源：db/migration/V20261011__workspace.sql
CREATE TABLE IF NOT EXISTS workspace (
    workspace_id VARCHAR(64) PRIMARY KEY COMMENT '工作空间ID（主键）',
    root_path VARCHAR(1024) NOT NULL COMMENT '工作空间根目录路径',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间'
) COMMENT='工作空间表：持久化工作空间定义';

CREATE TABLE IF NOT EXISTS session_workspace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作空间ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    INDEX idx_session_workspace (workspace_id, session_id)
) COMMENT='会话-工作空间关联表：持久化工作空间与会话的多对多关系';

-- ── Checkpoint ───────────────────────────────────────────────
-- 数据来源：db/migration/V20261011__checkpoint.sql
CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    step_index BIGINT NOT NULL COMMENT '会话步次索引',
    reason VARCHAR(64) NOT NULL COMMENT '检查点原因（before-model-request / before-tool-side-effect）',
    context_snapshot_json TEXT NULL COMMENT '上下文快照 JSON',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    INDEX idx_checkpoint_session (session_id, step_index)
) COMMENT='检查点表：持久化 Session 执行过程中的检查点快照（LLM 请求前 / 工具副作用前）';

-- ── Spill reference ──────────────────────────────────────────
-- 数据来源：db/migration/V20261011__spill_ref.sql
CREATE TABLE IF NOT EXISTS spill_ref (
    ref_id VARCHAR(64) PRIMARY KEY COMMENT '溢出引用ID（主键）',
    content LONGTEXT NULL COMMENT '溢出的完整内容',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间'
) COMMENT='溢出引用表：持久化超出上下文阈值的内容，以 refId 引用替代内联';

-- ── Anonymous user identity ──────────────────────────────────
-- 数据来源：db/migration/V20261011__anonymous_user_id.sql
CREATE TABLE IF NOT EXISTS anonymous_user_id (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    fingerprint VARCHAR(255) NOT NULL COMMENT '客户端指纹（唯一，用于稳定关联匿名标识）',
    anonymous_id VARCHAR(128) NOT NULL COMMENT '匿名标识（匿名 UUID，不含机器或账户数据）',
    -- MySQL 8.0.43 + explicit_defaults_for_timestamp=1 下，TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
    -- 会被判为 "Invalid default value"；统一用 DATETIME(3) + CURRENT_TIMESTAMP(3)。
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE (fingerprint),
    INDEX idx_anonymous_fingerprint (fingerprint)
) COMMENT='匿名用户身份表：为未认证用户生成稳定匿名标识';

-- ── Message feedback ─────────────────────────────────────────
-- 数据来源：db/migration/V20261011__message_feedback.sql
CREATE TABLE IF NOT EXISTS message_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID（唯一）',
    rating INT NULL COMMENT '用户评分',
    note TEXT NULL COMMENT '用户备注',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    UNIQUE (message_id)
) COMMENT='消息反馈表：持久化用户对消息的评分与备注';

-- ── Slash commands ───────────────────────────────────────────
-- 数据来源：db/migration/V20261011__slash_command.sql
CREATE TABLE IF NOT EXISTS slash_command (
    name VARCHAR(128) PRIMARY KEY COMMENT '命令名称（主键，不含前导斜杠）',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '命令描述',
    disabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否禁用（TRUE=禁用）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间'
) COMMENT='Slash 命令表：持久化已注册的 Slash 命令及其启停状态';

-- ── Multi-agent team collaboration (experimental) ────────────
-- 数据来源：db/migration/V20261012__agent_team.sql
-- 列名 leader 与 JdbcAgentTeamRepository 一致；规避 MySQL/H2 保留字 LEAD。
CREATE TABLE IF NOT EXISTS agent_team (
    team_id VARCHAR(64) PRIMARY KEY COMMENT '团队ID（主键）',
    leader VARCHAR(128) NOT NULL COMMENT 'Leader 代理名称',
    teammates_json TEXT NULL COMMENT '团队成员花名册 JSON',
    message_strategy VARCHAR(32) NOT NULL COMMENT '团队消息投递策略',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间'
) COMMENT='多代理团队表：团队花名册与消息投递策略';

CREATE TABLE IF NOT EXISTS team_task (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID（主键）',
    team_id VARCHAR(64) NOT NULL COMMENT '所属团队ID',
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    status VARCHAR(32) NOT NULL COMMENT '任务状态',
    assignee VARCHAR(128) NULL COMMENT '任务指派代理名称',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    INDEX idx_team_task_team (team_id)
) COMMENT='团队共享任务板表：持久化团队任务及状态';

CREATE TABLE IF NOT EXISTS team_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    team_id VARCHAR(64) NOT NULL COMMENT '所属团队ID',
    from_agent VARCHAR(128) NOT NULL COMMENT '发送方代理名称',
    to_agent VARCHAR(128) NOT NULL COMMENT '接收方代理名称',
    content TEXT NULL COMMENT '消息内容',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    INDEX idx_team_message_team (team_id)
) COMMENT='团队消息表：持久化团队内代理间消息';
