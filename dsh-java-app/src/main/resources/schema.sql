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
    id VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    avatar_ref VARCHAR(512) DEFAULT NULL,
    purpose VARCHAR(1024) DEFAULT NULL,
    role_tags VARCHAR(512) DEFAULT NULL,
    theme_color VARCHAR(32) DEFAULT NULL,
    approval_policy VARCHAR(32) DEFAULT NULL,
    concurrency_limit INT DEFAULT 1,
    project_path VARCHAR(1024) DEFAULT NULL,
    created_at TIMESTAMP(3) NULL DEFAULT NULL,
    updated_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (id)
);

-- 协作房间表
CREATE TABLE IF NOT EXISTS collaboration_room (
    id VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(128) NOT NULL,
    title VARCHAR(256) DEFAULT NULL,
    objective TEXT,
    orchestration_mode VARCHAR(64) DEFAULT NULL,
    run_state VARCHAR(32) DEFAULT NULL,
    created_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_cr_workspace (workspace_id)
);

-- 协作房间参与者表
CREATE TABLE IF NOT EXISTS room_participant (
    id VARCHAR(128) NOT NULL,
    room_id VARCHAR(128) NOT NULL,
    digital_human_id VARCHAR(128) NOT NULL,
    room_role VARCHAR(64) DEFAULT NULL,
    presence VARCHAR(32) DEFAULT NULL,
    active_task_label VARCHAR(512) DEFAULT NULL,
    joined_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_rp_room (room_id),
    INDEX idx_rp_human (digital_human_id)
);

-- 协作任务表
CREATE TABLE IF NOT EXISTS collaboration_task (
    id VARCHAR(128) NOT NULL,
    room_id VARCHAR(128) NOT NULL,
    parent_task_id VARCHAR(128) DEFAULT NULL,
    title VARCHAR(512) DEFAULT NULL,
    instruction TEXT,
    assigned_to VARCHAR(128) DEFAULT NULL,
    requested_by VARCHAR(128) DEFAULT NULL,
    state VARCHAR(32) DEFAULT NULL,
    depends_on TEXT,
    input_artifacts TEXT,
    output_artifacts TEXT,
    remote_session_id VARCHAR(128) DEFAULT NULL,
    trace_id VARCHAR(128) DEFAULT NULL,
    error_summary TEXT,
    created_at TIMESTAMP(3) NULL DEFAULT NULL,
    updated_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ct_room (room_id),
    INDEX idx_ct_state (state)
);

-- 协作产物表
CREATE TABLE IF NOT EXISTS collaboration_artifact (
    id VARCHAR(128) NOT NULL,
    room_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) DEFAULT NULL,
    producer_id VARCHAR(128) DEFAULT NULL,
    kind VARCHAR(64) DEFAULT NULL,
    title VARCHAR(512) DEFAULT NULL,
    version INT DEFAULT NULL,
    content TEXT,
    trace_id VARCHAR(128) DEFAULT NULL,
    created_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ca_room (room_id),
    INDEX idx_ca_task (task_id)
);

-- 协作房间事件表
CREATE TABLE IF NOT EXISTS room_event (
    id VARCHAR(128) NOT NULL,
    room_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL,
    task_id VARCHAR(128) DEFAULT NULL,
    participant_id VARCHAR(128) DEFAULT NULL,
    digital_human_id VARCHAR(128) DEFAULT NULL,
    event_type VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP(3) NULL DEFAULT NULL,
    visibility VARCHAR(32) DEFAULT NULL,
    payload TEXT,
    trace_id VARCHAR(128) DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_re_room_seq (room_id, seq)
);

-- Default: enable read-only tools for headless and web profiles
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'web.search');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'web.search');

-- ── Digital human endpoint ───────────────────────────────────
-- 数据来源：db/migration/V20260922__digital_human_endpoint.sql
CREATE TABLE IF NOT EXISTS digital_human_endpoint (
    id VARCHAR(128) NOT NULL,
    digital_human_id VARCHAR(128) NOT NULL,
    endpoint_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(512) DEFAULT NULL,
    credential_ref VARCHAR(128) DEFAULT NULL,
    protocol_version VARCHAR(32) DEFAULT NULL,
    agent_card TEXT,
    health_state VARCHAR(32) NOT NULL,
    last_checked_at TIMESTAMP(3) NULL DEFAULT NULL,
    latency_ms INT DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_dhe_digital_human_id (digital_human_id),
    INDEX idx_dhe_health_state (health_state)
);

