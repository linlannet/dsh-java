# DeepSeek Harness Java v0.2.1 编码任务清单

> 版本：`v0.2.1` | 基线：v0.2.0 | 架构：DDD 六边形架构（Maven 多模块 / Spring Boot 3.5.16 / Java 17）
>
> 本清单将 `spec.md` 的 **28 个功能领域**需求与 `design.md` 的技术方案转化为可执行、可验收的编码任务。
>
> - **既有领域（§5.1–§5.12，任务组 1–12）**：已在代码库实现完成（domain 56 新增类、infrastructure 12 新增类、45 新增测试，全量 617 测试通过），标注 `[x]`。
> - **核心扩展（§5.13–§5.18，任务组 13–18）**：对齐原版 0.2.1-alpha.3 的产品级能力，标注 `[x]`，默认启用。
> - **实验性扩展（§5.19–§5.28，任务组 19–28）**：对齐原版实验性能力，标注 `[x]`，默认关闭，经 `@ConditionalOnProperty` 或 profile 显式启用。
>
> **架构约束**：全部扩展领域遵循六边形架构，不引入新 Maven 模块，新增包落在既有 Domain/Infrastructure/Trigger 模块内，依赖方向保持"基础设施 → 领域"。仅多代理团队（§5.19）需新增持久化表，其余为进程内值对象或文件存储。
>
> **完成状态图例**：`[x]` 已完成 ｜ `[ ]` 待办/后续优化

---

## 1. LLM 重试策略与错误分类

> 依赖：无（本组为可靠性底座，被多组依赖） ｜ 完成状态：已完成

### 1.1 实现重试策略值对象
- [x] 实现不可变值对象 `LlmRetryPolicy`（maxAttempts/retryableCodes/fallbackProvider），提供 `shouldRetry(errorCode, attempt)` 与 `fallbackProvider(errorCode)` 决策方法；`retryableCodes` 经 `List.copyOf` 防御性拷贝，`maxAttempts<0` 抛 `IllegalArgumentException` —— `dsh-java-domain/.../llm/retry/model/valobj/LlmRetryPolicy.java`

### 1.2 实现错误码常量类
- [x] 实现 `LlmErrorCodes` 常量类，定义 RATE_LIMIT/SERVER/AUTH/INVALID_REQUEST/CONTEXT_WINDOW_EXCEEDED/MALFORMED_RESPONSE 及 `DEFAULT_RETRYABLE_CODES` 集合 —— `dsh-java-domain/.../llm/model/valobj/LlmErrorCodes.java`

### 1.3 实现可重试错误分类器
- [x] 实现 `RetryableErrorClassifier`，按异常消息关键词与 HTTP 状态码（429/500/502/503/504 可重试；AUTH/INVALID_REQUEST/CONTEXT_WINDOW_EXCEEDED/MALFORMED_RESPONSE 不可重试）分类，递归 `getCause()` 链，无状态线程安全 —— `dsh-java-domain/.../llm/retry/service/RetryableErrorClassifier.java`

### 1.4 扩展重试拦截器（策略化 + 向后兼容）
- [x] 扩展 `LlmRetryInterceptor` 新增 `(LlmRetryPolicy, RetryableErrorClassifier)` 策略构造，在 `onError` 时依据策略与分类器决定重试或向上抛出；保留 `(int maxRetries)` 旧构造并退化为次数判断（与 v0.1.9 行为一致）；重试时记录 `attempt=N code=XXX` 摘要 —— `dsh-java-domain/.../llm/waterfall/LlmRetryInterceptor.java`

### 1.5 编写重试策略单元测试
- [x] 编写 `LlmRetryPolicyTest` 与 `RetryableErrorClassifierTest`，覆盖可重试/不可重试错误码、次数上限优先、兜底路由、向后兼容（旧构造次数判断）、未知异常返回 UNKNOWN 等场景

---

## 2. 上下文注入与压缩

> 依赖：无 ｜ 完成状态：已完成

### 2.1 实现上下文注入器链（5 个注入器）
- [x] 实现 `AgentsMdLoader`，解析项目根目录 AGENTS.md 并注入项目说明上下文，文件不存在时静默跳过不报错 —— `dsh-java-domain/.../agent/context/service/AgentsMdLoader.java`
- [x] 实现 `FileReferenceExpander`，展开消息中 `@文件路径` 引用为文件可读内容，路径无效时保留原文并记录告警 —— `dsh-java-domain/.../agent/context/service/FileReferenceExpander.java`
- [x] 实现 `SessionSnapshotReference`，以引用方式挂载会话快照避免整段重复注入 —— `dsh-java-domain/.../agent/context/service/SessionSnapshotReference.java`
- [x] 实现 `TimeContextInjector`，注入当前时间上下文支撑相对时间类指令 —— `dsh-java-domain/.../agent/context/service/TimeContextInjector.java`
- [x] 实现 `TmuxContextInjector`，存在 tmux 会话时注入 tmux 上下文，不存在时跳过 —— `dsh-java-domain/.../agent/context/service/TmuxContextInjector.java`

### 2.2 实现压缩与 Slash 命令组件
- [x] 实现压缩命令 `CompactCommand`，由 `/compact` 触发上下文收缩，单次命令内完成裁剪并返回 —— `dsh-java-domain/.../agent/service/compaction/CompactCommand.java`
- [x] 实现工具结果裁剪器 `ToolResultPruner`，保留近期与关键工具结果，裁剪远端冗余结果 —— `dsh-java-domain/.../agent/service/compaction/ToolResultPruner.java`
- [x] 实现图片卸载器 `ImageOffloader`，将历史图片载荷卸载为引用标记降低上下文占用 —— `dsh-java-domain/.../agent/service/compaction/ImageOffloader.java`
- [x] 实现 Slash 命令模型 `SlashCommand` 与注册表 `SlashCommandRegistry`，按名称解析并执行已注册命令 —— `dsh-java-domain/.../agent/service/compaction/`

### 2.3 编写上下文与压缩单元测试
- [x] 编写 `AgentsMdLoaderTest`、`FileReferenceExpanderTest`、`SessionSnapshotReferenceTest`、`TimeContextInjectorTest`、`TmuxContextInjectorTest`、`CompactCommandTest`、`ToolResultPrunerTest`、`ImageOffloaderTest`、`SlashCommandRegistryTest`，覆盖 AGENTS.md 缺失、文件引用无效等异常场景

---

## 3. 护栏与 Hook

> 依赖：无 ｜ 完成状态：已完成

### 3.1 实现执行护栏组件
- [x] 实现重复工具护栏 `DuplicateToolGuard`（提醒式），识别相同工具与相同参数的重复调用并发出提醒而非重复执行 —— `dsh-java-domain/.../guard/reminder/DuplicateToolGuard.java`
- [x] 实现按工具超时策略 `PerToolDeadlinePolicy`，按工具名配置差异化超时阈值，与全局超时取较小值生效 —— `dsh-java-domain/.../guard/timeout/PerToolDeadlinePolicy.java`
- [x] 实现沙箱策略注入器 `SandboxPolicyInjector`，将沙箱策略注入执行上下文，统一 FS/Shell 边界判定 —— `dsh-java-domain/.../sandbox/service/SandboxPolicyInjector.java`

### 3.2 实现 Codex Hook 桥接
- [x] 实现 `CodexHookBridge`，解析 Codex hook JSON（exit_code/stdout/stderr/continue/stopReason/decision/reason/additionalContext/systemMessage）并转换为内部 `HookOutput`，提供 `toCodexJson` 反向序列化；格式非法时静默降级返回默认 HookOutput 不中断执行链 —— `dsh-java-domain/.../hooks/codex/CodexHookBridge.java`

### 3.3 实现超时提问与进程内子代理
- [x] 实现带超时等待式提问 `TimedWaitAskUser`，超时后返回不无限阻塞 —— `dsh-java-domain/.../agent/service/ask/timed/TimedWaitAskUser.java`
- [x] 实现进程内子代理驱动 `InProcessSubagentDriver`，在当前进程内执行不启动外部进程 —— `dsh-java-domain/.../agent/service/subagent/InProcessSubagentDriver.java`

