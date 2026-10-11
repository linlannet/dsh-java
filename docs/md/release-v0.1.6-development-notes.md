# DeepSeek Harness Java v0.1.6 开发说明

> v0.1.6 的主线是模型配置闭环与服务启动可靠性：模型渠道从启动参数扩展为数据库中的多渠道配置，控制台可选择、保存、发现并切换渠道；运行链路按渠道解析协议适配器；插件激活时同步校验运行态；远程插件 Token 鉴权在本版回退，镜像发布链路升级到 `0.1.6.4`。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.6` |
| 发布标记 | `tag-v0.1.6.4` |
| 变更范围 | `v0.1.5...v0.1.6` |
| 变更规模 | 143 个文件变更，新增约 3025 行，删除约 2321 行 |
| 主线 | 模型渠道配置、协议路由、运行链路贯通、插件启动可靠性、部署发布 |
| 技术栈 | Java 17、Spring Boot 3.5、DDD 六边形架构 |
| 交付形态 | 单实例 Spring Boot 应用 + 原生 JS Web 控制台 + Java Native 插件 |
| 交付日期 | 2026-09-12 |

## 2. 版本亮点

1. **多模型渠道**：`harness_model_setting` 以 `channel_code` 为主键，支持多个渠道、独立启停状态和全局唯一 `active` 渠道。
2. **多协议接入**：模型设置支持 OpenAI 兼容、Anthropic Messages 与 Ollama 协议，运行时按协议路由适配器。
3. **配置可发现**：控制台可从渠道模板快捷填充，也能按协议调用上游模型列表接口同步可用模型。
4. **运行链路贯通**：对话、任务与子代理统一使用 `channelCode` 解析 `AgentOptions`，避免模型名和渠道语义混用。
5. **启动状态可信**：插件激活后立即运行插件；启动失败会把插件状态落库为失败，不再把“已注册”误报为可用。
6. **迁移兜底**：启动器幂等补齐模型配置列、会话渠道列和事件负载容量，并把旧 `profile_code` 数据迁移为独立渠道。
7. **发布链路升级**：Docker 构建支持 Tag 与手动版本号，双仓库推送，部署脚本固定 `0.1.6.4` 镜像。

## 3. 架构变化

v0.1.6 不改变六边形架构依赖方向。变更按既有链路分布：

- **Trigger 层**暴露模型设置命令/查询接口、渠道模板接口和模型发现接口；
- **Case 层**维护模型设置 Pipeline、上游模型发现 Pipeline、插件激活后启动校验；
- **Domain 层**沉淀 `ModelSettingService`、`AgentModelSettingResolver` 和模型渠道路由规则；
- **Infrastructure 层**提供模型设置仓储、模型提供方端口和协议适配器工厂；
- **App 层**执行 Schema 迁移、默认模型种子注入和 Web 控制台交互。

```text
Web Console（模型渠道选择 / 保存 / 同步）
        ↓
Trigger：/api/harness/settings/models + /api/harness/channels/presets
        ↓
Case：ModelSetting Pipeline / ModelDiscovery Pipeline
        ↓
Domain：ModelSettingService / AgentModelSettingResolver / ModelRoutingService
        ↓
