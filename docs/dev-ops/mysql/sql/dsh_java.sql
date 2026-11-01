
SET NAMES utf8mb4;

CREATE database if NOT EXISTS `dsh_java` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `dsh_java`;

# 转储表 harness_goal_state
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_goal_state`;

CREATE TABLE `harness_goal_state` (
  `session_id` varchar(64) NOT NULL,
  `goal_id` varchar(128) NOT NULL,
  `revision` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `status` varchar(32) NOT NULL,
  `blocked_reason` text,
  `max_rounds` int NOT NULL,
  `rounds_started` int NOT NULL,
  `continuation_enabled` tinyint(1) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_model_setting
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_model_setting`;

CREATE TABLE `harness_model_setting` (
  `provider_code` varchar(64) NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `base_url` varchar(255) DEFAULT NULL,
  `api_key_ref` varchar(128) DEFAULT NULL,
  `protocol` varchar(32) NOT NULL DEFAULT 'openai',
  `enabled` tinyint(1) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '0',
  `updated_at` timestamp(3) NOT NULL,
  `channel_code` varchar(64) NOT NULL DEFAULT 'default',
  `display_name` varchar(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_model_setting` WRITE;

INSERT INTO `harness_model_setting` (`provider_code`, `model_code`, `base_url`, `api_key_ref`, `protocol`, `enabled`, `active`, `updated_at`, `channel_code`, `display_name`)
VALUES
	('custom','Deepseek-V4.0-Pro','http://127.0.0.1:28080/v1','sk-44195eb1cf3346748eaeeb4828906ea3','openai',1,1,'2026-09-12 14:34:24.789','channel-3118f980-a706-4bc0-b769-df148a60fe22','测试'),
	('deepseek','Deepseek-V4.1-Flash','http://127.0.0.1:28080/v1',NULL,'openai',1,0,'2026-09-12 14:35:21.566','default','默认渠道');

UNLOCK TABLES;


# 转储表 harness_plugin_config
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_plugin_config`;

CREATE TABLE `harness_plugin_config` (
  `plugin_id` varchar(128) NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `config_value` text,
  `config_layer` varchar(32) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`plugin_id`,`config_key`,`config_layer`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_plugin_installation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_plugin_installation`;

CREATE TABLE `harness_plugin_installation` (
  `plugin_id` varchar(128) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  `plugin_version` varchar(64) NOT NULL,
  `runtime_type` varchar(64) NOT NULL,
  `install_mode` varchar(64) NOT NULL,
  `source_path` varchar(512) NOT NULL,
  `entrypoint` varchar(512) NOT NULL,
  `status` varchar(32) NOT NULL,
  `installed_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_plugin_installation` WRITE;

INSERT INTO `harness_plugin_installation` (`plugin_id`, `display_name`, `plugin_version`, `runtime_type`, `install_mode`, `source_path`, `entrypoint`, `status`, `installed_at`)
VALUES
	('dsh-demo-plugin','DSH Demo Plugin','1.0.0','DSH_NODE_BRIDGE','NODE_BRIDGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','index.js','ACTIVE','2026-09-12 07:17:10.902'),
	('dsh-java-mysql-plugin','DSH MySQL Plugin','0.1.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','dsh-java-mysql-plugin.jar','ACTIVE','2026-09-12 14:51:03.950'),
	('dsh-java-mall-plugin','DSH Java Mall App','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','dsh-java-mall-plugin.jar','ACTIVE','2026-09-12 14:53:57.130'),
	('sample-tools','Sample Tools Plugin','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','sample-tools.jar','ACTIVE','2026-09-12 07:17:07.776');

UNLOCK TABLES;


# 转储表 harness_plugin_runtime_binding
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_plugin_runtime_binding`;

CREATE TABLE `harness_plugin_runtime_binding` (
  `plugin_id` varchar(128) NOT NULL,
  `package_type` varchar(64) NOT NULL,
  `manifest_path` varchar(512) DEFAULT NULL,
  `working_directory` varchar(512) DEFAULT NULL,
  `runtime_command` varchar(1024) DEFAULT NULL,
  `bridge_ready` tinyint(1) NOT NULL,
  `message` text NOT NULL,
  `checked_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_plugin_runtime_binding` WRITE;

INSERT INTO `harness_plugin_runtime_binding` (`plugin_id`, `package_type`, `manifest_path`, `working_directory`, `runtime_command`, `bridge_ready`, `message`, `checked_at`)
VALUES
	('dsh-demo-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/package.json','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','node /Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/index.js',1,'dsh package detected with executable Node entrypoint','2026-09-12 14:34:21.474'),
	('dsh-java-mysql-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mysql-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:51:03.958'),
	('dsh-java-mall-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mall-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:53:57.139'),
	('sample-tools','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:sample-tools',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:35:21.516');

UNLOCK TABLES;


# 转储表 harness_session
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_session`;

CREATE TABLE `harness_session` (
  `session_id` varchar(64) NOT NULL,
  `workspace_id` varchar(128) NOT NULL,
  `profile_code` varchar(64) NOT NULL,
  `execution_mode` varchar(32) NOT NULL,
  `resolved_model` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  `channel_code` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_session_event
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_session_event`;

CREATE TABLE `harness_session_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `payload` text NOT NULL,
  `occurred_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_harness_session_event_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_session_event_log
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_session_event_log`;

CREATE TABLE `harness_session_event_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `seq` bigint NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_time` timestamp(3) NOT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `surface_op` varchar(16) DEFAULT NULL,
  `source_event_seqs` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_id` (`session_id`,`seq`),
  KEY `idx_harness_session_event_log_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_session_header
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_session_header`;

CREATE TABLE `harness_session_header` (
  `session_id` varchar(64) NOT NULL,
  `version` int NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  `cwd` varchar(512) DEFAULT NULL,
  `parent_session` varchar(64) DEFAULT NULL,
  `seed_length` int NOT NULL DEFAULT '0',
  `origin` varchar(32) DEFAULT NULL,
  `delegation_depth` int NOT NULL DEFAULT '0',
  `agent_preset` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_session_header` WRITE;

INSERT INTO `harness_session_header` (`session_id`, `version`, `created_at`, `cwd`, `parent_session`, `seed_length`, `origin`, `delegation_depth`, `agent_preset`)
VALUES
	('1e070121-575e-405a-bb99-8bdb6d11c32b',0,'2026-09-12 07:23:37.444','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789169010271'),
	('3cfbfdaa-40d3-4fda-9302-e36dd7cfc780',0,'2026-09-12 14:34:32.940','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789194872916'),
	('7c57a246-1149-415d-9cdb-fb3b468cd647',0,'2026-09-12 14:52:09.557','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'3181cff2-e1bc-4153-b83e-126fee8360e0'),
	('ac6a3454-2023-43d5-acaa-3df8f5939e98',0,'2026-09-12 14:34:58.773','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'customer-service-demo');

UNLOCK TABLES;


# 转储表 harness_task
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_task`;

CREATE TABLE `harness_task` (
  `task_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `prompt` text NOT NULL,
  `enabled_tools_json` text NOT NULL,
  `status` varchar(32) NOT NULL,
  `submitted_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `fk_harness_task_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 harness_tool_catalog
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_tool_catalog`;

CREATE TABLE `harness_tool_catalog` (
  `tool_code` varchar(128) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  `description` text,
  `category` varchar(64) NOT NULL,
  `requires_approval` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_tool_catalog` WRITE;

INSERT INTO `harness_tool_catalog` (`tool_code`, `display_name`, `description`, `category`, `requires_approval`)
VALUES
	('fs.read','Read File','Read file contents from the workspace.','fs',0),
	('fs.write','Write File','Write or overwrite file contents in the workspace.','fs',1),
	('mcp.call','MCP Tool','Invoke an MCP server tool.','mcp',1),
	('shell.exec','Execute Shell','Run a shell command in the sandbox.','shell',1),
	('web.search','Web Search','Search the web for information.','web',0);

UNLOCK TABLES;


# 转储表 harness_tool_profile_binding
# ------------------------------------------------------------

DROP TABLE IF EXISTS `harness_tool_profile_binding`;

CREATE TABLE `harness_tool_profile_binding` (
  `profile_code` varchar(64) NOT NULL,
  `tool_code` varchar(128) NOT NULL,
  PRIMARY KEY (`profile_code`,`tool_code`),
  KEY `fk_harness_tool_binding_tool` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `harness_tool_profile_binding` WRITE;

INSERT INTO `harness_tool_profile_binding` (`profile_code`, `tool_code`)
VALUES
	('headless','fs.read'),
	('web','fs.read'),
	('headless','web.search'),
	('web','web.search');

UNLOCK TABLES;


# 转储表 agent_team
# ------------------------------------------------------------

DROP TABLE IF EXISTS `agent_team`;

CREATE TABLE `agent_team` (
  `team_id` varchar(64) NOT NULL,
  `leader` varchar(128) NOT NULL,
  `teammates_json` text,
  `message_strategy` varchar(32) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 anonymous_user_id
# ------------------------------------------------------------

DROP TABLE IF EXISTS `anonymous_user_id`;

CREATE TABLE `anonymous_user_id` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fingerprint` varchar(255) NOT NULL,
  `anonymous_id` varchar(128) NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_anonymous_fingerprint` (`fingerprint`),
  KEY `idx_anonymous_fingerprint` (`fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 checkpoint
# ------------------------------------------------------------

DROP TABLE IF EXISTS `checkpoint`;

CREATE TABLE `checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `step_index` bigint NOT NULL,
  `reason` varchar(64) NOT NULL,
  `context_snapshot_json` text,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_checkpoint_session` (`session_id`,`step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 digital_human_endpoint
# ------------------------------------------------------------

DROP TABLE IF EXISTS `digital_human_endpoint`;

CREATE TABLE `digital_human_endpoint` (
  `id` varchar(128) NOT NULL,
  `digital_human_id` varchar(128) NOT NULL,
  `endpoint_type` varchar(64) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `credential_ref` varchar(128) DEFAULT NULL,
  `protocol_version` varchar(32) DEFAULT NULL,
  `agent_card` text,
  `health_state` varchar(32) NOT NULL,
  `last_checked_at` timestamp(3) NULL DEFAULT NULL,
  `latency_ms` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dhe_digital_human_id` (`digital_human_id`),
  KEY `idx_dhe_health_state` (`health_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 message_feedback
# ------------------------------------------------------------

DROP TABLE IF EXISTS `message_feedback`;

CREATE TABLE `message_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(64) NOT NULL,
  `rating` int DEFAULT NULL,
  `note` text,
  `created_at` timestamp(3) NOT NULL,
  `updated_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_feedback` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 session_workspace
# ------------------------------------------------------------

DROP TABLE IF EXISTS `session_workspace`;

CREATE TABLE `session_workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workspace_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_session_workspace` (`workspace_id`,`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 slash_command
# ------------------------------------------------------------

DROP TABLE IF EXISTS `slash_command`;

CREATE TABLE `slash_command` (
  `name` varchar(128) NOT NULL,
  `description` varchar(512) NOT NULL DEFAULT '',
  `disabled` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 spill_ref
# ------------------------------------------------------------

DROP TABLE IF EXISTS `spill_ref`;

CREATE TABLE `spill_ref` (
  `ref_id` varchar(64) NOT NULL,
  `content` longtext,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 team_message
# ------------------------------------------------------------

DROP TABLE IF EXISTS `team_message`;

CREATE TABLE `team_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` varchar(64) NOT NULL,
  `from_agent` varchar(128) NOT NULL,
  `to_agent` varchar(128) NOT NULL,
  `content` text,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_team_message_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 team_task
# ------------------------------------------------------------

DROP TABLE IF EXISTS `team_task`;

CREATE TABLE `team_task` (
  `task_id` varchar(64) NOT NULL,
  `team_id` varchar(64) NOT NULL,
  `title` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL,
  `assignee` varchar(128) DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `idx_team_task_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 token_snapshot
# ------------------------------------------------------------

DROP TABLE IF EXISTS `token_snapshot`;

CREATE TABLE `token_snapshot` (
  `session_id` varchar(64) NOT NULL,
  `last_event_step_index` bigint NOT NULL,
  `input_tokens` int NOT NULL,
  `output_tokens` int NOT NULL,
  `cache_read_tokens` int DEFAULT NULL,
  `cache_write_tokens` int DEFAULT NULL,
  `reasoning_tokens` int DEFAULT NULL,
  `model_calls` bigint NOT NULL,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# 转储表 workspace
# ------------------------------------------------------------

DROP TABLE IF EXISTS `workspace`;

CREATE TABLE `workspace` (
  `workspace_id` varchar(64) NOT NULL,
  `root_path` varchar(1024) NOT NULL,
  `created_at` timestamp(3) NOT NULL,
  PRIMARY KEY (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;



# ============================================================
# 补建表（2026-11-01）：数字人档案 + 协作房间
# ------------------------------------------------------------
# 以下 6 张表此前只有 Java DAO 引用（IDigitalHumanDao / ICollaborationDao），
# 本转储文件、schema.sql、db/migration 三处都没有建表语句，
# 导致按本文件初始化的库上访问数字人/协作功能时抛
#   "Table 'dsh_java.digital_human' doesn't exist"
# 现按 DigitalHumanPO / CollaborationPO 与 DAO 的 SQL 列补齐，
# 与 schema.sql、db/migration/V20261101__digital_human_and_collaboration.sql 保持一致。
# ============================================================

# 转储表 digital_human
# ------------------------------------------------------------
# 说明：数字人档案表。数字人的基础信息与并发/审批策略；
#       端点（地址、协议、健康状态）存在 digital_human_endpoint，通过 digital_human_id 关联。

DROP TABLE IF EXISTS `digital_human`;

CREATE TABLE `digital_human` (
  `id` varchar(128) NOT NULL COMMENT '主键ID',
  `display_name` varchar(128) NOT NULL COMMENT '展示名称',
  `avatar_ref` varchar(512) DEFAULT NULL COMMENT '头像引用（emoji 或 URL）',
  `purpose` varchar(1024) DEFAULT NULL COMMENT '用途描述',
  `role_tags` varchar(512) DEFAULT NULL COMMENT '能力标签（逗号分隔）',
  `theme_color` varchar(32) DEFAULT NULL COMMENT '主题色',
  `approval_policy` varchar(32) DEFAULT NULL COMMENT '审批策略：AUTO_ALLOW / WRITE_REQUIRES_APPROVAL / ALWAYS_CONFIRM',
  `concurrency_limit` int DEFAULT '1' COMMENT '并发任务上限（1-8）',
  `project_path` varchar(1024) DEFAULT NULL COMMENT '绑定项目路径（本地数字人的工作目录）',
  `created_at` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数字人档案表：数字人基础信息与并发/审批策略';


# 转储表 collaboration_room
# ------------------------------------------------------------
# 说明：协作房间表。一次多人（多数字人）协作的容器，挂在工作区下；
#       run_state 表示房间整体运行状态，objective 由编排器首次填充。

DROP TABLE IF EXISTS `collaboration_room`;

CREATE TABLE `collaboration_room` (
  `id` varchar(128) NOT NULL COMMENT '房间ID',
  `workspace_id` varchar(128) NOT NULL COMMENT '工作区ID',
  `title` varchar(256) DEFAULT NULL COMMENT '房间标题',
  `objective` text COMMENT '协作目标',
  `orchestration_mode` varchar(64) DEFAULT NULL COMMENT '编排模式',
  `run_state` varchar(32) DEFAULT NULL COMMENT '运行状态',
  `created_at` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_cr_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作房间表';


# 转储表 room_participant
# ------------------------------------------------------------
# 说明：协作房间参与者表。房间与数字人的多对多关系，
#       room_role 为房间内角色、presence 为在场状态、active_task_label 为当前任务摘要。

DROP TABLE IF EXISTS `room_participant`;

CREATE TABLE `room_participant` (
  `id` varchar(128) NOT NULL COMMENT '主键ID',
  `room_id` varchar(128) NOT NULL COMMENT '房间ID',
  `digital_human_id` varchar(128) NOT NULL COMMENT '数字人ID',
  `room_role` varchar(64) DEFAULT NULL COMMENT '房间角色',
  `presence` varchar(32) DEFAULT NULL COMMENT '在场状态',
  `active_task_label` varchar(512) DEFAULT NULL COMMENT '当前任务摘要',
  `joined_at` timestamp(3) NULL DEFAULT NULL COMMENT '加入时间',
  PRIMARY KEY (`id`),
  KEY `idx_rp_room` (`room_id`),
  KEY `idx_rp_human` (`digital_human_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作房间参与者表';


# 转储表 collaboration_task
# ------------------------------------------------------------
# 说明：协作任务表。房间内的任务单，可指派给某数字人（assigned_to）并挂到远端会话；
#       depends_on / *_artifacts 存 JSON，state 为任务状态机取值。

DROP TABLE IF EXISTS `collaboration_task`;

CREATE TABLE `collaboration_task` (
  `id` varchar(128) NOT NULL COMMENT '任务ID',
  `room_id` varchar(128) NOT NULL COMMENT '房间ID',
  `parent_task_id` varchar(128) DEFAULT NULL COMMENT '父任务ID（子任务拆解）',
  `title` varchar(512) DEFAULT NULL COMMENT '任务标题',
  `instruction` text COMMENT '任务指令',
  `assigned_to` varchar(128) DEFAULT NULL COMMENT '指派给的数字人ID',
  `requested_by` varchar(128) DEFAULT NULL COMMENT '发起方ID',
  `state` varchar(32) DEFAULT NULL COMMENT '任务状态',
  `depends_on` text COMMENT '依赖任务（JSON）',
  `input_artifacts` text COMMENT '输入产物（JSON）',
  `output_artifacts` text COMMENT '输出产物（JSON）',
  `remote_session_id` varchar(128) DEFAULT NULL COMMENT '远端会话ID（远端数字人执行时）',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID',
  `error_summary` text COMMENT '错误摘要',
  `created_at` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ct_room` (`room_id`),
  KEY `idx_ct_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作任务表';


# 转储表 collaboration_artifact
# ------------------------------------------------------------
# 说明：协作产物表。任务产出的文档/代码/结论等，version 用于同名产物迭代。

DROP TABLE IF EXISTS `collaboration_artifact`;

CREATE TABLE `collaboration_artifact` (
  `id` varchar(128) NOT NULL COMMENT '产物ID',
  `room_id` varchar(128) NOT NULL COMMENT '房间ID',
  `task_id` varchar(128) DEFAULT NULL COMMENT '来源任务ID',
  `producer_id` varchar(128) DEFAULT NULL COMMENT '生产者ID（数字人）',
  `kind` varchar(64) DEFAULT NULL COMMENT '产物类型',
  `title` varchar(512) DEFAULT NULL COMMENT '产物标题',
  `version` int DEFAULT NULL COMMENT '版本号',
  `content` text COMMENT '产物内容',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID',
  `created_at` timestamp(3) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ca_room` (`room_id`),
  KEY `idx_ca_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作产物表';


# 转储表 room_event
# ------------------------------------------------------------
# 说明：协作房间事件表。房间内的时序事件流，seq 为房间内单调递增序号，
#       前端按 (room_id, seq > afterSeq) 增量拉取；payload 为 JSON 事件负载。

DROP TABLE IF EXISTS `room_event`;

CREATE TABLE `room_event` (
  `id` varchar(128) NOT NULL COMMENT '事件ID',
  `room_id` varchar(128) NOT NULL COMMENT '房间ID',
  `seq` bigint NOT NULL COMMENT '房间内事件序号（单调递增）',
  `task_id` varchar(128) DEFAULT NULL COMMENT '关联任务ID',
  `participant_id` varchar(128) DEFAULT NULL COMMENT '关联参与者ID',
  `digital_human_id` varchar(128) DEFAULT NULL COMMENT '关联数字人ID',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型',
  `occurred_at` timestamp(3) NULL DEFAULT NULL COMMENT '发生时间',
  `visibility` varchar(32) DEFAULT NULL COMMENT '可见范围',
  `payload` text COMMENT '事件负载（JSON）',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID',
  PRIMARY KEY (`id`),
  KEY `idx_re_room_seq` (`room_id`,`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作房间事件表';
