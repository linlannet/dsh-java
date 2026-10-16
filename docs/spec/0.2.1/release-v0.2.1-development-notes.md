# DeepSeek Harness Java v0.2.1 开发说明

> v0.2.1 的主线是**运行期可靠性与可观测性增强**：把 LLM 调用的错误可重试性做成策略化能力，补齐上下文注入、压缩与 Slash 命令，引入护栏（重复工具提醒、按工具超时）与 Codex Hook 桥接；同时上线会话检查点、全文检索、OpenTelemetry 遥测、会话投影（统计/标题/大纲）、MCP Resource 发现、网页搜索、Typert 与 PTC 沙箱、LSP stdio、Profile Bundle 等能力；工程侧完成 Maven 中央仓库发布形态治理与仓库元数据对齐。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.2.1` |
| 状态 | 功能开发完成；POM 版本已统一为 `0.2.1`，镜像 tag 与部署脚本待发布时同步 |
| 基线 | v0.2.0（核心底座对齐 Phase 1 全部 36 个任务） |
| 主线 | LLM 重试策略、上下文与压缩、护栏与 Hook、检查点、全文检索、遥测、会话投影、MCP Resource、Typert/PTC、发布治理 |
| 架构 | 继续保持 DDD 六边形架构，不改变依赖方向 |
| 新增类 | 68 个（domain 56、infrastructure 12） |
| 变更类 | 9 个（domain 4、infrastructure 2、pom/Dockerfile/README/脚本等） |
| 新增测试 | 45 个（domain 43、infrastructure 2） |
| 全量测试 | domain 564 + infrastructure 53 = 617 通过，0 失败，0 回归 |
| 新增依赖 | Lucene 9.11.1（会话全文检索） |

## 2. 版本亮点

1. **LLM 重试策略化**：`LlmRetryPolicy` + `RetryableErrorClassifier` 把「是否重试」从次数判断升级为错误码分类判断，429/5xx 可重试，AUTH/INVALID_REQUEST 等不重试，并可指定兜底 Provider。
2. **上下文与压缩体系**：`AgentsMdLoader`、`FileReferenceExpander`、`SessionSnapshotReference`、`TimeContextInjector`、`TmuxContextInjector` 组成上下文注入链；压缩侧新增 `CompactCommand`、`ToolResultPruner`、`ImageOffloader`、`SlashCommandRegistry`。
3. **护栏与 Hook**：`DuplicateToolGuard`（重复工具调用提醒）、`PerToolDeadlinePolicy`（按工具名差异化超时）、`CodexHookBridge`（Codex hook 协议收发与转换）。
4. **会话可靠性**：`CheckpointStrategy` + `CheckpointSnapshot` 在模型请求前、工具副作用前落检查点，`V20261011__checkpoint.sql` 提供持久化表。
5. **会话可检索**：`SessionSearchProvider` / `SessionFullTextIndex` 定义领域契约，`LuceneSessionSearchIndex` 提供本地全文索引与高亮定位。
6. **可观测性**：`OtelService` + `SessionTelemetryExporter` + `OtlpBuffer` 构成 OTLP 导出链路，`OtlpHttpExporter` 负责 HTTP 上报。
7. **会话投影补齐**：`SessionStatistics`、`SessionTitleGenerator`、`TurnOutline` 三个新增投影，复用 v0.1.9 的 `ProjectionProvider` 契约。
8. **扩展能力**：MCP Resource 发现（`McpResourceDiscovery` + `StdioMcpResourcePort`）、网页搜索（`ExaSearchProvider` / `PerplexitySearchProvider` + HTTP 适配）、Typert 脚本生成与 PTC 沙箱 Node 进程、LSP stdio Provider、Profile Bundle 装配。
9. **工具增强**：`GlobTool` / `GrepTool` 文件检索、`ScheduleTool` 定时任务、`ToolPresentation` 工具呈现值对象、`AgentDefaultModelProvider` 默认模型解析。
10. **发布形态治理**：app 的 fat jar 改为 `classifier=exec` 且 `attach=false`，仅上传瘦 jar、sources、javadoc、pom；打包脚本与 Dockerfile 同步指向 `-exec.jar`；全量 pom 的 `<url>/<scm>/<developers>` 对齐 GitHub 仓库与 `main` 分支。

## 3. 架构变化

v0.2.1 不引入新的 Maven 模块，依赖方向保持不变。新增包全部落在 Domain 与其端口，适配器落在 Infrastructure：

```text
domain/
  llm/
    retry/          ← LlmRetryPolicy / LlmErrorCodes / RetryableErrorClassifier
    extension/      ← LlmRequestExtensionRegistry
  agent/
    context/        ← AgentsMdLoader / FileReferenceExpander / SessionSnapshotReference
                       TimeContextInjector / TmuxContextInjector
    service/        ← AgentDefaultModelProvider、ask/timed/TimedWaitAskUser
    service/compaction/ ← CompactCommand / ToolResultPruner / ImageOffloader
                           SlashCommand / SlashCommandRegistry
    service/subagent/   ← InProcessSubagentDriver
  guard/
    reminder/       ← DuplicateToolGuard
    timeout/        ← PerToolDeadlinePolicy
  hooks/codex/      ← CodexHookBridge
  lsp/stdio/        ← LspStdioProvider
  runtime/profile/bundle/ ← BundleDefinition / BundleAssembler
  sandbox/service/  ← SandboxPolicyInjector
  session/
    checkpoint/     ← CheckpointStrategy / CheckpointSnapshot / ICheckpointRepository
    search/         ← SessionSearchProvider / SessionFullTextIndex / port
    telemetry/      ← SessionTelemetryExporter / OtlpBuffer
    workspace/      ← Workspace 实体与仓储端口
    projection/service/ ← SessionStatistics / SessionTitleGenerator / TurnOutline
  telemetry/        ← OtelService / IOtelExporterPort
  storage/attachment/   ← ContentAddressableAttachmentStore
  tool/
    fs/             ← GlobTool / GrepTool
    mcp/resource/   ← McpResourceDiscovery / IMcpResourcePort
    model/valobj/   ← ToolPresentation
    schedule/       ← ScheduleTool
    web/search/     ← ExaSearchProvider / PerplexitySearchProvider / port
  typert/           ← ITypertService / TypertService / PtcSandboxNodeProcess
                       TypertGenerator / IPtcRuntimePort