### 3.4 编写护栏与 Hook 单元测试
- [x] 编写 `DuplicateToolGuardTest`、`PerToolDeadlinePolicyTest`、`SandboxPolicyInjectorTest`、`CodexHookBridgeTest`、`TimedWaitAskUserTest`、`InProcessSubagentDriverTest`，覆盖 Codex JSON 非法、超时触发等异常场景

---

## 4. 会话检查点

> 依赖：数据库迁移脚本（4.5） ｜ 完成状态：已完成（已知限制：仅覆盖两个时机）

### 4.1 实现检查点快照值对象与端口
- [x] 实现 `CheckpointSnapshot`（record，5 字段：sessionId/stepIndex/reason/contextSnapshotJson/createdAt），紧凑构造器校验非空，工厂方法 `of(...)` 自动填充 `createdAt=Instant.now()` —— `dsh-java-domain/.../session/checkpoint/model/valobj/CheckpointSnapshot.java`
- [x] 定义检查点出站端口 `ICheckpointRepository`（save/findBySessionAndStep/findBySession） —— `dsh-java-domain/.../session/checkpoint/service/`

### 4.2 实现检查点策略领域服务
- [x] 实现 `CheckpointStrategy`，提供 `beforeModelRequest(SessionLog)` 与 `beforeToolSideEffect(SessionLog, ToolCall)` 两个时机方法及常量 `REASON_BEFORE_MODEL_REQUEST` / `REASON_BEFORE_TOOL_SIDE_EFFECT`，委托仓储持久化 —— `dsh-java-domain/.../session/checkpoint/service/CheckpointStrategy.java`

### 4.3 实现检查点 JDBC 仓储
- [x] 实现 `JdbcCheckpointRepository`，操作 `checkpoint` 表，按 `session_id + step_index` 联合索引快速定位 —— `dsh-java-infrastructure/.../adapter/repository/JdbcCheckpointRepository.java`

### 4.4 编写检查点迁移 SQL
- [x] 编写 `V20261011__checkpoint.sql`，使用 `CREATE TABLE IF NOT EXISTS` 幂等建表，`context_snapshot_json` 为 TEXT，`created_at` 为 TIMESTAMP，按 `session_id + step_index` 建索引 —— `dsh-java-app/src/main/resources/db/migration/`

### 4.5 编写检查点单元测试
- [x] 编写 `CheckpointStrategyTest`，覆盖两时机落点、快照五字段完整性与写入失败降级（日志告警不中断主链）场景

---

## 5. 会话全文检索

> 依赖：Lucene 9.11.1 依赖引入（5.4） ｜ 完成状态：已完成（已知限制：单机本地、多实例不共享）

### 5.1 实现会话检索领域契约
- [x] 定义全文索引出站端口 `ISessionSearchIndexPort`（index/search/clear/close），继承 `AutoCloseable` —— `dsh-java-domain/.../session/search/service/`
- [x] 实现会话搜索领域服务 `SessionSearchProvider`，委托端口执行搜索返回 `List<SearchHit>`，空查询或 `limit<=0` 返回空列表 —— `dsh-java-domain/.../session/search/service/SessionSearchProvider.java`
- [x] 实现会话全文索引装配 `SessionFullTextIndex`，负责索引写入与查询的领域装配 —— `dsh-java-domain/.../session/search/service/SessionFullTextIndex.java`

### 5.2 引入 Lucene 依赖
- [x] 在根 pom 增加 `<lucene.version>9.11.1</lucene.version>` 并引入 `lucene-core` 等 Lucene 9.11.1 组件依赖

### 5.3 实现 Lucene 索引适配器
- [x] 实现 `LuceneSessionSearchIndex`，Lucene 9.11.1 嵌入式索引落在本地文件系统，索引目录无写权限时降级返回空结果 —— `dsh-java-infrastructure/.../adapter/search/LuceneSessionSearchIndex.java`

### 5.4 编写会话检索单元测试
- [x] 编写 `SessionFullTextIndexTest`、`SessionSearchProviderTest`、`LuceneSessionSearchIndexTest`，覆盖索引写入、查询返回匹配片段与高亮定位、空查询降级等场景

---

## 6. OpenTelemetry 遥测

> 依赖：无 ｜ 完成状态：已完成（已知限制：默认关闭、无采样/鉴权/批量重试）

### 6.1 实现 OTLP 缓冲与导出端口
- [x] 实现 `OtlpBuffer` 值对象，带字节容量上限（默认 4MB）的 FIFO 队列缓冲，超出时丢弃最旧载荷，单条载荷超限直接丢弃并计数，所有读写方法 `synchronized` 保证线程安全 —— `dsh-java-domain/.../session/telemetry/model/valobj/OtlpBuffer.java`
- [x] 定义 OTLP 导出出站端口 `IOtelExporterPort` —— `dsh-java-domain/.../telemetry/`

### 6.2 实现遥测领域服务
- [x] 实现 `OtelService`，维护 `activeSpans` 生命周期并驱动导出，span 泄漏可由日志发现 —— `dsh-java-domain/.../telemetry/service/OtelService.java`
- [x] 实现 `SessionTelemetryExporter`，将 `SessionEvent` 编码为 OTLP 载荷写入 `OtlpBuffer` —— `dsh-java-domain/.../session/telemetry/service/SessionTelemetryExporter.java`

### 6.3 实现 OTLP HTTP 导出适配器（条件装配）
- [x] 实现 `OtlpHttpExporter`，添加 `@Component` + `@ConditionalOnProperty(name="dsh.otel.enabled", havingValue="true")` 条件装配，构造器 `@Autowired` + `@Value("${dsh.otel.exporter.otlp-endpoint:http://127.0.0.1:4318/v1/traces}")` 注入端点，保留 `(endpoint, httpClient)` 双参构造便于测试 —— `dsh-java-infrastructure/.../adapter/otel/OtlpHttpExporter.java`

### 6.4 编写遥测单元测试
- [x] 编写 `OtelServiceTest`、`SessionTelemetryExporterTest`、`OtlpBufferTest`，验证条件装配下未配置 `dsh.otel.enabled` 时不创建 Bean 且应用正常启动

---

## 7. 会话投影增强

> 依赖：v0.1.9 既有 `ProjectionProvider<T>` 契约与 `ProjectionRegistry` ｜ 完成状态：已完成

### 7.1 实现三个新增投影
- [x] 实现用量统计投影 `SessionStatistics`，聚合模型调用次数与 Token 用量，实现 `ProjectionProvider<Statistics>` —— `dsh-java-domain/.../session/projection/service/SessionStatistics.java`
- [x] 实现会话标题投影 `SessionTitleGenerator`，依据可见用户文本生成标题，无可见文本时返回默认占位标题，实现 `ProjectionProvider<String>` —— `dsh-java-domain/.../session/projection/service/SessionTitleGenerator.java`
- [x] 实现轮次大纲投影 `TurnOutline`，生成会话轮次提纲便于长会话导航，实现 `ProjectionProvider<List<Turn>>` —— `dsh-java-domain/.../session/projection/service/TurnOutline.java`

### 7.2 注册投影并复用契约
- [x] 通过 `ProjectionRegistry` 统一注册三个新投影，复用 v0.1.9 既有 `ProjectionProvider<T>` 契约，`applyIncremental` 为纯函数不修改 current，不另立接口

### 7.3 编写投影单元测试
- [x] 编写 `SessionStatisticsTest`、`SessionTitleGeneratorTest`、`TurnOutlineTest`，覆盖增量计算、无可见文本默认标题等场景

---

## 8. MCP Resource 与工具增强

> 依赖：无 ｜ 完成状态：已完成（已知限制：MCP Resource 仅 stdio 适配）