Infrastructure：ModelSettingRepository / ModelProviderPort / LlmAdapterFactory
```

## 4. 模型渠道配置

### 4.1 数据模型

一条模型渠道包含以下关键字段：

| 字段 | 说明 |
| --- | --- |
| `channel_code` | 渠道内部标识，主键 |
| `display_name` | 控制台展示名称 |
| `provider_code` | 渠道提供方编码 |
| `model_code` | 实际请求的模型编码 |
| `base_url` | 上游服务地址 |
| `api_key_ref` | 上游 API Key |
| `protocol` | `openai` / `anthropic` / `ollama` |
| `enabled` | 是否可启用 |
| `active` | 当前生效渠道，全局唯一 |

### 4.2 生命周期规则

- 保存第一条启用渠道时自动置为生效渠道；
- 追加其他渠道不会替换当前生效渠道；
- 显式激活只允许启用中的渠道；
- 停用或删除当前生效渠道时，自动切换到其他启用渠道；
- 唯一渠道重新启用时恢复为生效渠道。

### 4.3 模板与模型发现

渠道模板是控制台的快捷填充源，运行时权威配置仍以数据库为准。模板提供协议、默认地址、鉴权方式和推荐模型。

| 协议 | 模型发现 | 说明 |
| --- | --- | --- |
| OpenAI 兼容 | `GET {base}/models` | 适配大多数厂商和网关 |
| Anthropic | `GET {base}/models` | 请求携带 `x-api-key` 与版本头 |
| Ollama | `GET {base}/api/tags` | 模型列表使用原生端点 |

上游发现失败只影响当次同步，不阻塞手动填写模型编码和保存渠道。

## 5. 运行链路

### 5.1 对话链路

`AgentResolveNode` 把请求中的 `channelCode` 交给 `AgentModelSettingResolver`。解析顺序为：

1. 请求指定渠道且启用；
2. 数据库当前激活渠道且启用；
3. `application.yml` 或环境变量兜底配置。

解析结果写入 `AgentOptions`，包含渠道、Provider、模型、BaseUrl、API Key 和协议。已有 Agent 收到新消息时也会调用 `updateOptions`，因此下一条请求可以切换渠道。

### 5.2 任务链路

任务提交链路同样按 `channelCode` 调用 `ModelRoutingService`，并把 `resolvedChannelCode` 写入任务响应。这样调用方不仅知道任务状态，也知道任务实际使用的模型渠道。

### 5.3 子代理链路

`AgentToolCatalog` 构造工具注册表时，把父 Agent 的 `AgentOptions` 以引用方式交给子代理工具。子代理启动前动态读取父 Agent 最新选项，因此父 Agent 更新模型渠道后，后续子代理会继承该配置。

## 6. 持久化迁移

`SchemaMigrationRunner` 在启动阶段执行幂等补齐：

- 为旧模型配置表补齐 `protocol`、`active`、`channel_code`、`display_name`；
- 为会话表补齐 `channel_code`；
- 将旧 `profile_code` 记录迁移为 `channel-<uuid>` 独立渠道，并迁移旧主键；
- 没有稳定激活渠道时，选择第一条启用渠道；
- 空展示名回填为 `#<channel_code>`；
- 将会话事件负载列扩展为 `LONGTEXT` / `CLOB`。

`ModelSyncRunner` 只在 `default` 渠道不存在时写入默认模型种子。已有数据库配置优先于启动配置，避免重启覆盖控制台设置。

## 7. 插件启动可靠性

`ActivateHarnessPluginCaseImpl` 的流程由“执行激活 Pipeline 即返回”调整为：

1. 执行插件激活 Pipeline；
2. 立即调用插件运行用例；
3. 运行结果不是 `running` 时抛出异常；
4. 捕获运行异常后刷新插件状态为失败；
5. 只有启动成功才返回激活成功。

控制台插件安装页保留 JAR 与 Maven 两种识别方式；识别到有效候选后可一键安装并启用。安装请求仍使用 JSON 转义后的插件元数据。

## 8. 插件命令安全边界

v0.1.5 引入的 `PluginApiAuthFilter` 和 `X-Plugin-Api-Token` 在 v0.1.6.4 中移除，控制台也不再维护插件 API Token 输入框。升级后远程调用插件命令接口不会再被过滤器拦截。

这是开发期可用性优先的调整，但也放大了公网暴露面。若服务必须暴露到公网或不可信内网，应在反向代理、网关或防火墙层限制插件命令接口，或恢复应用层鉴权后再部署。

## 9. 发布与部署

- Maven POM 版本为 `0.1.6`；
- Docker 与标准部署脚本使用 `0.1.6.4` 镜像；
- Dockerfile 使用 standalone profile，默认内嵌 H2，便于单容器运行；
- GitHub Actions 支持 `tag-v*` 触发和手动输入版本号；
- 构建产物同时推送 Docker Hub 与阿里云 ACR；
- 标准部署脚本固定 Harness `0.1.6.4` 与商城 `0.1.1`。

## 10. 验证情况

### 10.1 定向测试