infrastructure/
  adapter/
    llm/piai/       ← PiAiAdapter
    lsp/            ← StdioLspProvider
    mcp/            ← StdioMcpResourcePort
    otel/           ← OtlpHttpExporter
    repository/     ← JdbcCheckpointRepository / JdbcWorkspaceRepository
    search/         ← LuceneSessionSearchIndex
    storage/        ← Sha256ContentAddressableStore
    typert/         ← TsMorphTypertGenerator / SandboxedNodePtcProcess
                       InMemoryTypertRegistry / TypertProtocolLoader
    web/            ← ExaHttpSearch / PerplexityHttpSearch

app/
  resources/db/migration/ ← V20261011__checkpoint.sql、V20261011__workspace.sql
```

| 层 | 新增或调整 |
| --- | --- |
| Domain | 新增 56 个类；增强 `ReactLoopAgent`、`LlmRetryInterceptor`、`LspTool`、`ChannelProtocol` |
| Infrastructure | 新增 12 个类；增强 `LlmAdapterFactory`（新增 PiAi 协议分支）、`UpstreamModelSyncService` |
| App | 新增 2 个迁移 SQL；`spring-boot-maven-plugin` repackage 使用 `classifier=exec` + `attach=false` |
| Trigger / API / Case / Types | 本版本无变更 |
| 工程配置 | Dockerfile 指向 `*-exec.jar`；`scripts/package-*.sh` 优先取 exec jar；pom 元数据对齐 GitHub |

## 4. LLM 重试策略与错误分类

### 4.1 设计目标

把「流式调用失败是否重试」的决定权交给策略对象：错误码决定是否重试，次数决定上限，并可按 Provider 兜底。

### 4.2 核心抽象

| 类 | 说明 |
| --- | --- |
| `LlmRetryPolicy` | 最大重试次数、可重试错误码集合、兜底 Provider；默认 `maxAttempts=3` 且使用 `LlmErrorCodes.DEFAULT_RETRYABLE_CODES` |
| `LlmErrorCodes` | 错误码常量：`RATE_LIMIT`、`SERVER`、`AUTH`、`INVALID_REQUEST`、`CONTEXT_WINDOW_EXCEEDED`、`MALFORMED_RESPONSE` |
| `RetryableErrorClassifier` | 对异常分类：`429/500/502/503/504` 视为可重试；`AUTH`、`INVALID_REQUEST`、`CONTEXT_WINDOW_EXCEEDED`、`MALFORMED_RESPONSE` 不重试 |
| `LlmRetryInterceptor` | 新增 `(LlmRetryPolicy, RetryableErrorClassifier)` 构造；保留 `(int maxRetries)` 向后兼容；重试时记录 `attempt/code` 摘要 |

### 4.3 兼容性

旧构造 `LlmRetryInterceptor(int)` 退化为「次数判断」，与 v0.1.9 行为一致；传入策略后才会启用错误码分类与兜底路由。

### 4.4 测试

`LlmRetryPolicyTest`、`RetryableErrorClassifierTest`。

## 5. 上下文注入与压缩

### 5.1 上下文注入

| 类 | 说明 |
| --- | --- |
| `AgentsMdLoader` | 加载并解析 `AGENTS.md` 约定的项目说明 |
| `FileReferenceExpander` | 展开消息中的文件引用（`@...`）为可读内容 |
| `SessionSnapshotReference` | 以引用方式挂载会话快照，避免整段重复注入 |
| `TimeContextInjector` | 注入当前时间上下文，支撑相对时间类指令 |
| `TmuxContextInjector` | 注入 tmux 会话上下文，便于终端场景追问 |

### 5.2 压缩与 Slash 命令

| 类 | 说明 |
| --- | --- |
| `CompactCommand` | 压缩命令：`/compact` 触发上下文收缩 |
| `ToolResultPruner` | 裁剪历史工具结果，保留近期与关键结果 |
| `ImageOffloader` | 将历史图片载荷卸载为引用，降低上下文占用 |
| `SlashCommand` / `SlashCommandRegistry` | Slash 命令模型与注册表，按名称解析并执行 |

### 5.3 测试

`AgentsMdLoaderTest`、`FileReferenceExpanderTest`、`SessionSnapshotReferenceTest`、`TimeContextInjectorTest`、`TmuxContextInjectorTest`、`CompactCommandTest`、`ToolResultPrunerTest`、`ImageOffloaderTest`、`SlashCommandRegistryTest`。

## 6. 护栏与 Hook

| 类 | 说明 |
| --- | --- |
| `DuplicateToolGuard` | 识别重复工具调用（相同工具 + 相同参数），发出提醒而非重复执行 |
| `PerToolDeadlinePolicy` | 按工具名配置差异化超时，补充 v0.1.8 全局 `toolTimeoutMs` 的不足 |
| `SandboxPolicyInjector` | 将沙箱策略注入执行上下文，统一 FS/Shell 边界判定 |
| `CodexHookBridge` | 解析 Codex hook 协议 JSON（`exit_code`、`stdout`、`stderr`、`continue`、`stopReason`、`decision`、`reason`），转换为内部 `HookOutput` |
| `TimedWaitAskUser` | 带超时的等待式用户提问，避免阻塞无限等待 |
| `InProcessSubagentDriver` | 进程内子代理驱动，降低外部进程依赖 |

测试：`DuplicateToolGuardTest`、`PerToolDeadlinePolicyTest`、`SandboxPolicyInjectorTest`、`CodexHookBridgeTest`、`TimedWaitAskUserTest`、`InProcessSubagentDriverTest`。

## 7. 会话检查点

### 7.1 设计目标

在执行链的不可逆动作前保存可回滚状态，支持失败回放与人工介入。

### 7.2 实现

`CheckpointStrategy` 提供两个时机：

| 时机 | 常量 | 场景 |
| --- | --- | --- |
| 模型请求前 | `before-model-request` | 请求失败时可重试或回滚 |
| 工具副作用前 | `before-tool-side-effect` | 写操作前留痕 |

快照结构 `CheckpointSnapshot(sessionId, stepIndex, reason, contextSnapshotJson, createdAt)`，由 `ICheckpointRepository` 持久化，`JdbcCheckpointRepository` 提供 JDBC 实现，表结构见 `V20261011__checkpoint.sql`（按 `session_id + step_index` 建索引）。

测试：`CheckpointStrategyTest`。

## 8. 会话全文检索

| 层 | 类 | 说明 |
| --- | --- | --- |
| Domain | `SessionSearchProvider` | 委托 `ISessionSearchIndexPort` 执行搜索，返回匹配片段与高亮定位 |
| Domain | `SessionFullTextIndex` | 索引写入与查询的领域装配 |
| Infrastructure | `LuceneSessionSearchIndex` | Lucene 9.11.1 本地索引实现 |

新增依赖 `lucene-core` 等 Lucene 9.11.1 组件，根 pom 增加 `<lucene.version>9.11.1</lucene.version>`。索引落在本地，不依赖外部搜索服务。

测试：`SessionFullTextIndexTest`、`SessionSearchProviderTest`、`LuceneSessionSearchIndexTest`。

## 9. OpenTelemetry 遥测

```text
SessionEvent
  → SessionTelemetryExporter.export(event)
  → OtlpBuffer（缓冲 encode 后的 OTLP 载荷）
  → IOtelExporterPort
  → OtlpHttpExporter（HTTP 上报）
