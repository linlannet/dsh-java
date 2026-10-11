# ************************************************************
# Sequel Ace SQL dump
# 版本号： 20094
#
# https://sequel-ace.com/
# https://github.com/Sequel-Ace/Sequel-Ace
#
# 主机: 127.0.0.1 (MySQL 8.0.42)
# 数据库: dsh_java
# 生成时间: 2026-09-12 07:04:55 +0000
# ************************************************************


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

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
/*!40000 ALTER TABLE `harness_model_setting` DISABLE KEYS */;

INSERT INTO `harness_model_setting` (`provider_code`, `model_code`, `base_url`, `api_key_ref`, `protocol`, `enabled`, `active`, `updated_at`, `channel_code`, `display_name`)
VALUES
	('custom','Deepseek-V4.0-Pro','http://127.0.0.1:28080/v1','sk-44195eb1cf3346748eaeeb4828906ea3','openai',1,1,'2026-09-12 14:34:24.789','channel-3118f980-a706-4bc0-b769-df148a60fe22','测试'),
	('deepseek','Deepseek-V4.1-Flash','http://127.0.0.1:28080/v1',NULL,'openai',1,0,'2026-09-12 14:35:21.566','default','默认渠道');

/*!40000 ALTER TABLE `harness_model_setting` ENABLE KEYS */;
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
/*!40000 ALTER TABLE `harness_plugin_installation` DISABLE KEYS */;

INSERT INTO `harness_plugin_installation` (`plugin_id`, `display_name`, `plugin_version`, `runtime_type`, `install_mode`, `source_path`, `entrypoint`, `status`, `installed_at`)
VALUES
	('dsh-demo-plugin','DSH Demo Plugin','1.0.0','DSH_NODE_BRIDGE','NODE_BRIDGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','index.js','ACTIVE','2026-09-12 07:17:10.902'),
	('dsh-java-mysql-plugin','DSH MySQL Plugin','0.1.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','dsh-java-mysql-plugin.jar','ACTIVE','2026-09-12 14:51:03.950'),
	('dsh-java-mall-plugin','DSH Java Mall App','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','dsh-java-mall-plugin.jar','ACTIVE','2026-09-12 14:53:57.130'),
	('sample-tools','Sample Tools Plugin','1.0.0','JAVA_NATIVE','NATIVE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','sample-tools.jar','ACTIVE','2026-09-12 07:17:07.776');

/*!40000 ALTER TABLE `harness_plugin_installation` ENABLE KEYS */;
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
/*!40000 ALTER TABLE `harness_plugin_runtime_binding` DISABLE KEYS */;

INSERT INTO `harness_plugin_runtime_binding` (`plugin_id`, `package_type`, `manifest_path`, `working_directory`, `runtime_command`, `bridge_ready`, `message`, `checked_at`)
VALUES
	('dsh-demo-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/package.json','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin','node /Users/mac/coding/github/dsh/dsh-java/plugins/dsh-demo-plugin/index.js',1,'dsh package detected with executable Node entrypoint','2026-09-12 14:34:21.474'),
	('dsh-java-mysql-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mysql-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mysql-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:51:03.958'),
	('dsh-java-mall-plugin','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/dsh-java-mall-plugin.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:dsh-java-mall-plugin',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:53:57.139'),
	('sample-tools','DSH_PACKAGE','/Users/mac/coding/github/dsh/dsh-java/plugins/sample-tools.jar','/Users/mac/coding/github/dsh/dsh-java/plugins','java-in-process:sample-tools',1,'java-native plugin JAR ready for in-process loading','2026-09-12 14:35:21.516');

/*!40000 ALTER TABLE `harness_plugin_runtime_binding` ENABLE KEYS */;
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
/*!40000 ALTER TABLE `harness_session_header` DISABLE KEYS */;

INSERT INTO `harness_session_header` (`session_id`, `version`, `created_at`, `cwd`, `parent_session`, `seed_length`, `origin`, `delegation_depth`, `agent_preset`)
VALUES
	('1e070121-575e-405a-bb99-8bdb6d11c32b',0,'2026-09-12 07:23:37.444','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789169010271'),
	('3cfbfdaa-40d3-4fda-9302-e36dd7cfc780',0,'2026-09-12 14:34:32.940','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'agent-1789194872916'),
	('7c57a246-1149-415d-9cdb-fb3b468cd647',0,'2026-09-12 14:52:09.557','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'3181cff2-e1bc-4153-b83e-126fee8360e0'),
	('ac6a3454-2023-43d5-acaa-3df8f5939e98',0,'2026-09-12 14:34:58.773','/Users/mac/coding/github/dsh/dsh-java',NULL,0,NULL,0,'customer-service-demo');

/*!40000 ALTER TABLE `harness_session_header` ENABLE KEYS */;
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
/*!40000 ALTER TABLE `harness_tool_catalog` DISABLE KEYS */;

INSERT INTO `harness_tool_catalog` (`tool_code`, `display_name`, `description`, `category`, `requires_approval`)
VALUES
	('fs.read','Read File','Read file contents from the workspace.','fs',0),
	('fs.write','Write File','Write or overwrite file contents in the workspace.','fs',1),
	('mcp.call','MCP Tool','Invoke an MCP server tool.','mcp',1),
	('shell.exec','Execute Shell','Run a shell command in the sandbox.','shell',1),
	('web.search','Web Search','Search the web for information.','web',0);

/*!40000 ALTER TABLE `harness_tool_catalog` ENABLE KEYS */;
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
/*!40000 ALTER TABLE `harness_tool_profile_binding` DISABLE KEYS */;

INSERT INTO `harness_tool_profile_binding` (`profile_code`, `tool_code`)
VALUES
	('headless','fs.read'),
	('web','fs.read'),
	('headless','web.search'),
	('web','web.search');

/*!40000 ALTER TABLE `harness_tool_profile_binding` ENABLE KEYS */;
UNLOCK TABLES;



/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
