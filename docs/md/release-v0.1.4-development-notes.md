# DeepSeek Harness Java v0.1.4 开发说明

> v0.1.4 的主线是把插件机制从“SPI 可注册”推进到“产物可识别、配置可持久化、运行状态可对账、业务能力可交付”。这一版同时补齐 Java 插件开发技能、一键安装脚本、商城客服插件示例，以及插件相关的接口、控制台、测试与运行可靠性验证。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.4` |
| 变更范围 | `v0.1.3...v0.1.4` |
| 变更规模 | 35 个文件变更，新增约 2528 行，删除约 235 行 |
| 主线 | 插件工程化、插件配置持久化、商城客服插件、插件状态对账 |
| 技术栈 | Java 17、Spring Boot 3.3、DDD 六边形架构 |
| 交付形态 | 单实例 Spring Boot 应用 + 原生 JS Web 控制台 + Java Native 插件 |
| 交付日期 | 2026-09-09 至 2026-09-10 |

## 2. 版本亮点

1. **插件安装工程化**：JAR 与 Maven 依赖两种产物都可以自动解析为插件候选信息，减少手工填写 `pluginId`、名称和版本。
2. **插件配置持久化**：插件键值配置从内存扩展到数据库，支持分层存储和重启后恢复。
3. **插件状态可信**：新增 `plugin_status` 工具，Agent 可以查询登记状态和运行时状态，避免把 `FAILED` 或未运行插件说成可用。
4. **插件状态对账**：宿主启动时会对比插件登记状态和运行时状态，自动修复状态偏差并重新拉起可恢复插件。
5. **插件开发体验增强**：新增 DSH Java 插件开发 Skill 和 `install-plugin.sh`，打通编写、打包、安装、启动路径。
6. **业务插件落地**：新增 `dsh-java-mall-plugin` 商城客服插件，打通商品、订单和物流工具调用链路。
7. **Web 控制台增强**：插件设置页支持 JAR/POM 分析、候选信息确认、安装、启停、卸载和配置编辑。
8. **启动可靠性增强**：MySQL 连接池增加快速失败和连接治理配置，降低外部数据库抖动导致的长时间阻塞。

## 3. 架构变化

v0.1.4 继续保持六边形架构依赖方向，没有让插件作者依赖宿主内部实现。插件侧继续只依赖 `dsh-java-types`。

```text
Web Console / REST API
        ↓
Trigger API + Case 编排
        ↓
Domain：Plugin Registry / Runtime / Tool Catalog / PluginStatusTool
        ↓
Infrastructure：JAR Analyzer / Maven Analyzer / Plugin Config Store / Plugin Runtime
        ↑
dsh-java-types（唯一契约包，scope=provided）
        ↑