```

| 类 | 说明 |
| --- | --- |
| `OtelService` | span 生命周期管理，维护 activeSpans 并驱动导出 |
| `SessionTelemetryExporter` | 将 `SessionEvent` 编码为 OTLP 载荷并写入缓冲区 |
| `OtlpBuffer` | 带容量约束的环形/队列缓冲（避免内存无界增长） |
| `OtlpHttpExporter` | OTLP/HTTP 导出适配器 |

### 9.1 条件装配与配置

`OtlpHttpExporter` 以 Spring `@Component` 形式注册，但**默认不装配**：

| 项 | 说明 |
| --- | --- |
| `@ConditionalOnProperty(name="dsh.otel.enabled", havingValue="true")` | 仅在显式开启遥测时创建 Bean，避免未配置 OTLP 端点时上下文启动失败 |
| `dsh.otel.exporter.otlp-endpoint` | OTLP/HTTP 上报端点，默认 `http://127.0.0.1:4318/v1/traces` |
| 构造器注入 | `@Autowired` + `@Value` 注入端点；保留 `(endpoint, httpClient)` 双参构造，便于测试注入自定义 `HttpClient` |

修复背景：初版 `OtlpHttpExporter` 仅有带参构造器却被无条件注册为 Bean，Spring 回退查找默认构造器失败，`ApplicationIntegrationSmokeTest` 启动上下文时抛出 `NoSuchMethodException: net.linlan.dsh.infrastructure.adapter.otel.OtlpHttpExporter.<init>()`。补充条件装配与端点注入后，`mvn -pl dsh-java-app -am test` 全量通过（domain + infrastructure 53 + app 5）。