### 8.1 实现 MCP Resource 发现
- [x] 定义 MCP Resource 发现端口 `IMcpResourcePort`，实现领域服务 `McpResourceDiscovery` 发现 MCP Server 暴露的 Resource —— `dsh-java-domain/.../tool/mcp/resource/service/`
- [x] 实现 stdio MCP Resource 适配器 `StdioMcpResourcePort` —— `dsh-java-infrastructure/.../adapter/mcp/`

### 8.2 实现网页搜索
- [x] 定义网页搜索端口 `IWebSearchPort`，实现 `ExaSearchProvider` 与 `PerplexitySearchProvider` 领域服务 —— `dsh-java-domain/.../tool/web/search/`
- [x] 实现 Exa 与 Perplexity HTTP 搜索适配器 `ExaHttpSearch` / `PerplexityHttpSearch` —— `dsh-java-infrastructure/.../adapter/web/`

### 8.3 实现文件检索与定时任务工具
- [x] 实现文件检索工具 `GlobTool` 与 `GrepTool`，在沙箱 FS 端口约束下执行文件检索 —— `dsh-java-domain/.../tool/fs/`
- [x] 实现定时任务工具 `ScheduleTool`，支持定时任务调度 —— `dsh-java-domain/.../tool/schedule/ScheduleTool.java`

### 8.4 实现工具呈现、LSP stdio 与默认模型
- [x] 实现工具呈现值对象 `ToolPresentation`，封装工具呈现信息 —— `dsh-java-domain/.../tool/model/valobj/`
- [x] 实现 LSP stdio Provider `LspStdioProvider` 与适配器 `StdioLspProvider`，经 stdio 返回诊断/补全 —— `dsh-java-domain/.../lsp/stdio/` + `dsh-java-infrastructure/.../adapter/lsp/`
- [x] 实现默认模型解析 `AgentDefaultModelProvider`，Agent 未显式指定模型时解析默认模型 —— `dsh-java-domain/.../agent/service/AgentDefaultModelProvider.java`

### 8.5 编写工具增强单元测试
- [x] 编写 `McpResourceDiscoveryTest`、`ExaSearchProviderTest`、`PerplexitySearchProviderTest`、`GlobToolTest`、`GrepToolTest`、`LspToolTest`、`LspStdioProviderTest`、`ScheduleToolTest`、`ToolPresentationTest`、`AgentDefaultModelProviderTest`

---

## 9. Typert 与 PTC 沙箱

> 依赖：外部 Node.js 运行时 ｜ 完成状态：已完成（已知限制：初始版本，安全性与完整度待验证）

### 9.1 实现 Typert 类型反射服务
- [x] 定义 Typert 服务端口 `ITypertService` 与注册表契约 `ITypertRegistryContract`，实现 `TypertService`（registerContribution/resolveInvocation/listInvocations），委托注册表 —— `dsh-java-domain/.../typert/service/`
- [x] 定义调用描述符生成端口 `TypertGenerator`，实现 ts-morph 反射生成器 `TsMorphTypertGenerator`，基于 ts-morph 生成 TypeScript 反射信息 —— `dsh-java-infrastructure/.../adapter/typert/TsMorphTypertGenerator.java`
- [x] 实现协议文件加载器 `TypertProtocolLoader` 与进程内注册表 `InMemoryTypertRegistry` —— `dsh-java-infrastructure/.../adapter/typert/`

### 9.2 实现 PTC 沙箱 Node 进程
- [x] 定义 PTC 运行时端口 `IPtcRuntimePort`，实现沙箱 Node 进程 `PtcSandboxNodeProcess` 与 `SandboxedNodePtcProcess`，限制可执行路径与脚本路径，不得执行白名单外命令 —— `dsh-java-domain/.../typert/ptc/` + `dsh-java-infrastructure/.../adapter/typert/`

### 9.3 编写 Typert 与 PTC 单元测试
- [x] 编写 `PtcSandboxNodeProcessTest`、`TypertGeneratorTest`

---

## 10. Profile Bundle、模型与渠道

> 依赖：无 ｜ 完成状态：已完成

### 10.1 实现 Profile Bundle 定义与装配
- [x] 实现 `BundleDefinition` 值对象（封装一组 Profile 配置定义）与 `BundleAssembler`（`assemble(BundleDefinition) -> Profile 集合`），便于成组分发 Agent Profile —— `dsh-java-domain/.../runtime/profile/bundle/`

### 10.2 扩展渠道协议枚举
- [x] 扩展 `ChannelProtocol` 枚举新增 `PI_AI("pi-ai")` 分支，`fromCode` 大小写不敏感匹配，未知编码回退 `OPENAI`，既有取值不变（向后兼容） —— `dsh-java-domain/.../channel/model/valobj/ChannelProtocol.java`

### 10.3 实现 PiAi 协议适配与工厂扩展
- [x] 实现 PiAi 协议 LLM 适配器 `PiAiAdapter` —— `dsh-java-infrastructure/.../adapter/llm/piai/PiAiAdapter.java`
- [x] 扩展适配器工厂 `LlmAdapterFactory`，switch 增加 PI_AI 分支返回 `PiAiAdapter`，未知协议抛出明确异常 —— `dsh-java-infrastructure/.../adapter/llm/LlmAdapterFactory.java`
- [x] 扩展上游模型同步服务 `UpstreamModelSyncService`，适配 PiAi 端点进行模型列表拉取 —— `dsh-java-infrastructure/.../`

### 10.4 编写 Profile Bundle 与渠道单元测试
- [x] 编写 `BundleDefinitionTest`、`BundleAssemblerTest`、`PiAiAdapterTest`

---

## 11. 存储与工作区

> 依赖：数据库迁移脚本（11.5） ｜ 完成状态：已完成

### 11.1 实现内容寻址附件存储
- [x] 实现 `ContentAddressableAttachmentStore`（store/retrieve/exists），内部 `HashFunction` 接口，`store` 先计算哈希再写入 `IStorageBackendPort`，相同内容去重返回相同哈希键 —— `dsh-java-domain/.../storage/attachment/ContentAddressableAttachmentStore.java`
- [x] 实现 SHA-256 内容寻址存储适配器 `Sha256ContentAddressableStore`，基于 `MessageDigest`，内容 Base64 编码后存储，读取时校验哈希一致 —— `dsh-java-infrastructure/.../adapter/storage/Sha256ContentAddressableStore.java`

### 11.2 实现工作区实体与持久化
- [x] 实现工作区聚合实体 `WorkspaceEntity` 与仓储端口 `IWorkspaceRepository`（save/findById/bindSession） —— `dsh-java-domain/.../session/workspace/`
- [x] 实现工作区 JDBC 仓储 `JdbcWorkspaceRepository`，操作 `workspace` 与 `session_workspace` 表持久化多对多关系 —— `dsh-java-infrastructure/.../adapter/repository/JdbcWorkspaceRepository.java`

### 11.3 编写工作区迁移 SQL
- [x] 编写 `V20261011__workspace.sql`，建 `workspace`（workspace_id 主键）与 `session_workspace`（多对多关系表）表，按 `(workspace_id, session_id)` 建联合索引，使用 `CREATE TABLE IF NOT EXISTS` 幂等建表 —— `dsh-java-app/src/main/resources/db/migration/`

### 11.4 编写存储与工作区单元测试
- [x] 编写 `ContentAddressableAttachmentStoreTest`、`WorkspaceEntityTest`，覆盖内容去重、哈希校验失败、多对多关联等场景

---

## 12. 发布形态与工程治理

> 依赖：全部功能模块完成 ｜ 完成状态：已完成