| 测试 | 覆盖点 |
| --- | --- |
| `ModelSettingServiceTest#firstEnabledChannelBecomesActive` | 第一条启用渠道自动生效 |
| `ModelSettingServiceTest#addingAnotherChannelDoesNotReplaceActiveChannel` | 新增渠道不抢占当前生效渠道 |
| `ModelSettingServiceTest#activatingChannelMovesGlobalActiveMarker` | 显式激活迁移全局生效标记 |
| `ModelSettingServiceTest#disabledChannelCannotBeActivated` | 停用渠道不能激活 |
| `ModelSettingServiceTest#disablingActiveChannelSelectsAnotherEnabledChannel` | 停用生效渠道后自动切换 |
| `ModelSettingServiceTest#deletingActiveChannelSelectsAnotherEnabledChannel` | 删除生效渠道后自动切换 |
| `AgentModelSettingResolverTest#usesActiveChannelWhenRequestHasNoChannel` | 空渠道使用当前激活配置 |
| `AgentModelSettingResolverTest#usesRequestedChannelWhenProvided` | 请求渠道优先 |
| `AgentModelSettingResolverTest#requestedChannelFallsBackWhenMissing` | 无效请求渠道回退兜底配置 |

### 10.2 回归验证

- 控制台模型设置：保存、编辑、启停、激活、删除；
- 控制台渠道选择：下拉切换、`/channel` 命令、请求携带 `channelCode`；
- 模型发现：OpenAI 兼容、Anthropic 与 Ollama 端点解析；
- 插件安装：JAR 识别、Maven 依赖识别、安装后启用；
- 容器镜像：standalone profile 启动、持久化卷挂载、标准脚本部署。

## 11. 接口与配置速查

### 11.1 模型设置接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/harness/settings/models` | 查询全部模型渠道 |
| `POST /api/harness/settings/models` | 保存或更新模型渠道 |
| `DELETE /api/harness/settings/models/{channelCode}` | 删除模型渠道 |
| `POST /api/harness/settings/models/active` | 切换当前生效渠道 |
| `POST /api/harness/settings/models/discover` | 按协议同步上游模型列表 |
| `GET /api/harness/channels/presets` | 查询渠道模板 |
| `GET /api/harness/runtime/models` | 查询当前可用模型渠道 |

### 11.2 兼容性变化

- 请求和响应中的模型标识从 `model/profile` 语义收敛为 `channelCode`；
- `SubmitTaskResponseDTO` 新增 `resolvedChannelCode`；
- `harness_model_setting` 的主键从旧 profile 结构迁移到 `channel_code`；
- 插件命令接口不再要求 `X-Plugin-Api-Token`。

## 12. 升级注意

1. 从 v0.1.5 升级前请备份 `harness_model_setting` 和 `harness_session`；
2. 首次启动 `SchemaMigrationRunner` 会迁移旧 profile 数据，建议先在开发或预发环境验证；
3. 控制台或脚本若仍携带 `X-Plugin-Api-Token`，服务端会忽略该请求头；
4. 公网或不可信内网部署必须自行限制插件命令接口；
5. 生产环境建议改为外部 MySQL，并定期备份模型配置与会话事件。

## 13. 已知限制

| 事项 | 说明 |
| --- | --- |
| 插件命令接口无应用层鉴权 | 需要依赖反向代理、网关或网络隔离 |
| API Key 明文保存在应用数据库 | 尚未实现独立密钥管理或加密存储 |
| 渠道解析依赖启动时迁移 | 迁移异常只记录日志，不会阻断启动 |
| 子代理继承父渠道 | 暂不支持子代理单独覆盖模型渠道 |

## 14. 提交记录

| 提交 | 说明 |
| --- | --- |
| `6c6bec0` / `8620fd0` | 服务处理 |
| `27f2204` | 模型配置优化 |
| `7387328` / `73fdbd6` | 模型渠道解析与控制台体验迭代 |
| `38866d0` | 插件安装简化、移除插件 API Token 过滤器 |
| `63ff0ef` / `f109c2e` / `49a2e8d` | 标准部署、站点与版本文档更新 |

其余 `ba55e18`、`99f5edc`、`695a18a`、`5e647dd`、`e117053` 等提交主要处理版本号、镜像标签、截图与发布物料。

## 15. 结论

v0.1.6 把模型配置从固定启动参数推进为可运营的渠道资产：配置可以持久化、发现、切换和随请求路由。任务与子代理复用同一套渠道语义，插件激活也开始关心真实运行态。发布侧统一到 `0.1.6.4` 镜像，降低了部署入口不一致的问题。

下一版建议优先处理两件事：为插件命令接口恢复最小安全边界，为模型 API Key 引入加密或独立密钥管理；同时把 Schema 迁移失败从“记录后继续启动”升级为明确的启动前检查或受控修复流程。