测试：`OtelServiceTest`、`SessionTelemetryExporterTest`、`OtlpBufferTest`。

## 10. 会话投影增强

在 v0.1.9 的 `ProjectionRegistry` / `ProjectionProvider` 契约下新增三个投影：

| 投影 | 类 | 用途 |
| --- | --- | --- |
| 用量统计 | `SessionStatistics` | 聚合模型调用次数与 Token 用量视图 |
| 会话标题 | `SessionTitleGenerator` | 依据可见用户文本生成会话标题 |
| 轮次大纲 | `TurnOutline` | 生成会话轮次提纲，便于长会话导航 |

测试：`SessionStatisticsTest`、`SessionTitleGeneratorTest`、`TurnOutlineTest`。

## 11. MCP Resource 与工具增强

| 能力 | 领域 | 适配器 |
| --- | --- | --- |
| MCP Resource 发现 | `McpResourceDiscovery` / `IMcpResourcePort` | `StdioMcpResourcePort` |
| 网页搜索 | `ExaSearchProvider` / `PerplexitySearchProvider` / `IWebSearchPort` | `ExaHttpSearch` / `PerplexityHttpSearch` |
| 文件检索 | `GlobTool` / `GrepTool` | 复用既有沙箱 FS 端口 |
| 定时任务 | `ScheduleTool` | — |
| 工具呈现 | `ToolPresentation` 值对象 | — |
| LSP stdio | `LspStdioProvider` | `StdioLspProvider` |
| 默认模型 | `AgentDefaultModelProvider` | — |