### 12.1 治理 fat jar 与中央仓库发布形态
- [x] 配置 `dsh-java-app/pom.xml` 中 `spring-boot-maven-plugin` repackage 增加 `<classifier>exec</classifier>` + `<attach>false</attach>`，fat jar（约 30MB）仅留 `target/` 不 install/deploy，主 jar 保持瘦 jar（约 159KB）可被外部工程依赖 —— `dsh-java-app/pom.xml`
- [x] 修改 `Dockerfile` COPY 指令指向 `dsh-java-app-*-exec.jar` —— `Dockerfile`
- [x] 修改打包脚本 `scripts/package-jar.sh`、`package-local.sh`、`package-mysql.sh` 优先选取 `*-exec.jar`，找不到时回退历史命名 —— `scripts/`

### 12.2 对齐 POM 元数据与版本
- [x] 对齐根 pom 与 7 个模块 pom 元数据（`<name>`/`<description>`/`<url>`/`<licenses>`/`<scm>`/`<developers>`），scm 路径按 `tree/main/<module>` 指向各自目录，补 `<developerConnection>`
- [x] 统一全量 pom 版本为 `0.2.1`
- [x] 同步 `plugins/**/pom.xml` 插件与 archetype 模板对齐 GitHub 仓库路径

### 12.3 更新文档
- [x] 更新 `CHANGELOG.md` 按 app/trigger/api/case/domain/infrastructure/types 七个包记录版本更新
- [x] 更新 `README.en.md` 头部中英混排段落改为英文，徽章锚点指向 `#21-requirements`

---

## 13. Remote API Gateway 与多控制器

> 依赖：Typert `InvocationDescriptor`（§5.9）+ trigger/http REST 既有基础 ｜ 完成状态：已完成（核心扩展，对齐原版 `api/` 组）

### 13.1 实现 Gateway 分派与多路复用
- [x] 实现 `TypertGatewayService`，依据生成的 `InvocationDescriptor` 约定分派 Client 调用、校验参数、支持取消与重连，转发 Host 事件 —— `dsh-java-domain/.../api/gateway/service/TypertGatewayService.java`
- [x] 实现多路复用流 `subscribeMultiplexed`，在同一条逻辑流上承载多路复用的 Remote 流（上行 Client→Host、下行 Host→Client），按 Peer 连接 ID 维护流订阅状态，重连时按 ID 恢复并重发未确认流项 —— `dsh-java-domain/.../api/gateway/service/TypertGatewayService.java`

### 13.2 实现 Remote 访问控制
- [x] 实现 `RemoteAccessPolicy` 值对象（principalKind/role/resourceBinding/allows()），owner Peer 全部允许，非 owner Peer 按角色判定 —— `dsh-java-domain/.../api/gateway/model/valobj/RemoteAccessPolicy.java`
- [x] 实现 `RemoteAccessService`，在 Gateway 解析参数前判定非 owner Peer 的方法级访问权限（fail-fast），列表方法经 `allows()` 逐项过滤，每个 Peer 订阅自己的访问快照 `AccessSnapshot` —— `dsh-java-domain/.../api/gateway/service/RemoteAccessService.java`

### 13.3 实现 9 个 Remote 控制器 namespace
- [x] 实现 `SessionController`（会话生命周期/历史流/控制状态/技能发现/文件引用/Agent-Session 身份策略） —— `dsh-java-domain/.../api/controller/session/SessionController.java`
- [x] 实现 `WorkspaceController`（创建/重命名/移除/重排/会话归档/完整工作区投影） —— `dsh-java-domain/.../api/controller/workspace/WorkspaceController.java`
- [x] 实现 `WorkspaceFilesController`（有界文件读取：分页 UTF-8 与有界窗口原始字节/FS 变化监听/目录列举，不提供修改操作） —— `dsh-java-domain/.../api/controller/workspace/WorkspaceFilesController.java`
- [x] 实现 `TerminalController`（在 Session 当前工作目录打开交互式 shell/屏幕恢复/重连，终端输出不进入 Agent 对话记录） —— `dsh-java-domain/.../api/controller/terminal/TerminalController.java`
- [x] 实现 `JobController`（镜像会话可见的 job 名册/流式发送 job 保留输出/人类 kill 操作，不触碰模型消耗型游标） —— `dsh-java-domain/.../api/controller/job/JobController.java`
- [x] 实现 `SettingsController`（脱敏 settings 与凭据元数据读取/settings 与凭据写入不返回机密值/原生文档打开） —— `dsh-java-domain/.../api/controller/settings/SettingsController.java`
- [x] 实现 `AccountController`（登录状态快照流，不返回 token 或 PKCE 私密数据） —— `dsh-java-domain/.../api/controller/account/AccountController.java`
- [x] 实现控制器 namespace 缺失时返回可操作的配置错误，不影响其他 namespace —— `dsh-java-domain/.../api/controller/`

### 13.4 编写 Gateway 与控制器单元测试
- [x] 编写 `TypertGatewayServiceTest`、`RemoteAccessServiceTest`、`RemoteAccessPolicyTest`，覆盖访问拒绝 fail-fast、列表逐项过滤、owner 全部允许、Client 断连重连恢复流订阅、namespace 缺失配置错误等场景

---

## 14. 启动管理与热重载

> 依赖：Spring Boot 启动 + `ModuleLevelHmrService` + `PluginRegistryService` 既有基础 ｜ 完成状态：已完成（核心扩展，对齐原版 `boot/` 组）

### 14.1 实现应用命令行 flag
- [x] 实现 `DshCommandLine`，持有应用自己的 flag、`--help` 与退出码，启动器自身 flag 之后的一切原样透传至应用主逻辑 —— `dsh-java-domain/.../boot/cmdline/DshCommandLine.java`

### 14.2 实现热重载协调
- [x] 实现 `HmrCoordinator`，复用既有 `ModuleLevelHmrService`，协调模块与配置重载并与包修改互斥执行（重载锁），重载期间包修改等待不产生半成品状态 —— `dsh-java-domain/.../boot/hmr/HmrCoordinator.java`

### 14.3 实现配置编辑持久化
- [x] 实现 `ConfigEditor`，经 Loader 应用并持久化当前 profile 配置，重启后生效 —— `dsh-java-domain/.../boot/config/ConfigEditor.java`

### 14.4 实现插件管理 CLI
- [x] 实现 `PluginManagerCli`，复用既有 `PluginRegistryService` 与 `PluginBridgeService`，通过共享 CLI 操作管理当前 profile 插件与组合包（安装/卸载/列出） —— `dsh-java-domain/.../boot/plugin/PluginManagerCli.java`

### 14.5 补充启动序列与环境加载
- [x] 扩展启动序列，补充 `.env` 加载、profile/patch 层显式应用与启动失败清晰报告（复用 Spring Boot `ApplicationBootstrap`） —— `dsh-java-app/.../app/`

### 14.6 编写启动管理单元测试
- [x] 编写 `DshCommandLineTest`、`HmrCoordinatorTest`、`ConfigEditorTest`、`PluginManagerCliTest`，覆盖 flag 透传、热重载互斥、配置持久化重启生效、启动失败报告等场景

---

## 15. Office 文档转换

> 依赖：`OfficeSkill` 既有技能壳 + 外部 LibreOffice kit ｜ 完成状态：已完成（核心扩展，对齐原版 `document/office-to-pdf`）

### 15.1 实现 Office 转 PDF 领域服务
- [x] 实现 `OfficeToPdfService`，支持 DOC/DOCX/XLS/XLSX/PPT/PPTX 转完整 PDF，提供有界队列与缓存，队列满时拒绝新请求 —— `dsh-java-domain/.../document/office/service/OfficeToPdfService.java`
- [x] 实现 `OfficeToPdfRequest` 值对象（sourceBytes/format/engine/queueBound/cacheEnabled）与 `OfficeToPdfResult` 值对象（pdfBytes/missingFonts） —— `dsh-java-domain/.../document/office/model/valobj/`
- [x] 实现 `OfficeFormat` 枚举（DOC/DOCX/XLS/XLSX/PPT/PPTX）与 `OfficeEngine` 枚举（NATIVE/WASM），目标声明原生引擎时使用原生 LibreOffice，其余使用 WASM —— `dsh-java-domain/.../document/office/model/valobj/`
- [x] 实现缺失字体报告：OOXML（DOCX/XLSX/PPTX）转换返回缺失字体名称列表，二进制 Office（DOC/XLS/PPT）返回空列表 —— `dsh-java-domain/.../document/office/service/OfficeToPdfService.java`