插件 JAR（AbstractHarnessPlugin + AbstractTool 实现）
```

本版本的关键扩展点：

- **Trigger 层**新增插件产物分析和插件配置读写接口；
- **Case 层**新增插件候选解析与配置用例；
- **Domain 层**把插件状态暴露成 Agent 可调用的工具；
- **Infrastructure 层**把插件配置落到数据库，并让运行时状态成为对账依据；
- **App 层**在启动阶段执行插件状态对账，修复登记状态和真实运行状态的偏差。

## 4. 插件产物识别与安装

### 4.1 JAR 插件分析

新增 `POST /api/harness/plugins/analyze-jar`。上传的 JAR 会被写入临时目录，然后读取：

```text
META-INF/plugin.yaml
META-INF/services/net.linlan.dsh.domain.spi.JavaHarnessPlugin
```

分析器校验以下信息：

| 校验项 | 说明 |
| --- | --- |
| 文件形态 | 必须是 `.jar` 文件 |
| 插件清单 | 必须存在 `META-INF/plugin.yaml` |
| 必填字段 | `id`、`name`、`version` 不能为空 |
| 入口声明 | 存在 `entrypoint` 或存在 Java SPI 声明 |
| 安装模式 | Java Native 插件返回 `JAVA_NATIVE` |

解析成功后返回 `PluginArtifactCandidateDTO`，控制台可直接回填安装表单，避免插件 ID 和显示名被手工写错。

### 4.2 Maven 依赖分析

新增 `POST /api/harness/plugins/analyze-maven`。用户可以粘贴完整 POM、`<dependencies>` 或单个 `<dependency>` 片段。

服务会：

1. 解析 XML；
2. 展开 POM `properties` 中的 `${...}` 占位符；
3. 读取 `groupId`、`artifactId`、`version`；
4. 到本地 Maven 仓库定位 JAR；
5. 复用 JAR 分析器校验插件清单。

本地仓库不存在产物时，接口会提示先执行 `mvn install` 或 `mvn dependency:copy-dependencies`。这个阶段只读取本地 Maven 仓库，不会隐式联网下载依赖。

## 5. 插件配置持久化

### 5.1 数据模型

新增 `harness_plugin_config` 表：

| 字段 | 说明 |
| --- | --- |
| `plugin_id` | 插件 ID |
| `config_key` | 配置键 |
| `config_value` | 配置值 |
| `config_layer` | 配置层，当前支持 `DEFAULT` 和 `OVERRIDE` |
| `updated_at` | 更新时间 |

主键为 `(plugin_id, config_key, config_layer)`，并通过外键关联插件安装表。这样插件配置可以随插件生命周期一起治理，不会只留在进程内存。

### 5.2 分层合并

`DatabasePluginConfigStore` 支持：

| 能力 | 说明 |
| --- | --- |
| `get` | 读取单键有效值 |
| `getAll` | 合并读取全部有效值 |
| `setOverride` / `removeOverride` | 管理 `OVERRIDE` 层 |
| `clearOverrides` | 清空某个插件的覆盖配置 |
| `setDefaults` | 重写某个插件的 `DEFAULT` 层 |

合并规则为 `OVERRIDE` 覆盖 `DEFAULT`。控制台当前保存的是 `DEFAULT` 层，配置读写接口已经为后续租户、环境或灰度覆盖预留扩展能力。

### 5.3 配置接口

```http
GET /api/harness/plugins/{pluginId}/config
POST /api/harness/plugins/{pluginId}/config
```

保存请求结构：

```json
{
  "configs": [
    { "key": "mall.base-url", "value": "http://127.0.0.1:18080" },
    { "key": "mall.service-token", "value": "mall-internal-demo-token" }
  ]
}
```

保存时会：

1. 校验插件已安装；
2. 忽略空 key；
3. 去除 key 前后空白；
4. 空值统一保存为空字符串；
5. 清空旧的 `DEFAULT` 层后写入新集合。

插件内部继续通过 `PluginContext#getConfig(key)` 读取配置。业务地址、Token、开关、模型名、提示词策略等都不需要硬编码在插件代码里。

## 6. 插件状态治理

### 6.1 Agent 可用的状态工具

新增 `plugin_status` 工具，注册到 Agent 工具目录：

```text
plugin_status
  → 读取插件登记表
  → 查询插件运行时状态
  → 输出 registryStatus / running / runtimeCommand / message
```

该工具支持：

- 不传 `pluginId` 时列出全部插件；
- 传 `pluginId` 时查询指定插件；
- 插件不存在时返回 `PLUGIN_NOT_FOUND`；
- 并发安全执行。

Agent 现在可以在回答“插件是否可用”前先检查权威状态，而不是只依赖数据库登记状态或历史对话记忆。

### 6.2 启动状态对账

`PresetPluginLoader` 从“只恢复 ACTIVE 插件”改为 `reconcilePersistedPlugins()`：

```text
宿主启动
  → 读取所有已登记插件
  → 跳过预置插件，由预置逻辑单独处理
  → 查询真实运行时状态
  → 运行中但登记非 ACTIVE：纠正为 ACTIVE
  → 未运行且登记为 DISABLED/UNINSTALLED：保持不拉起
  → 未运行且登记可恢复：重新执行 run
```

这样解决了两个实际场景：