-- ── Workspace & session binding ───────────────────────────────
-- 数据来源：db/migration/V20261011__workspace.sql
CREATE TABLE IF NOT EXISTS workspace (
    workspace_id VARCHAR(64) PRIMARY KEY,
    root_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS session_workspace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    INDEX idx_session_workspace (workspace_id, session_id)
);

-- ── Checkpoint ───────────────────────────────────────────────
-- 数据来源：db/migration/V20261011__checkpoint.sql
CREATE TABLE IF NOT EXISTS checkpoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    step_index BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    context_snapshot_json TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_checkpoint_session (session_id, step_index)
);

-- ── Spill reference ──────────────────────────────────────────
-- 数据来源：db/migration/V20261011__spill_ref.sql
CREATE TABLE IF NOT EXISTS spill_ref (
    ref_id VARCHAR(64) PRIMARY KEY,
    content LONGTEXT NULL,
    created_at TIMESTAMP(3) NOT NULL
);

-- ── Anonymous user identity ──────────────────────────────────
-- 数据来源：db/migration/V20261011__anonymous_user_id.sql
CREATE TABLE IF NOT EXISTS anonymous_user_id (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fingerprint VARCHAR(255) NOT NULL,
    anonymous_id VARCHAR(128) NOT NULL,
    -- MySQL 8.0.43 + explicit_defaults_for_timestamp=1 下，TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
    -- 会被判为 "Invalid default value"；统一用 DATETIME(3) + CURRENT_TIMESTAMP(3)。
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (fingerprint),
    INDEX idx_anonymous_fingerprint (fingerprint)
);

-- ── Message feedback ─────────────────────────────────────────
-- 数据来源：db/migration/V20261011__message_feedback.sql
CREATE TABLE IF NOT EXISTS message_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    rating INT NULL,
    note TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    UNIQUE (message_id)
);

-- ── Slash commands ───────────────────────────────────────────
-- 数据来源：db/migration/V20261011__slash_command.sql
CREATE TABLE IF NOT EXISTS slash_command (
    name VARCHAR(128) PRIMARY KEY,
    description VARCHAR(512) NOT NULL DEFAULT '',
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

-- ── Multi-agent team collaboration (experimental) ────────────
-- 数据来源：db/migration/V20261012__agent_team.sql
-- 列名 leader 与 JdbcAgentTeamRepository 一致；规避 MySQL/H2 保留字 LEAD。
CREATE TABLE IF NOT EXISTS agent_team (
    team_id VARCHAR(64) PRIMARY KEY,
    leader VARCHAR(128) NOT NULL,
    teammates_json TEXT NULL,
    message_strategy VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS team_task (
    task_id VARCHAR(64) PRIMARY KEY,
    team_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    assignee VARCHAR(128) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_team_task_team (team_id)
);

CREATE TABLE IF NOT EXISTS team_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id VARCHAR(64) NOT NULL,
    from_agent VARCHAR(128) NOT NULL,
    to_agent VARCHAR(128) NOT NULL,
    content TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_team_message_team (team_id)
);

-- ── Feishu bot ───────────────────────────────────────────────
-- 数据来源：db/migration/V20261102__feishu_bot.sql
CREATE TABLE IF NOT EXISTS feishu_bot (
    bot_id VARCHAR(64) NOT NULL,
    bot_name VARCHAR(128) DEFAULT NULL,
    app_id VARCHAR(128) NOT NULL,
    secret_ref VARCHAR(128) DEFAULT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    agent_base_id VARCHAR(128) DEFAULT NULL,
    channel_code VARCHAR(64) DEFAULT NULL,
    cwd VARCHAR(1024) DEFAULT NULL,
    group_require_mention TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (bot_id)
);