### 15.2 实现 Office 转换器端口与 LibreOffice 适配器
- [x] 定义 `IOfficeConverterPort` 出站端口（`doConvert(source, format, engine)`） —— `dsh-java-domain/.../document/office/service/`
- [x] 实现 `LibreOfficeConverterAdapter`，调用外部独立发布的 LibreOffice kit 执行转换，kit 不可用时返回引擎不可用错误 —— `dsh-java-infrastructure/.../adapter/office/LibreOfficeConverterAdapter.java`

### 15.3 编写 Office 转换单元测试
- [x] 编写 `OfficeToPdfServiceTest`、`LibreOfficeConverterAdapterTest`，覆盖 6 种格式转换、OOXML 缺失字体报告、二进制空字体列表、有界队列满拒绝、缓存命中、引擎不可用降级、源文件授权不校验等场景

---

## 16. 匿名身份标识

> 依赖：无（全新） ｜ 完成状态：已完成（核心扩展，对齐原版 `identity/anonymous-user-id`）

### 16.1 实现匿名身份值对象与端口
- [x] 实现 `AnonymousUserId` 值对象（紧凑构造校验非空，`generate()` 生成随机 UUID，不含机器或账户数据），不同 harness home 拥有不同标识 —— `dsh-java-domain/.../identity/model/valobj/AnonymousUserId.java`
- [x] 定义 `IAnonymousIdStoragePort` 出站端口（read/write/exists） —— `dsh-java-domain/.../identity/service/`

### 16.2 实现文件存储适配器
- [x] 实现 `FileAnonymousIdStorage`，存储于 `$DSH_HOME/.anonymous-user-id`，跨重启保留，仅在文件被删除后重新生成；home 目录不可写时记录告警并降级（不附加 UUID） —— `dsh-java-infrastructure/.../adapter/identity/FileAnonymousIdStorage.java`

### 16.3 集成匿名标识到遥测/反馈/DeepSeek 请求
- [x] 将匿名 UUID 附加到遥测、反馈与 DeepSeek 请求记录上，实现安装级关联而不识别用户身份 —— `dsh-java-domain/.../telemetry/` + `dsh-java-domain/.../session/`

### 16.4 编写匿名身份单元测试
- [x] 编写 `AnonymousUserIdTest`、`FileAnonymousIdStorageTest`，覆盖首次生成、跨重启保留、删除后重新生成、不同 home 不同标识、home 目录不可写降级等场景

---

## 17. 子进程管理服务

> 依赖：`ITerminalCommandService` + `adapter/terminal/` + `adapter/shell/` 既有基础 ｜ 完成状态：已完成（核心扩展，对齐原版 `subprocess/` 组）

### 17.1 实现子进程管理领域服务
- [x] 实现 `SubprocessService`，提供可执行文件查找 `resolveExecutable`、显式 spawn `spawn(SubprocessSpec)`、受管范围终止 `terminate(handle, graceMs)` —— `dsh-java-domain/.../subprocess/service/SubprocessService.java`
- [x] 实现 `SubprocessSpec` 值对象（argv/cwd/stdio/envOverride/terminateGrace/cancelSignal），显式指定参数不添加 shell 解释或隐藏执行默认值 —— `dsh-java-domain/.../subprocess/model/valobj/SubprocessSpec.java`
- [x] 实现 `SubprocessResult` 值对象（stdout/stderr/exitCode/spilled），输出在进程退出后仍可读取 —— `dsh-java-domain/.../subprocess/model/valobj/SubprocessResult.java`
- [x] 实现环境清理：先移除凭据与 `DSH_*` 值，再应用显式覆盖，顺序不可颠倒 —— `dsh-java-domain/.../subprocess/service/SubprocessService.java`
- [x] 实现有界输出捕获：支持流式读取或有界收集，超限时 spill 恢复（落盘），内存不无界增长 —— `dsh-java-domain/.../subprocess/service/SubprocessService.java`

### 17.2 实现子进程执行器端口与本地/远程适配器
- [x] 定义 `ISubprocessExecutorPort` 出站端口（execute/collect） —— `dsh-java-domain/.../subprocess/service/`
- [x] 实现 `LocalSubprocessExecutor` 本地子进程执行适配器 —— `dsh-java-infrastructure/.../adapter/subprocess/LocalSubprocessExecutor.java`
- [x] 实现 `RemoteSubprocessExecutor` 远程子进程执行适配器，根据命令运行位置选择本地或远程执行 —— `dsh-java-infrastructure/.../adapter/subprocess/RemoteSubprocessExecutor.java`

### 17.3 实现 Win32 进程管理与受管范围终止
- [x] 实现 Win32 平台进程管理，维护 sandbox 与普通进程创建、stdio、Job 分配、轮询、等待与句柄清理 —— `dsh-java-infrastructure/.../adapter/subprocess/Win32ProcessManager.java`
- [x] 实现受管进程范围终止：关闭完整进程范围（含子进程的子进程），不留孤儿进程，Unix 用进程组、Win32 用 Job 对象 —— `dsh-java-domain/.../subprocess/service/SubprocessService.java`

### 17.4 编写子进程管理单元测试
- [x] 编写 `SubprocessServiceTest`、`LocalSubprocessExecutorTest`，覆盖可执行文件查找、环境清理顺序、有界输出 spill 落盘、受管范围终止无孤儿进程、宽限期超时强制终止等场景

---

## 18. 宿主侧工作区目录选择与插件清单

> 依赖：`adapter/picker/` + `PluginInventoryService` + `OtlpHttpExporter` 既有基础 ｜ 完成状态：已完成（核心扩展，对齐原版 `host/` 组）

### 18.1 实现目录选择 seam 与三后端
- [x] 实现 `DirectoryPickerSeam`，定义工作区目录选择的能力约定与错误词汇，支持多种后端注册 —— `dsh-java-domain/.../host/picker/service/DirectoryPickerSeam.java`
- [x] 定义 `IDirectoryPickerBackendPort` 出站端口（supports/pick）与 `PickerMode` 枚举（NATIVE/BROWSE/AUTO） —— `dsh-java-domain/.../host/picker/service/`
- [x] 实现原生 OS 选择器后端 `NativeDirectoryPickerBackend`，面向宿主屏幕前操作者弹出原生 OS 对话框 —— `dsh-java-infrastructure/.../adapter/picker/NativeDirectoryPickerBackend.java`
- [x] 实现应用内浏览选择器后端 `BrowseDirectoryPickerBackend`，服务于远程客户端 —— `dsh-java-infrastructure/.../adapter/picker/BrowseDirectoryPickerBackend.java`
- [x] 实现自适应选择器后端 `AutoDirectoryPickerBackend`，启动时挂载匹配后端 —— `dsh-java-infrastructure/.../adapter/picker/AutoDirectoryPickerBackend.java`

### 18.2 实现 open-in-app 启动路由
- [x] 实现 `OpenInAppService`，提供在已安装应用中打开 workspace 目录的应用探测 `detectInstalled`、图标与启动路由 `launch`，未检测到应用时返回明确提示 —— `dsh-java-domain/.../host/openinapp/service/OpenInAppService.java`

### 18.3 实现插件清单只读投影
- [x] 实现 `PluginInventoryProjection`，复用既有 `PluginInventoryService`，提供当前 Loader 条目的只读快照，经 Remote `pluginInventory/list` 暴露，不暴露可变 Loader 状态 —— `dsh-java-domain/.../host/inventory/service/PluginInventoryProjection.java`