1. 宿主重启后，数据库登记仍是 `ACTIVE`，但进程内运行状态丢失，需要重新拉起；
2. 运行时实际已经拉起，但数据库状态仍停留在 `FAILED`，需要把登记状态纠正为可用。

### 6.3 预置插件恢复

预置插件仍然通过 `harness.extensions.plugins.preset` 声明。启动时按插件 ID 做存在性检查：

- 已存在且 `DISABLED`，如果 `auto-start=true` 会重新启用；
- 不存在则按配置安装；
- `auto-start=true` 时继续执行运行。

预置插件不会因为已经登记而重复安装。

## 7. 插件开发体验

### 7.1 DSH Java 插件开发 Skill

新增：

```text
docs/skills/dsh-java-plugin-skills/SKILL.md
```

该 Skill 面向“零文档检索”的插件开发场景，覆盖：

- archetype 生成；
- 插件入口与工具实现；
- `plugin.yaml` 和 Java SPI 注册；
- `PluginContext` 能力；
- 参数 Schema、Hook、事件、提示词；
- 常见错误和编码规范；
- 打包与重新安装流程。

Agent 在协助开发者创建插件时，可以直接使用该 Skill 提供一致的开发路径。

### 7.2 一键安装脚本

新增：

```text
scripts/install-plugin.sh
```

典型用法：

```bash
./scripts/install-plugin.sh --jar /path/to/my-plugin.jar
```

脚本会：

1. 归一化 JAR 路径；
2. 从 `META-INF/plugin.yaml` 读取 `id`、`name`、`version`；
3. 允许通过参数或 `PLUGIN_HOST` 覆盖宿主地址；
4. 调用 `POST /api/harness/plugins/install`；
5. 调用 `POST /api/harness/plugins/run`；
6. 输出 Agent 可见的工具命名规则 `plugin__<pluginId>__<toolName>`。

可选参数包括 `--id`、`--name`、`--version`、`--host` 和 `--entrypoint`，便于 CI 或脚本化部署中覆盖默认元数据。

## 8. Web 控制台增强

插件设置页从静态列表升级为插件运维工作台：

| 能力 | 说明 |
| --- | --- |
| 插件列表 | 展示插件 ID、名称、版本、运行时类型、登记状态 |
| JAR 上传分析 | 选择本地 JAR 后自动解析候选信息 |
| Maven 依赖分析 | 粘贴 POM 片段后自动解析候选信息 |
| 安装确认 | 展示插件 ID、版本、来源路径和校验结果 |
| 启停卸载 | 支持运行、停止、启用、禁用和卸载 |
| 配置编辑 | 按插件读取和保存键值配置 |
| 使用提示 | 安装后提示工具命名规则和验证方式 |

控制台改造重点不是新增入口数量，而是把“安装前检查、安装时确认、安装后配置和验证”串成闭环。

## 9. 商城客服插件示例

新增：

```text
plugins/dsh-java-mall-plugin.jar
```

插件 ID 为 `dsh-java-mall-plugin`，用于演示“核心 Agent 由 Harness 承载，业务能力通过插件接入”的形态。

### 9.1 注册能力

| 工具 | 业务用途 |
| --- | --- |
| `search_products` | 搜索商品，返回名称、价格、库存、评分和简介 |
| `get_product` | 查询商品详情 |
| `query_orders` | 查询指定用户订单列表 |
| `get_order` | 查询订单详情 |
| `query_logistics` | 查询订单物流轨迹 |

插件还注入商城客服系统提示词，约束模型：

- 只推荐商城工具返回的商品；
- 商品、订单、物流问题必须先调用业务工具；
- 不虚构价格、库存、订单和物流事件；
- 使用商城上下文中的当前用户 ID；
- 对外只展示客服结论，不暴露内部工具名和 JSON 字段。

### 9.2 插件配置

| 配置项 | 示例 | 用途 |
| --- | --- | --- |
| `mall.base-url` | `http://127.0.0.1:18080` | 商城服务地址 |
| `mall.service-token` | `mall-internal-demo-token` | 内部服务调用 Token |