测试：`McpResourceDiscoveryTest`、`ExaSearchProviderTest`、`PerplexitySearchProviderTest`、`GlobToolTest`、`GrepToolTest`、`LspToolTest`、`LspStdioProviderTest`、`ScheduleToolTest`、`ToolPresentationTest`、`AgentDefaultModelProviderTest`。

## 12. Typert 与 PTC 沙箱

| 类 | 说明 |
| --- | --- |
| `ITypertService` / `TypertService` | 类型反射与远程调用注册表，`registerContribution` / `resolveInvocation` / `listInvocations` |
| `TypertGenerator` | 由端点定义生成调用描述符 |
| `TsMorphTypertGenerator` | 基于 ts-morph 的 TypeScript 反射生成实现 |
| `TypertProtocolLoader` | 协议文件加载 |
| `InMemoryTypertRegistry` | 进程内注册表实现 |
| `PtcSandboxNodeProcess` / `SandboxedNodePtcProcess` | 沙箱 Node 进程执行 PTC（Programmatic Tool Calling），限制可执行与脚本路径 |
| `IPtcRuntimePort` | PTC 运行时端口 |

测试：`PtcSandboxNodeProcessTest`、`TypertGeneratorTest`。

## 13. Profile Bundle、模型与渠道

| 类 | 说明 |
| --- | --- |
| `BundleDefinition` / `BundleAssembler` | Profile 资源包定义与装配，便于成组分发 `Agent Profile` |
| `ChannelProtocol` | 渠道协议枚举调整，纳入 PiAi 等新协议 |
| `LlmAdapterFactory` | 按协议选取适配器，新增 PiAi 分支 |
| `PiAiAdapter` | PiAi 协议 LLM 适配器 |
| `UpstreamModelSyncService` | 上游模型同步适配新协议端点 |

测试：`BundleDefinitionTest`、`BundleAssemblerTest`、`PiAiAdapterTest`。

## 14. 存储与工作区

| 类 / 文件 | 说明 |
| --- | --- |
| `ContentAddressableAttachmentStore` | 附件内容寻址存储服务（Domain） |
| `Sha256ContentAddressableStore` | 基于 SHA-256 的内容寻址实现（Infrastructure） |
| `V20261011__workspace.sql` | 新增 `workspace` 与 `session_workspace` 表，持久化工作区与会话多对多关系 |
| `JdbcWorkspaceRepository` | 工作区 JDBC 仓储 |
| `Workspace` 实体 | 工作区聚合实体 |

测试：`ContentAddressableAttachmentStoreTest`、`WorkspaceEntityTest`。

## 15. 发布形态与工程治理

### 15.1 Fat jar 与中央仓库发布

`dsh-java-app/pom.xml` 中 `spring-boot-maven-plugin` 的 `repackage` 增加：

```xml
<classifier>exec</classifier>
<attach>false</attach>
```

效果：

| 项 | 说明 |
| --- | --- |
| fat jar | 附加 `exec` classifier，不 install/deploy，仅留在 `target/`（约 30MB，不再占用中央仓库空间限额） |
| 主 jar | 保持瘦 jar（约 159KB），可被外部工程依赖 |
| sources / javadoc / pom | 体积可控且具备下游消费价值，继续上传 |

配套改动：`Dockerfile` 改为复制 `dsh-java-app-*-exec.jar`；`scripts/package-jar.sh`、`package-local.sh`、`package-mysql.sh` 优先选取 `-exec.jar`，找不到时回退历史命名。

### 15.2 POM 元数据对齐

| 文件 | 调整 |
| --- | --- |
| `pom.xml` | `<url>` / `<scm>` / `<developers>` 指向实际仓库 `https://github.com/linlannet/dsh-java`，补 `<developerConnection>` |
| 7 个模块 pom | 补齐 `<name>` / `<description>` / `<url>` / `<licenses>` / `<scm>` / `<developers>`，scm 路径按 `tree/main/<module>` 指向各自目录 |
| `plugins/**/pom.xml` | 插件与 archetype 模板同步对齐 GitHub 仓库路径 |