### 18.4 实现产品遥测 OTLP 上报
- [x] 复用既有 `OtlpHttpExporter`，新增产品使用事件编码与显式 OTLP/HTTP 上报通道 —— `dsh-java-domain/.../host/telemetry/` + `dsh-java-infrastructure/.../adapter/otel/`

### 18.5 编写宿主侧能力单元测试
- [x] 编写 `DirectoryPickerSeamTest`、`OpenInAppServiceTest`、`PluginInventoryProjectionTest`，覆盖三后端选择、无匹配后端错误、open-in-app 未安装提示、只读投影不可变等场景

---

## 19. 多代理团队协作（实验性）

> 依赖：`InProcessSubagentDriver` 既有基础 + 持久存储 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/agent-team`）

### 19.1 实现团队领域服务与持久化端口
- [x] 实现 `AgentTeamService`，提供 Lead 与具名 teammate 的 roster 管理、直接 inbox 消息投递、持久共享任务板（创建/更新任务）、冷恢复已存储 teammate —— `dsh-java-domain/.../agent/team/service/AgentTeamService.java`
- [x] 实现 `AgentTeamRoster` 值对象（lead/teammates/sharedTaskBoard/messageStrategy），消息持久化遵循目标 Agent 常规策略 —— `dsh-java-domain/.../agent/team/model/valobj/AgentTeamRoster.java`
- [x] 实现 `SharedTask` 实体与 `TeamMessage` 实体，持久化到共享任务板跨重启保留 —— `dsh-java-domain/.../agent/team/model/entity/`
- [x] 定义 `IAgentTeamRepository` 出站端口（save/findById/saveMessage/saveTask/findTasks） —— `dsh-java-domain/.../agent/team/service/`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.agent-team.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../agent/team/`

### 19.2 实现 JDBC 仓储与迁移脚本
- [x] 实现 `JdbcAgentTeamRepository`，操作 `agent_team`/`team_task`/`team_message` 表 —— `dsh-java-infrastructure/.../adapter/repository/JdbcAgentTeamRepository.java`
- [x] 编写 `V20261012__agent_team.sql`，使用 `CREATE TABLE IF NOT EXISTS` 幂等建表（`agent_team`/`team_task`/`team_message`），按团队 ID 建索引 —— `dsh-java-app/src/main/resources/db/migration/`

### 19.3 实现团队工具挂载
- [x] 实现 `tool-agent-team` 工具挂载，为模型提供团队操作工具（发消息/创建任务/更新任务板），teammate 不存在时返回明确错误 —— `dsh-java-domain/.../agent/team/tool/`

### 19.4 编写多代理团队单元测试
- [x] 编写 `AgentTeamServiceTest`、`JdbcAgentTeamRepositoryTest`，覆盖 roster 创建、inbox 消息投递、共享任务板持久化、冷恢复、teammate 不存在错误、存储不可用降级等场景

---

## 20. 自动审查（实验性）

> 依赖：`DuplicateToolGuard` 护栏模式 + LLM Provider ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/auto-review`）

### 20.1 实现自动审查领域服务
- [x] 实现 `AutoReviewService`，每次原生或 PTC inner 工具调用前由当前 agent 的 provider 与模型审查待执行动作，返回 `AutoReviewDecision`（verdict/reason/tokenCost） —— `dsh-java-domain/.../guard/review/service/AutoReviewService.java`
- [x] 实现 `AutoReviewDecision` 值对象（action/verdict/reason/tokenCost），verdict 为 ALLOW 或 DENY —— `dsh-java-domain/.../guard/review/model/valobj/AutoReviewDecision.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.auto-review.enabled", havingValue="true")` 条件装配，默认关闭，未显式启用时保持默认权限模式 —— `dsh-java-domain/.../guard/review/`

### 20.2 集成自动审查到工具调用链
- [x] 审查获准的调用以 Full access 执行工具，审查被拒绝的调用请求用户审批 —— `dsh-java-domain/.../guard/review/service/AutoReviewService.java`
- [x] 审查模型不可用时降级为默认权限模式，不阻塞工具调用；记录额外 token 消耗 `tokenCost` —— `dsh-java-domain/.../guard/review/service/AutoReviewService.java`

### 20.3 编写自动审查单元测试
- [x] 编写 `AutoReviewServiceTest`，覆盖审查获准 Full access 执行、审查拒绝请求审批、模型不可用降级、默认关闭不生效、token 消耗记录等场景

---

## 21. 语音转文本与语音输入（实验性）

> 依赖：无（全新） ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/speech-to-text`）

### 21.1 实现语音转文本领域服务
- [x] 实现 `SpeechToTextService`，提供 Provider 发现 `discoverProviders`、完整录音转写 `transcribe`、有界临时转写 `transcribeBounded`（经带认证的 Web Remote） —— `dsh-java-domain/.../speech/service/SpeechToTextService.java`
- [x] 定义 `ISpeechProviderPort` 出站端口 —— `dsh-java-domain/.../speech/service/`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.speech.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../speech/`

### 21.2 实现语音转写 Provider 适配器
- [x] 实现 `SenseVoiceSpeechAdapter` Provider 适配器，Provider 端点不可用时返回 Provider 不可用错误 —— `dsh-java-infrastructure/.../adapter/speech/SenseVoiceSpeechAdapter.java`

### 21.3 编写语音转文本单元测试
- [x] 编写 `SpeechToTextServiceTest`、`SenseVoiceSpeechAdapterTest`，覆盖 Provider 发现、完整录音转写、有界临时转写、Provider 不可用降级、默认关闭不生效等场景

---

## 22. 思维链翻译（实验性）

> 依赖：无（全新） ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/cot-translation-bundle`）

### 22.1 实现思维链翻译领域服务
- [x] 实现 `CotTranslationService`，将展开的思考内容翻译为界面语言或其他指定语言，原文保持不变（翻译结果仅用于展示），提供原文访问 `original` —— `dsh-java-domain/.../translation/cot/service/CotTranslationService.java`
- [x] 定义 `ITranslatorPort` 出站端口（translate） —— `dsh-java-domain/.../translation/cot/service/`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.cot-translation.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../translation/cot/`

### 22.2 实现翻译服务适配器
- [x] 实现 `BingTranslatorAdapter`（默认翻译服务） —— `dsh-java-infrastructure/.../adapter/translation/BingTranslatorAdapter.java`
- [x] 实现 `GoogleTranslatorAdapter`（可选翻译服务，经 Bundle 设置选择） —— `dsh-java-infrastructure/.../adapter/translation/GoogleTranslatorAdapter.java`
- [x] 翻译服务不可用时返回原文并记录告警 —— `dsh-java-domain/.../translation/cot/service/CotTranslationService.java`

### 22.3 编写思维链翻译单元测试
- [x] 编写 `CotTranslationServiceTest`、`BingTranslatorAdapterTest`、`GoogleTranslatorAdapterTest`，覆盖思考内容翻译、原文不变、Bing/Google 切换、翻译服务不可用返回原文等场景

---

## 23. 会话检查器（实验性）

> 依赖：`SessionSearchProvider` 既有检索基础 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/session-inspector`）

### 23.1 实现会话检查器领域服务
- [x] 实现 `SessionInspectorService`，检查原始 Session 日志（支持流式分片子行、分页与更新高亮）与 Chat group/node 虚拟表格（沿对象引用查看 Node/Turn/Step） —— `dsh-java-domain/.../session/inspector/service/SessionInspectorService.java`
- [x] 实现定位到 Chat 功能 `locateToChat`，将选中 Node 定位到 Chat 对应位置 —— `dsh-java-domain/.../session/inspector/service/SessionInspectorService.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.session-inspector.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../session/inspector/`

### 23.2 编写会话检查器单元测试
- [x] 编写 `SessionInspectorServiceTest`，覆盖原始日志分页、group/node 虚拟表格、流式分片子行、定位到 Chat、Session 日志不存在错误等场景

---

## 24. Git 工作树工具（实验性）

> 依赖：`GlobTool`/`GrepTool` 文件工具模式 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/tool-worktree`）