商城服务独立运行在 `18080`，Harness 运行在 `8090`。插件通过配置访问商城服务，而不是把商城域名或环境信息写死。

### 9.3 推荐接入步骤

```bash
cd 2d-weekend-mall
mvn clean package -DskipTests
mvn -pl mall-app spring-boot:run

cd dsh-java
java -jar dsh-java-app/target/dsh-java-app-1.0.0-SNAPSHOT.jar
```

然后通过控制台或 API 安装：

```bash
./scripts/install-plugin.sh \
  --jar /path/to/mall-agent-plugin-1.0.0-SNAPSHOT.jar
```

安装后配置商城地址，再重启或重新运行插件，使插件重新执行 `configure()` 并读取最新配置。

## 10. 数据库与模型配置说明

### 10.1 MySQL 初始化

`docs/dev-ops/mysql/sql/dsh_java.sql` 继续作为完整初始化脚本。README 中补充了导入说明：

```bash
mysql -u root -p < docs/dev-ops/mysql/sql/dsh_java.sql
```

应用启动时仍会执行幂等的 `schema.sql`，其中 `CREATE TABLE IF NOT EXISTS` 不会清空已有数据。适合使用仓库 dump 获取一致表结构，同时由 Spring 补齐缺失表。

### 10.2 模型配置优先级

README 补充了模型配置说明，推荐优先在数据库中保存模型信息：

```text
数据库启用配置
  → 环境变量 / harness.yml 兜底
  → 代码默认值
```

这样可以避免只改启动参数，但数据库里仍保留旧模型或旧 API Key 导致运行行为不一致。

## 11. 运行可靠性增强

默认 MySQL Profile 增加连接池治理配置：

| 配置 | 值 | 目的 |
| --- | --- | --- |
| `initialization-fail-timeout` | `0` | 数据库暂不可用时允许应用先启动，避免启动阶段无限阻塞 |
| `connection-timeout` | `3000ms` | 获取连接快速失败 |
| `validation-timeout` | `2000ms` | 校验连接快速失败 |
| `minimum-idle` | `1` | 降低空闲连接资源 |
| `maximum-pool-size` | `10` | 控制并发连接上限 |
| `keepalive-time` | `30000ms` | 维持连接健康 |
| `max-lifetime` | `600000ms` | 限制连接生命周期 |
| `leak-detection-threshold` | `30000ms` | 暴露长时间占用连接的疑似泄漏 |

这项调整针对外部数据库网络抖动场景：请求不再长时间悬挂在获取连接阶段，可以更快失败并进入降级路径。

## 12. 验证情况

### 12.1 编译与定向测试

版本内新增或完善以下测试：

| 测试 | 覆盖点 |
| --- | --- |
| `PresetPluginLoaderTest` | 预置插件恢复、非预置插件状态对账、失败插件重试 |
| `DatabasePluginConfigStoreTest` | 默认层覆盖、覆盖层合并、空 key 归一化 |
| `AgentToolCatalogTest` | Agent 工具目录和插件状态工具注册 |

定向测试和打包验证通过。

### 12.2 本机性能与稳定性验证

验证环境：

```text
Harness：127.0.0.1:8090，standalone H2 Profile
商城：127.0.0.1:18080
插件：dsh-java-mall-plugin，运行中
模型：Deepseek-V4.1-Flash，本地 OpenAI 兼容网关
```

| 场景 | 口径 | 结果 |
| --- | --- | --- |
| 模型设置查询 | 100 请求 / 20 并发 | 成功 100，约 1669 RPS，p50 8ms，p95 23.3ms |
| 商城商品查询 | 100 请求 / 20 并发 | 成功 100，约 2273 RPS，p50 6.1ms，p95 11.3ms |
| Agent 状态查询 | 100 请求 / 20 并发 | 成功 100，约 2575 RPS，p50 5.4ms，p95 9ms |
| SSE 简单链路 | 15 请求 / 3 波次 / 每波 5 并发 | 成功 15，TTFB p50 9.3ms，总耗时 p50 1.988s |
| 客服插件真实链路 | 3 并发 / 多轮工具调用 | 成功 3，TTFB 约 11-12ms，总耗时 14.5s-39.4s |