### 15.3 文档

- `CHANGELOG.md` 改为按 app / trigger / api / case / domain / infrastructure / types 七个包记录版本更新；
- `README.en.md` 头部中英混排段落改为英文，徽章锚点改指向 `#21-requirements`。

## 16. 兼容性变化

| 变化 | 兼容性 |
| --- | --- |
| `LlmRetryInterceptor` 新增策略构造 | 旧 `(int)` 构造保留，行为不变 |
| `ChannelProtocol` 枚举调整 | 新增协议分支，既有协议取值不变 |
| fat jar 增加 `exec` classifier | 打包/部署脚本与 Dockerfile 已同步；手工部署需改取 `*-exec.jar` |
| `OtlpHttpExporter` 改为条件装配 | 默认不创建该 Bean；需要 OTLP 上报时设置 `dsh.otel.enabled=true`，不影响既有启动流程 |
| 新增 `checkpoint` / `workspace` 表 | 由迁移脚本幂等建表，不影响既有数据 |
| pom 新增 `<name>` / `<scm>` 等 | 不影响构建，仅满足发布要求 |
| 其余新增类 | 均为新增包，不影响既有 API |

## 17. 升级注意

1. 部署脚本若直接取 `dsh-java-app-*.jar`，需改为 `dsh-java-app-*-exec.jar`；
2. 使用 LLM 重试能力时，建议显式注入 `LlmRetryPolicy` 与 `RetryableErrorClassifier`，否则退化为次数重试；
3. 升级前执行 `V20261011__checkpoint.sql` 与 `V20261011__workspace.sql`，或依赖 `SchemaMigrationRunner` 幂等补表；
4. 启用 Lucene 全文检索需确认本地索引目录的写权限与磁盘占用；
5. Maven POM 版本已统一为 `0.2.1`，发布时同步 Docker 镜像 tag 与部署脚本；若需启用 OTLP 遥测，显式配置 `dsh.otel.enabled=true` 与 `dsh.otel.exporter.otlp-endpoint`。

## 18. 已知限制

| 事项 | 说明 |
| --- | --- |
| 检查点仅覆盖两个时机 | 目前只在模型请求前、工具副作用前落点，尚不支持任意事件位点 |
| Lucene 索引单机本地 | 多实例部署时索引不共享，需外部检索时另接适配 |
| Typert / PTC 为初始版本 | ts-morph 生成与 Node 沙箱进程的功能完整度、安全性待进一步验证 |
| MCP Resource 仅 stdio 适配 | HTTP/SSE MCP 的 Resource 发现尚未实现 |
| 重复工具护栏为提醒式 | `DuplicateToolGuard` 只发出提醒，不做强制拦截 |
| 遥测默认关闭且无鉴权与采样控制 | 仅 `dsh.otel.enabled=true` 时装配 `OtlpHttpExporter`；OTLP 导出未做采样、鉴权与批量重试策略 |

## 19. 后续建议

1. 将检查点扩展到更多事件位点，并提供回滚执行入口；
2. 为 Lucene 索引增加增量写入、容量上限与清理策略；
3. 为 PerToolDeadlinePolicy 提供配置文件化注入；
4. 补齐 Typert/PTC 的端到端验证与安全基线；
5. 为 OTLP 导出增加采样、批量重试与失败降级；
6. 发布前统一 POM 版本号、Docker 镜像、部署脚本与 release tag。

## 20. 结论

v0.2.1 把 Agent 运行期从「能跑通」推进到「跑得稳、看得见、可找回」：LLM 失败具备策略化重试与兜底，上下文可注入可压缩并支持 Slash 命令，护栏与 Hook 补齐执行边界，会话检查点、全文检索、OTLP 遥测和三类新投影让运行过程可观测可回追；MCP Resource、网页搜索、Typert/PTC、LSP、Profile Bundle 扩展了能力面；工程侧完成 fat jar 与发布元数据治理，为中央仓库发布扫清障碍。全量 617 测试通过，0 回归。