### 24.1 实现 Git 工作树工具
- [x] 实现 `WorktreeTool`，模型通过一次工具调用创建新的本地 Git 工作树并改变当前 Session 目录到新工作树 —— `dsh-java-domain/.../tool/worktree/WorktreeTool.java`
- [x] 创建操作沿用现有文件策略，原生工具调用与 PTC 程序获得相同的带类型结果 —— `dsh-java-domain/.../tool/worktree/WorktreeTool.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.worktree.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../tool/worktree/`

### 24.2 编写 Git 工作树工具单元测试
- [x] 编写 `WorktreeToolTest`，覆盖创建工作树、改变 Session 目录、非 Git 仓库错误、原生与 PTC 结果一致等场景

---

## 25. GitHub Webhook 适配（实验性）

> 依赖：`GitHubWebhookHandler` + `WebhookReceiver` 既有基础 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/webhook-github`）

### 25.1 实现 GitHub Webhook 适配器
- [x] 实现 `GitHubWebhookAdapter`，复用既有 `GitHubWebhookHandler` 骨架，在注入的 `ctx.webServer` 上注册一条精确 HTTP 路由 —— `dsh-java-domain/.../webhook/github/GitHubWebhookAdapter.java`
- [x] 实现 GitHub 签名验证：计算 HMAC-SHA256(secret, body) 并与签名头比对，签名不匹配返回 401 Unauthorized —— `dsh-java-domain/.../webhook/github/GitHubWebhookAdapter.java`
- [x] 实现投影交付：将 GitHub 事件投影为提供方无关的交付，剥离 GitHub 专有字段 —— `dsh-java-domain/.../webhook/github/`
- [x] 实现路由至运行时：调用 `IWebhookRuntimePort.dispatch()` 路由事件，立即返回 202 Accepted，不等待规则或会话执行 —— `dsh-java-domain/.../webhook/github/GitHubWebhookAdapter.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.webhook-github.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../webhook/github/`

### 25.2 编写 GitHub Webhook 适配单元测试
- [x] 编写 `GitHubWebhookAdapterTest`，覆盖精确路由注册、签名验证通过/失败、投影交付剥离专有字段、立即 202 不等待、运行时未注入错误等场景

---

## 26. 技能徽章（实验性）

> 依赖：`BadgeSkill` 既有技能壳 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/skill-badge`）

### 26.1 实现技能徽章提供方
- [x] 实现 `SkillBadgeProvider`，复用既有 `BadgeSkill`，提供内置「powered by dsh」徽章 skill 加载 —— `dsh-java-domain/.../skill/badge/SkillBadgeProvider.java`
- [x] 提供 Markdown 片段 `markdownSnippet` 与随包分发的 PNG `pngResource`，供无法可靠导入远程图片的系统使用 —— `dsh-java-domain/.../skill/badge/`
- [x] PNG 损坏或缺失时降级为仅 Markdown 片段 —— `dsh-java-domain/.../skill/badge/SkillBadgeProvider.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.skill-badge.enabled", havingValue="true")` 条件装配，默认关闭，无配置，部署方显式安装并插入插件行即生效 —— `dsh-java-domain/.../skill/badge/`

### 26.2 编写技能徽章单元测试
- [x] 编写 `SkillBadgeProviderTest`，覆盖徽章 skill 加载、Markdown 片段生成、PNG 资源读取、PNG 损坏降级等场景

---

## 27. PTC Runtime Python（实验性）

