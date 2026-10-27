-- 转储表 harness_goal_state
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_goal_state;

CREATE TABLE harness_goal_state (
  session_id varchar(64) NOT NULL COMMENT '会话ID（主键）',
  goal_id varchar(128) NOT NULL COMMENT '目标ID',
  revision bigint NOT NULL COMMENT '目标修订号',
  title varchar(255) NOT NULL COMMENT '目标标题',
  description text COMMENT '目标描述',
  status varchar(32) NOT NULL COMMENT '目标状态',
  blocked_reason text COMMENT '阻塞原因',
  max_rounds int NOT NULL COMMENT '最大轮次上限',
  rounds_started int NOT NULL COMMENT '已启动轮次',
  continuation_enabled tinyint(1) NOT NULL COMMENT '是否启用自动续跑',
  created_at timestamp(3) NOT NULL COMMENT '创建时间',
  updated_at timestamp(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (session_id)
) COMMENT='Goal 状态表：持久化会话目标状态与续跑控制';



-- 转储表 harness_model_setting
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_model_setting;

CREATE TABLE harness_model_setting (
  provider_code varchar(64) NOT NULL COMMENT '供应商标识',
  model_code varchar(128) NOT NULL COMMENT '模型编码',
  base_url varchar(255) DEFAULT NULL COMMENT '服务端点基地址',
  api_key_ref varchar(128) DEFAULT NULL COMMENT 'API Key 引用（仅存引用不存明文）',
  protocol varchar(32) NOT NULL DEFAULT 'openai' COMMENT '协议类型（openai 等）',
  enabled tinyint(1) NOT NULL COMMENT '是否启用',
  active tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为当前激活渠道',
  updated_at timestamp(3) NOT NULL COMMENT '更新时间',
  channel_code varchar(64) NOT NULL DEFAULT 'default' COMMENT '渠道编码（主键）',
  display_name varchar(128) NOT NULL DEFAULT '' COMMENT '渠道显示名称',
  PRIMARY KEY (channel_code)
) COMMENT='模型渠道配置表：模型供应商、协议与激活状态';


INSERT INTO harness_model_setting (provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at, channel_code, display_name)
VALUES
	('custom','Deepseek-V4.0-Pro','http://127.0.0.1:28080/v1','sk-44195eb1cf3346748eaeeb4828906ea3','openai',1,1,'2026-09-12 14:34:24.789','channel-3118f980-a706-4bc0-b769-df148a60fe22','测试'),
	('deepseek','Deepseek-V4.1-Flash','http://127.0.0.1:28080/v1',NULL,'openai',1,0,'2026-09-12 14:35:21.566','default','默认渠道');


-- 转储表 harness_plugin_config
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_plugin_config;

CREATE TABLE harness_plugin_config (
  plugin_id varchar(128) NOT NULL COMMENT '插件ID',
  config_key varchar(128) NOT NULL COMMENT '配置键',
  config_value text COMMENT '配置值',
  config_layer varchar(32) NOT NULL COMMENT '配置层级',
  updated_at timestamp(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (plugin_id,config_key,config_layer)
) COMMENT='插件配置表：按配置层级持久化插件键值配置';



-- 转储表 harness_plugin_installation
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_plugin_installation;

CREATE TABLE harness_plugin_installation (
  plugin_id varchar(128) NOT NULL COMMENT '插件ID（主键）',
  display_name varchar(255) NOT NULL COMMENT '插件显示名称',
  plugin_version varchar(64) NOT NULL COMMENT '插件版本',
  runtime_type varchar(64) NOT NULL COMMENT '运行时类型（JAVA_NATIVE/DSH_NODE_BRIDGE 等）',
  install_mode varchar(64) NOT NULL COMMENT '安装模式（NATIVE/NODE_BRIDGE 等）',
  source_path varchar(512) NOT NULL COMMENT '安装源路径',
  entrypoint varchar(512) NOT NULL COMMENT '入口文件或命令',
  status varchar(32) NOT NULL COMMENT '安装状态（如 ACTIVE）',
  installed_at timestamp(3) NOT NULL COMMENT '安装时间',
  PRIMARY KEY (plugin_id)
) COMMENT='插件安装表：已安装插件的元数据与状态';


INSERT INTO harness_plugin_installation (plugin_id, display_name, plugin_version, runtime_type, install_mode, source_path, entrypoint, status, installed_at)
VALUES
	('dsh-demo-plugin','DSH Demo Plugin','1.0.0','DSH_NODE_BRIDGE','NODE_BRIDGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','index.js','ACTIVE','2026-09-12 07:17:10.902'),
	('dsh-java-mysql-plugin','DSH MySQL Plugin','0.1.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','dsh-java-mysql-plugin.jar','ACTIVE','2026-09-12 14:51:03.950'),
	('dsh-java-mall-plugin','DSH Java Mall App','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','dsh-java-mall-plugin.jar','ACTIVE','2026-09-12 14:53:57.130'),
	('sample-tools','Sample Tools Plugin','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','sample-tools.jar','ACTIVE','2026-09-12 07:17:07.776');


-- 转储表 harness_plugin_runtime_binding
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_plugin_runtime_binding;

CREATE TABLE harness_plugin_runtime_binding (
  plugin_id varchar(128) NOT NULL COMMENT '插件ID（主键）',
  package_type varchar(64) NOT NULL COMMENT '包类型（如 DSH_PACKAGE）',
  manifest_path varchar(512) DEFAULT NULL COMMENT '清单文件路径',
  working_directory varchar(512) DEFAULT NULL COMMENT '运行时工作目录',
  runtime_command varchar(1024) DEFAULT NULL COMMENT '运行时启动命令',
  bridge_ready tinyint(1) NOT NULL COMMENT '桥接是否就绪',
  message text NOT NULL COMMENT '就绪状态描述',
  checked_at timestamp(3) NOT NULL COMMENT '检查时间',
  PRIMARY KEY (plugin_id)
) COMMENT='插件运行时绑定表：插件运行时包与启动绑定';


INSERT INTO harness_plugin_runtime_binding (plugin_id, package_type, manifest_path, working_directory, runtime_command, bridge_ready, message, checked_at)
VALUES
	('dsh-demo-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/package.json','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','node /Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/index.js',1,'dsh package detected with executable Node entrypoint','2026-09-12 14:34:21.474'),
	('dsh-java-mysql-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mysql-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:51:03.958'),
	('dsh-java-mall-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mall-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:53:57.139'),
	('sample-tools','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:sample-tools',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:35:21.516');


-- 转储表 harness_session
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_session;

CREATE TABLE harness_session (
  session_id varchar(64) NOT NULL COMMENT '会话ID（主键）',
  workspace_id varchar(128) NOT NULL COMMENT '工作空间ID',
  profile_code varchar(64) NOT NULL COMMENT 'Profile 编码',
  execution_mode varchar(32) NOT NULL COMMENT '执行模式',
  resolved_model varchar(128) NOT NULL COMMENT '最终解析模型',
  status varchar(32) NOT NULL COMMENT '会话状态',
  created_at timestamp(3) NOT NULL COMMENT '创建时间',
  channel_code varchar(64) DEFAULT NULL COMMENT '渠道编码',
  PRIMARY KEY (session_id)
) COMMENT='会话表：会话基本信息与执行上下文';


-- 转储表 harness_session_event
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_session_event;

CREATE TABLE harness_session_event (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  session_id varchar(64) NOT NULL COMMENT '会话ID',
  event_type varchar(128) NOT NULL COMMENT '事件类型',
  payload text NOT NULL COMMENT '事件负载',
  occurred_at timestamp(3) NOT NULL COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_harness_session_event_session (session_id)
) COMMENT='会话事件表：会话事件流记录';



-- 转储表 harness_session_event_log
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_session_event_log;

CREATE TABLE harness_session_event_log (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  session_id varchar(64) NOT NULL COMMENT '会话ID',
  seq bigint NOT NULL COMMENT '事件序号',
  event_type varchar(64) NOT NULL COMMENT '事件类型',
  event_time timestamp(3) NOT NULL COMMENT '事件时间',
  payload_json longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '事件负载 JSON',
  surface_op varchar(16) DEFAULT NULL COMMENT '表面操作（surface op）类型',
  source_event_seqs varchar(512) DEFAULT NULL COMMENT '来源事件序号',
  PRIMARY KEY (id),
  UNIQUE KEY session_id (session_id,seq),
  KEY idx_harness_session_event_log_session (session_id)
) COMMENT='会话事件日志表：带序号的事件日志';



-- 转储表 harness_session_header
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_session_header;

CREATE TABLE harness_session_header (
  session_id varchar(64) NOT NULL COMMENT '会话ID（主键）',
  version int NOT NULL COMMENT '头部版本号',
  created_at timestamp(3) NOT NULL COMMENT '创建时间',
  cwd varchar(512) DEFAULT NULL COMMENT '当前工作目录',
  parent_session varchar(64) DEFAULT NULL COMMENT '父会话ID',
  seed_length int NOT NULL DEFAULT '0' COMMENT '初始种子长度',
  origin varchar(32) DEFAULT NULL COMMENT '会话来源',
  delegation_depth int NOT NULL DEFAULT '0' COMMENT '委派深度',
  agent_preset varchar(128) DEFAULT NULL COMMENT 'Agent 预设',
  PRIMARY KEY (session_id)
) COMMENT='会话头表：会话元信息与委派关系';

LOCK TABLES harness_session_header WRITE;
/*!40000 ALTER TABLE harness_session_header DISABLE KEYS */;

INSERT INTO harness_session_header (session_id, version, created_at, cwd, parent_session, seed_length, origin, delegation_depth, agent_preset)
VALUES
	('1e070121-575e-405a-bb99-8bdb6d11c32b',0,'2026-09-12 07:23:37.444','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789169010271'),
	('3cfbfdaa-40d3-4fda-9302-e36dd7cfc780',0,'2026-09-12 14:34:32.940','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789194872916'),
	('7c57a246-1149-415d-9cdb-fb3b468cd647',0,'2026-09-12 14:52:09.557','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'3181cff2-e1bc-4153-b83e-126fee8360e0'),
	('ac6a3454-2023-43d5-acaa-3df8f5939e98',0,'2026-09-12 14:34:58.773','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'customer-service-demo');


-- 转储表 harness_task
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_task;

CREATE TABLE harness_task (
  task_id varchar(64) NOT NULL COMMENT '任务ID（主键）',
  session_id varchar(64) NOT NULL COMMENT '会话ID',
  prompt text NOT NULL COMMENT '任务提示词',
  enabled_tools_json text NOT NULL COMMENT '启用工具 JSON',
  status varchar(32) NOT NULL COMMENT '任务状态',
  submitted_at timestamp(3) NOT NULL COMMENT '提交时间',
  PRIMARY KEY (task_id),
  KEY fk_harness_task_session (session_id)
) COMMENT='任务表：会话提交的异步任务';


-- 转储表 harness_tool_catalog
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_tool_catalog;

CREATE TABLE harness_tool_catalog (
  tool_code varchar(128) NOT NULL COMMENT '工具编码（主键）',
  display_name varchar(255) NOT NULL COMMENT '工具显示名称',
  description text COMMENT '工具描述',
  category varchar(64) NOT NULL COMMENT '工具分类',
  requires_approval tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否需要审批',
  PRIMARY KEY (tool_code)
) COMMENT='工具目录表：可用工具清单与审批要求';

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
VALUES
	('fs.read','Read File','Read file contents from the workspace.','fs',0),
	('fs.write','Write File','Write or overwrite file contents in the workspace.','fs',1),
	('mcp.call','MCP Tool','Invoke an MCP server tool.','mcp',1),
	('shell.exec','Execute Shell','Run a shell command in the sandbox.','shell',1),
	('web.search','Web Search','Search the web for information.','web',0);


-- 转储表 harness_tool_profile_binding
-- ------------------------------------------------------------

DROP TABLE IF EXISTS harness_tool_profile_binding;

CREATE TABLE harness_tool_profile_binding (
  profile_code varchar(64) NOT NULL COMMENT 'Profile 编码（联合主键）',
  tool_code varchar(128) NOT NULL COMMENT '工具编码（联合主键）',
  PRIMARY KEY (profile_code,tool_code),
  KEY fk_harness_tool_binding_tool (tool_code)
) COMMENT='工具-Profile 绑定表：Profile 与工具的启用关系';


INSERT INTO harness_tool_profile_binding (profile_code, tool_code)
VALUES
	('headless','fs.read'),
	('web','fs.read'),
	('headless','web.search'),
	('web','web.search');