真实客服链路的耗时主要由 LLM 多轮推理和多次工具调用决定。接口和插件工具执行本身保持稳定，基础设施侧没有出现数据库阻塞、连接失败或请求错误。

## 13. 接口速查

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/harness/plugins/analyze-jar` | 上传并分析 JAR 插件 |
| `POST` | `/api/harness/plugins/analyze-maven` | 分析 Maven 依赖对应的插件产物 |
| `POST` | `/api/harness/plugins/install` | 安装插件 |
| `POST` | `/api/harness/plugins/activate` | 激活插件 |
| `POST` | `/api/harness/plugins/run` | 运行插件 |
| `POST` | `/api/harness/plugins/{pluginId}/disable` | 禁用插件 |
| `POST` | `/api/harness/plugins/{pluginId}/enable` | 启用插件 |
| `POST` | `/api/harness/plugins/{pluginId}/uninstall` | 卸载插件 |
| `GET` | `/api/harness/plugins/{pluginId}/config` | 读取插件配置 |
| `POST` | `/api/harness/plugins/{pluginId}/config` | 保存插件配置 |
| `GET` | `/api/harness/plugins/{pluginId}/status` | 查询插件运行状态 |

Agent 工具命名保持：

```text
plugin__<pluginId>__<toolName>
```

## 14. 兼容与升级

### 14.1 兼容性

- Java 插件继续只依赖 `dsh-java-types`，插件作者不感知 Spring 或宿主内部实现；
- 旧的 `tools()` 返回方式继续可用；
- `configure(PluginContext)` 中的 `super.configure(context)` 仍会注册 `tools()`；
- 预置插件配置格式没有破坏性变化；
- 插件工具命名规则不变。

### 14.2 升级注意

1. 执行新版 `schema.sql` 或导入新的 MySQL dump，确保 `harness_plugin_config` 表存在；
2. 已安装插件建议在升级后重新运行一次，触发状态对账；
3. 如果插件依赖外部服务，保存 `base-url`、Token 等配置后再重启插件；
4. 生产环境不要把插件开发用 Token、测试商城地址长期保留在默认配置中。

## 15. 已知限制

| 事项 | 说明 |
| --- | --- |
| Maven 分析只读本地仓库 | 不会自动联网解析远程依赖 |
| 控制台配置写入 `DEFAULT` 层 | `OVERRIDE` 层能力已预留，控制台尚未提供完整编辑入口 |
| 插件运行状态与登记状态可能瞬时偏差 | 启动会自动对账，但运行中异常仍需通过 `plugin_status` 或接口确认 |
| 客服链路延迟受模型影响 | 多轮推理和工具调用次数直接决定总耗时，后续应继续收敛调用轮次 |

## 16. 提交记录

| 提交 | 说明 |
| --- | --- |
| `89923f1` | 插件机制：产物分析、配置持久化、接口与控制台 |
| `5d4f91b` | 插件机制：新增商城客服插件 JAR |
| `d024d92` | 新增 Java 插件开发 Skill 和一键安装脚本 |
| `5ec4beb` | 完善 MySQL 初始化脚本 |
| `2f4ecf7` | 补充商城插件、模型配置和 MySQL 使用文档 |
| `9e0baf7` | 插件状态对账、`plugin_status` 工具与连接池可靠性 |

## 17. 结论

v0.1.4 完成了插件机制从开发期能力到可运维能力的推进。插件现在具备统一的分析、安装、运行、配置、状态查询和重启恢复链路；商城客服插件验证了真实业务工具接入；文档、Skill、脚本和控制台共同降低了插件开发成本。

后续优化建议：

1. 给插件配置补充环境级和租户级覆盖能力；
2. 将插件状态对账从启动阶段扩展为周期性健康检查；
3. 为插件工具调用增加独立的指标、慢调用追踪和失败率统计；
4. 继续优化商城客服场景的提示词与工具路由，减少模型多轮试错。