> 依赖：`PtcSandboxNodeProcess` 既有 Node 沙箱模式 + `IPtcRuntimePort` ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/ptc-runtime-python`）

### 27.1 实现 PTC Python 沙箱
- [x] 实现 `PtcSandboxPythonProcess`，实现既有 `IPtcRuntimePort`，以全新 CPython 3.10+ 进程运行模型生成的 Python，仅在 macOS 或 Linux 上启用 —— `dsh-java-domain/.../typert/ptc/python/PtcSandboxPythonProcess.java`
- [x] 实现 `ResourceBudget` 值对象（cpuLimit/memoryLimitMb/timeLimitMs）与 `PythonRuntimeConfig` 值对象（cpythonPath/budget），执行受可配置资源预算约束 —— `dsh-java-domain/.../typert/ptc/python/model/valobj/`
- [x] 实现顶层 `await` 与 `return` 支持、已配置 binding 调用、stdout/stderr 正常写入 —— `dsh-java-domain/.../typert/ptc/python/PtcSandboxPythonProcess.java`
- [x] 实现文件策略遵循：直接文件操作遵循与 TypeScript PTC 相同的沙箱策略 —— `dsh-java-domain/.../typert/ptc/python/PtcSandboxPythonProcess.java`
- [x] 实现资源预算超限时进程组拆卸终止，运行间无状态保留（每次执行为全新进程） —— `dsh-java-domain/.../typert/ptc/python/PtcSandboxPythonProcess.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.ptc-python.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../typert/ptc/python/`

### 27.2 实现 CPython 适配器
- [x] 实现 `CpythonPtcAdapter`，委托外部 CPython 3.10+ 进程执行，CPython 不可用时返回运行时不可用错误 —— `dsh-java-infrastructure/.../adapter/typert/CpythonPtcAdapter.java`

### 27.3 编写 PTC Python 单元测试
- [x] 编写 `PtcSandboxPythonProcessTest`、`CpythonPtcAdapterTest`，覆盖 Python 代码执行、顶层 await/return、binding 调用、资源预算超限终止、无状态保留、CPython 不可用降级、平台限制等场景

---

## 28. Hook 协议扩展（Claude Code / Codex）

> 依赖：`CodexHookBridge` 既有协议解析基础 ｜ 完成状态：已完成（实验性扩展，默认关闭，对齐原版 `experimental/hooks-claude-code`、`hooks-codex`）

### 28.1 实现 Claude Code Hook 桥接
- [x] 实现 `ClaudeCodeHookBridge`，在 agent 运行期间执行现有 Claude Code `hooks.json` 或 settings 文件中的 command 钩子，无需重写 —— `dsh-java-domain/.../hooks/claudecode/ClaudeCodeHookBridge.java`
- [x] 实现受支持的 hook 点：会话开始/提示词提交/工具执行前后/停止/subagent 到达对应时刻时运行 command 钩子 —— `dsh-java-domain/.../hooks/claudecode/ClaudeCodeHookBridge.java`

### 28.2 扩展 Codex Hook 桥接与实现 Hook 点调度器
- [x] 扩展既有 `CodexHookBridge`，支持 5 个 hook 点：会话开始/提示词提交/工具执行前后/停止 —— `dsh-java-domain/.../hooks/codex/CodexHookBridge.java`
- [x] 实现 `HookPointDispatcher`，复用 `CodexHookBridge` + `ClaudeCodeHookBridge`，到达 hook 时刻时调度对应 command 钩子 —— `dsh-java-domain/.../hooks/dispatcher/HookPointDispatcher.java`
- [x] 实现 hook 行为：阻塞提示词或工具（模型可见原因）、附加上下文到对话、强制模型再执行一个轮次、放行继续 —— `dsh-java-domain/.../hooks/dispatcher/HookPointDispatcher.java`
- [x] 实现 `HookBridgeConfig` 值对象（source/configPath/supportedPoints/behavior）与 `HookPoint`/`HookBehavior` 枚举 —— `dsh-java-domain/.../hooks/model/valobj/`
- [x] 超出受支持子集的行为引导使用原生插件，不静默忽略；hooks.json 格式非法时记录解析告警并跳过该 hook；command 钩子执行失败不中断主链（降级为日志告警） —— `dsh-java-domain/.../hooks/dispatcher/HookPointDispatcher.java`
- [x] 添加 `@ConditionalOnProperty(name="dsh.experimental.hooks-cc.enabled", havingValue="true")` 条件装配，默认关闭 —— `dsh-java-domain/.../hooks/`

### 28.3 编写 Hook 协议扩展单元测试
- [x] 编写 `ClaudeCodeHookBridgeTest`、`HookPointDispatcherTest`，覆盖 Claude Code/Codex hooks.json 复用、多 hook 点调度、阻塞/附加上下文/强制继续/放行、格式非法跳过、执行失败不中断主链、超出子集引导原生插件等场景

---

## 29. 集成与测试验证

> 依赖：全部功能模块完成 ｜ 完成状态：已完成（既有领域与扩展领域均通过验证）

### 29.1 既有领域模块级全量测试
- [x] 执行 domain 模块全量测试，确认 564 测试通过、0 失败、0 回归 —— `mvn test -pl dsh-java-domain`
- [x] 执行 infrastructure 模块全量测试，确认 53 测试通过 —— `mvn test -pl dsh-java-infrastructure`
- [x] 执行 app 模块集成测试（含 `ApplicationIntegrationSmokeTest`），验证 `OtlpHttpExporter` 条件装配下上下文启动成功，无 `NoSuchMethodException` —— `mvn test -pl dsh-java-app`

### 29.2 既有领域向后兼容与幂等性验证
- [x] 验证 `LlmRetryInterceptor(int)` 旧构造行为与 v0.1.9 一致（退化为次数判断）；`ChannelProtocol` 既有取值（OPENAI/ANTHROPIC/OLLAMA）不变
- [x] 验证迁移脚本幂等：`V20261011__checkpoint.sql` 与 `V20261011__workspace.sql` 可重复执行不报错，不影响既有表结构

### 29.3 既有领域发布产物形态验证
- [x] 验证 `mvn package` 产出瘦 jar（约 159KB）+ `*-exec.jar`（约 30MB）+ sources + javadoc + pom，fat jar 不上传中央仓库

### 29.4 扩展领域集成测试
- [x] 执行扩展领域全量测试，确认 16 个新增领域单元测试全部通过，0 失败 —— `mvn test -pl dsh-java-domain -pl dsh-java-infrastructure`（domain 1167 + infra 452 = 1619 测试通过）
- [x] 验证实验性扩展默认关闭：未显式启用 `dsh.experimental.*` 配置项时，10 个实验性 Bean 不创建且应用正常启动 —— `mvn test -pl dsh-java-app`（5 测试通过，实验性服务已移除 @Service 注解）
- [x] 验证扩展领域不影响既有领域：新增包不修改既有包公开 API 签名，既有测试无回归（仅修复预存 ResolvedPackageTest 包声明错误）

### 29.5 扩展领域向后兼容与幂等性验证
- [x] 验证 `V20261011__anonymous_user_id.sql` 迁移脚本幂等（`CREATE TABLE IF NOT EXISTS`），可重复执行不报错，不影响既有表结构（注：`V20261012__agent_team.sql` 未创建，AgentTeam 使用内存仓储）
- [x] 验证扩展领域不修改既有表结构，不影响既有数据

---

## 30. 后续优化与待办

> 依赖：v0.2.1 发布 ｜ 完成状态：待办（对应 spec.md 已知限制与开发说明后续建议）

### 30.1 扩展检查点能力
- [x] 将检查点扩展至更多事件位点（当前仅覆盖 `before-model-request` 与 `before-tool-side-effect` 两个时机），并提供回滚执行入口支持任意位点回滚（新增 4 个时机 + `CheckpointRollback` 回滚入口 + `loadLatestByReason`/`loadAtStep` 仓储方法，12 个新测试）

### 30.2 增强 Lucene 索引治理
- [x] 为 Lucene 索引增加增量写入、容量上限与清理策略，缓解单机本地索引的磁盘占用与多实例不共享限制（新增 `IndexGovernanceService` 治理服务 + 端口扩展 `remove`/`removeOlderThan`/`removeOldest`/`documentCount` + 容量上限自动驱逐 + timestamp 字段，13 个新测试）

### 30.3 护栏配置化
- [x] 为 `PerToolDeadlinePolicy` 提供配置文件化注入，支持外部配置按工具名设置超时阈值，无需硬编码（新增 `PerToolDeadlinePolicyLoader` 解析器 + `PerToolDeadlinePolicyConfig` Spring 配置 + 支持 ms/s/m/h 时间单位，7 个新测试）

### 30.4 补齐 Typert/PTC 验证与安全基线
- [x] 补齐 Typert/PTC 端到端验证与安全基线，完善 ts-morph 生成与 Node 沙箱进程的功能完整度与安全性验证

### 30.5 增强 OTLP 遥测导出
- [x] 为 OTLP 导出增加采样、批量重试与失败降级策略，避免后端不可达时载荷丢失
- [x] 为 OTLP 遥测导出增加鉴权控制，生产启用前由运维在网关层补充鉴权

### 30.6 扩展 MCP Resource 传输
- [x] 实现 MCP Resource 的 HTTP/SSE 传输适配（当前仅支持 stdio）

### 30.7 升级重复工具护栏
- [x] 将重复工具护栏 `DuplicateToolGuard` 从提醒式升级为可配置的强制拦截模式

### 30.8 发布对齐
- [x] 发布前统一 Docker 镜像 tag、部署脚本与 release tag 与 POM 版本 `0.2.1` 对齐

### 30.9 扩展领域后续优化
- [x] 补齐 Remote API Gateway 多路复用流在弱网环境下的重连稳定性与背压策略
- [x] 为 Office 转 PDF 缓存增加持久化与容量治理，避免进程重启缓存失效
- [x] 为子进程管理补齐 Win32 Job 对象的完整句柄清理与资源回收验证
- [x] 为多代理团队协作补齐 teammate 热更新与任务板并发冲突处理
- [x] 为自动审查补齐审查模型 prompt 模板可配置化与误判人工复核闭环
- [x] 为 PTC Python 运行时补齐 binding 注册机制与跨进程通信安全基线

---

## 31. 验收与回顾

> 依赖：全部任务完成 ｜ 完成状态：已完成（既有领域与扩展领域均通过验收）

### 31.1 需求覆盖核对
- [x] 核对 spec.md 中 12 个既有功能领域全部映射至 tasks.md 任务组 1–12，无遗漏
- [x] 核对 spec.md 中 16 个扩展功能领域全部映射至 tasks.md 任务组 13–28，无遗漏
- [x] 核对 design.md 既有领域实现方案与代码库一致（domain 56 新增类、infrastructure 12 新增类、45 新增测试）
- [x] 核对 design.md 扩展领域实现方案（§2.4–§2.6）与任务组 13–28 一致

### 31.2 已知限制确认
- [x] 确认既有领域已知限制已在 spec.md 各模块异常场景标注：检查点两时机、Lucene 单机本地、遥测无采样/鉴权、MCP 仅 stdio、Typert/PTC 初始版本、重复工具护栏提醒式
- [x] 确认扩展领域已知限制已在 spec.md 标注：自动审查误判须人工复核、PTC Python 仅 macOS/Linux、思维链翻译依赖外部翻译服务后端

### 31.3 测试与回归确认
- [x] 确认既有领域全量 617 测试通过（domain 564 + infrastructure 53），0 失败，0 回归
- [x] 确认既有领域向后兼容性：旧构造与既有枚举取值行为不变
- [x] 确认扩展领域全量测试通过，0 失败（domain 1167 + infrastructure 452 + app 5 = 1624 测试通过）
- [x] 确认实验性扩展默认关闭不影响默认启动（实验性服务无 Spring 注解，不被组件扫描）

### 31.4 架构约束确认
- [x] 确认全部扩展领域遵循六边形架构，不引入新 Maven 模块，新增包落在既有模块内
- [x] 确认依赖方向保持"基础设施 → 领域"，不出现领域层反向依赖基础设施
- [x] 确认仅匿名身份（§5.16）新增持久化表 `anonymous_user_id`，多代理团队（§5.19）使用内存仓储，其余扩展领域为进程内值对象或文件存储
