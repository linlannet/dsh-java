# DeepSeek Harness Java v0.1.8 开发说明

> v0.1.8 的主线是 Agent 运行期的安全控制与可观测性增强：引入当前工作目录（cwd）感知、审批感知 FS/Shell、工具执行超时与工件收集、MCP Server 断开管理与扩展工具、沙箱额外根目录、运行期问答、会话恢复、流式增强（DeltaBatch + 心跳保活）、数字人/协作/A2A、OpenAI 兼容 URI 统一解析、推理力度透传、会话摘要增强、全局 CORS 与认证增强。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.8` |
| 状态 | 功能开发完成，发布号与镜像链路待发布时统一处理 |
| 基线 | `origin/main`，即 v0.1.7 文档提交 |
| 主线 | cwd 感知、审批感知 FS/Shell、工具超时、MCP 断开管理、沙箱额外根目录、运行期问答、会话恢复、流式增强、数字人/协作/A2A、URI 统一解析、推理力度、会话摘要增强、全局 CORS |
| 架构 | 继续保持 DDD 六边形架构，不改变依赖方向 |
| 新增类 | 43 个（domain 7、api 4、case 2、infrastructure 17、trigger 13） |
| 变更类 | 47 个（types 1、domain 19、api 3、case 4、infrastructure 12、trigger 8） |
| 新增依赖 | spring-boot-actuator 3.3.2、micrometer-core 1.13.2、micrometer-jakarta9 1.13.2、HdrHistogram 2.2.2、LatencyUtils 2.0.3 |

## 2. 版本亮点

1. **cwd 感知**：Agent 运行期可按用户指定工作目录派发 FS/Shell 工具，而非始终使用进程工作目录；`AgentRunFactory` 为每个 Agent 维护 `AtomicReference<String>`，`AgentRunLifecycle.updateCwd` 支持运行期热更新。
2. **审批感知 FS/Shell**：`ApprovalAwareFsPort` 和 `ApprovalAwareShellExecutor` 在写操作前调用 `approvalGate.check`，DENY 时记录合成失败结果不执行工具；`AgentToolCatalog` 按审批模式选择受限/不受限 executor。
3. **工具执行超时**：`ToolCallExecutor` 引入 `TOOL_WORKER` daemon 线程池和 `toolTimeoutMs`（默认 300s），每个工具调用在独立线程执行，超时后判定 `TOOL_TIMEOUT` 并 cancel；`runGroup` 改为 deadline 巡检 + 超时 cancel。
4. **工件收集**：`ToolRunContext` 新增 `artifacts` Set，工具执行时 `recordArtifact` 记录写入路径；`ToolCallExecutor.drainArtifacts` 和 `AgentRun.drainWrittenArtifacts` 在 turn 结束时汇总回传。
5. **MCP 断开管理**：`McpBootstrap` 新增 `disconnectServer`/`isConnected`/`connectedServers`；`McpRuntime` 替代 `McpBootstrapConfig` 中的直接 bootstrap，支持 `connectAll`/`disconnectAll`。
6. **扩展工具**：`ExtensionTools` 提供 6 个工具（install/list/manage/mcp_connect/mcp_disconnect/mcp_list），`IExtensionManagementPort` 定义 5 个管理命令。
7. **沙箱额外根目录**：`SandboxExtraRootsRegistry` 全局注册表，请求级追加可写根目录；`SandboxExecutionPolicy` record 扩展为 3 组件（+workspaceRoots），`resolveWithExtraRoots` 按额外根集解析策略。
8. **运行期问答**：`InMemoryUserQuestionGateway` 新增 `pendingMeta` 和 `PendingQuestion` 内部 record，`getPendingQuestions` 查询待处理问题；`IRuntimeQuestionApi`/`IRuntimeQuestionCase`/`RuntimeQuestionCaseImpl` 提供查询和提交答案的完整链路。
9. **会话恢复**：`AgentResolveNode.findPersistedSession` 从事件日志按 `agentPreset` 匹配 `SessionHeader`，用 `PersistingSessionLog` 重建事件序列，`AgentRunFactory.resume` 恢复内存中丢失的 Agent。
10. **流式增强**：`DeltaBatch` 文本/推理增量批处理，`DonePayloads` 精简 done 载荷；`AgentStreamApi`/`GatewayStreamApi`/`WorkflowStreamApi` 引入 15s 心跳保活（keepalive 注释帧），SSE 超时从 300s 改为 0（不超时，靠心跳保活）。
11. **数字人/协作/A2A**：新增 `DigitalHumanService`/`CollaborationService`/`PlannerService`/`RemoteAgentGateway`/`RemoteEndpointCredentialRegistry`/`AggregatingRuntimeApprovalGateway`，以及 `A2AController`/`AgentCardController`/`CollaborationController`/`DigitalHumanController` 等 10 个 Controller。
12. **URI 统一解析**：`OpenAiCompatibleUriResolver` 静态工具类，`resolveModelsUri`/`resolveChatCompletionsUri` 统一解析 OpenAI 兼容端点；`DeepSeekAdapter`/`UpstreamModelSyncService`/`ModelDiscoverySyncNode` 的本地 URI 解析方法上移到 domain 层。
13. **推理力度透传**：`AgentOptions` 新增 `reasoningEffort` 字段和 `withReasoningEffort`/`withApprovalMode` 方法；`AgentMessageRequestDTO` 新增 `reasoningEffort`；`DeepSeekAdapter.serializeRequest` 写入 `reasoning_effort` 字段。
14. **会话摘要增强**：`ConversationQueryService` 新增 `visibleUserText`/`previewText` 私有方法，剥离 `<hidden-context>` 标签后生成标题和预览；`ConversationSessionSummaryResponseDTO` 新增 `workspaceId` 字段。
15. **全局 CORS**：`WebMvcConfig.addCorsMappings` 配置 `/**` 映射，允许所有来源模式、GET/POST/PUT/PATCH/DELETE/OPTIONS 方法、所有头，`allowCredentials(false)`，`maxAge(3600)`。
16. **认证增强**：`ApiKeyAuthInterceptor` 新增 OPTIONS 预检请求直接放行、`/.well-known/` 前缀放行、`/api/gateway/stream` 请求认证日志追踪。

## 3. 架构变化

v0.1.8 不引入新的模块，也不调整 API、Trigger、Case、Domain、Infrastructure 的依赖方向。变化集中在既有链路内部：

```text
HTTP / Web Console
    ↓
Trigger：RuntimeQuestionController / ExtensionCommandController / A2AController / DeltaBatch / DonePayloads
    ↓
Case：RuntimeQuestionCaseImpl / AgentResolveNode（会话恢复）/ AgentCollectNode（工件收集）
    ↓
Domain：CwdResolvingFsPort / ApprovalAwareFsPort / ApprovalAwareShellExecutor / SandboxExtraRootsRegistry / OpenAiCompatibleUriResolver / ExtensionTools / IExtensionManagementPort
    ↓
Infrastructure：ExtensionManagementService / McpRuntime / AggregatingRuntimeApprovalGateway / DigitalHumanService / CollaborationService
```

关键分层如下：

| 层 | 新增或调整 |
| --- | --- |
| API | 新增运行期问答契约（IRuntimeQuestionApi + 3 DTO）；扩展 AgentMessageRequestDTO（+reasoningEffort/sandboxRoots）、AgentMessageResponseDTO（+artifacts/error）、ConversationSessionSummaryResponseDTO（+workspaceId） |
| Trigger | 新增 10 个 Controller（A2A/AgentCard/Collaboration/DigitalHuman/AttachmentUpload/ExtensionCommand/RuntimeQuestion/ExtensionQuery/WorkspaceFileQuery/WorkspaceFileTreeQuery）；新增 RuntimeQuestionApi/DeltaBatch/DonePayloads；增强 AgentStreamApi/GatewayStreamApi/WorkflowStreamApi（心跳+批处理）；全局 CORS + 认证增强 |
| Case | 新增 RuntimeQuestionCaseImpl；增强 AgentResolveNode（会话恢复+沙箱根目录+推理力度）、AgentCollectNode（工件收集+错误收口） |
| Domain | 新增 7 个类（CwdResolvingFsPort/ApprovalAwareFsPort/ApprovalAwareShellExecutor/SandboxExtraRootsRegistry/OpenAiCompatibleUriResolver/ExtensionTools/IExtensionManagementPort）；增强 19 个类（ToolCallExecutor 超时重构、AgentOptions 推理力度、McpBootstrap 断开管理等） |
| Infrastructure | 新增 17 个类（extension 4、digitalhuman 6、dao/po 6、config 1）；增强 12 个类（DeepSeekAdapter URI 上移、SandboxedFsService/ShellExecutor 多根边界、AgentRunBeanConfig 审批网关+超时+cwd 等） |

## 4. 当前工作目录（cwd）感知

### 4.1 设计目标

让 Agent 运行期可按用户指定 cwd 派发 FS/Shell 工具，而非始终使用进程工作目录。cwd 可在创建 Agent 时指定，也可运行期热更新。

### 4.2 实现

| 类 | 变更 |
| --- | --- |
| **CwdResolvingFsPort**（新增） | 包装 IFsPort，在每次读/写前将相对路径按 `cwdSupplier` 解析为绝对路径 |
| **AgentRunLifecycle** | + `updateCwd(agentId, cwd)` 抽象方法 |
| **AgentRunFactory** | + `cwdRefs` ConcurrentMap\<String, AtomicReference\<String\>\> 字段，为每个 Agent 维护 cwd 引用；实现 `updateCwd` 更新对应 AtomicReference |
| **FsEditTool / FsWriteTool / StrReplaceEditorTool** | + `cwdSupplier` 字段（Supplier\<String\>）+ `resolveAbsolute` 私有方法 + 双参构造重载 `(IFsPort, Supplier<String>)`；execute 内路径解析后 `recordArtifact` |
| **AgentResolveNode** | Agent 已存在时调用 `agentFactory.updateCwd(agentId, cwd)` 热更新 |

### 4.3 配置

| 参数 | 默认值 | 来源 |
| --- | --- | --- |
| `harness.agent.cwd` | `user.dir` | `@Value` 注入 `AgentRunBeanConfig` |

## 5. 审批感知 FS / Shell

### 5.1 设计目标

工具执行前根据审批门禁决定是否放行。DENY 时记录合成失败结果（不执行工具），保持会话回放一致。

### 5.2 实现

| 类 | 变更 |
| --- | --- |
| **ApprovalAwareFsPort**（新增） | 包装 IFsPort，写操作前调 `approvalGate.check` |
| **ApprovalAwareShellExecutor**（新增） | 包装 IShellExecutor，执行前调 `approvalGate.check` |
| **RuntimeApprovalGate** | + `default requiresApproval(toolName)`，默认返回 false |
| **MatrixRuntimeApprovalGate** | + `requiresApproval` 实现，按 agentId × toolName 矩阵判定 |
| **AgentToolCatalog** | + `unrestrictedFsPort` / `unrestrictedShellExecutor` 字段；构造扩展为 4 个 shell/fs 参数（受限 + 不受限）；`createRegistry` 内按审批模式选择 effective executor |
| **ToolCallExecutor.checkApproval** | 先调 `requiresApproval`，若需审批则发出 `requires_approval` 生命周期事件；再调 `check` 获取最终决定 |
| **AggregatingRuntimeApprovalGateway**（新增） | 聚合 broker + endpointDao + humanDao + remoteCredentials 的审批网关，`@Primary` 注入 |

### 5.3 审批模式

| 模式 | 行为 |
| --- | --- |
| `REQUEST_APPROVAL` | 需审批工具调用前发出 `requires_approval` 信号，等待人工裁决 |
| `AUTO_APPROVE` | 自动放行所有工具 |
| `FULL_OPEN` | 不受限 executor 直接执行 |

## 6. 工具执行超时与工件收集

### 6.1 超时机制

`ToolCallExecutor` 引入独立 daemon 线程池和超时判定：

| 组件 | 说明 |
| --- | --- |
| `TOOL_WORKER` | `Executors.newCachedThreadPool`，daemon 线程，命名为 `tool-worker` |
| `DEFAULT_TOOL_TIMEOUT_MS` | 300000（5 分钟） |
| `toolTimeoutMs` | 实例字段，构造时传入；<= 0 时不启用超时 |
| `awaitWithTimeout` | `future.get(timeout, MILLISECONDS)`，超时返回 `TOOL_TIMEOUT` 失败结果 |
| `isTimeoutFailure` | 检查 Slot 结果是否为 `TOOL_TIMEOUT` 错误码 |
| `runGroup` | `toolTimeoutMs > 0` 时 `supplyAsync(TOOL_WORKER)` 派发；deadline 巡检 + 超时 cancel；`settled` KeySetView 防止 handle 回调与超时路径重复写入 |

### 6.2 工件收集

| 组件 | 说明 |
| --- | --- |
| `ToolRunContext.artifacts` | `Set<String>`，工具执行时 `recordArtifact(path)` 记录写入路径 |
| `ToolCallExecutor.turnArtifacts` | `ConcurrentHashMap.newKeySet()`，`commitOne` 时 `addAll(slot.ctx.drainArtifacts())` |
| `ToolCallExecutor.drainArtifacts` | 取出全部工件并清空内部集合 |
| `AgentRun.drainWrittenArtifacts` | default 方法，委托给 `toolExecutor.drainArtifacts()` |
| `ReactLoopAgent.drainWrittenArtifacts` | 实现委托 |
| `AgentCollectNode` | `agent.drainWrittenArtifacts()` 收集后传入 `AgentMessageResponseDTO.artifacts` |
| `AgentMessageResponseDTO` | + `artifacts` (List\<String\>) 字段 |

### 6.3 错误收口

`AgentCollectNode.doApply` 捕获 `whenIdle().join()` 异常时，提取 cause 信息存入 `turnError`，最终传入 `AgentMessageResponseDTO.error` 字段，避免异常丢失。

### 6.4 配置

| 参数 | 默认值 | 来源 |
| --- | --- | --- |
| `harness.agent.tool-timeout-ms` | 300000 | `@Value` 注入 `AgentRunBeanConfig` |

## 7. MCP Server 断开管理与扩展工具

### 7.1 MCP 断开管理

| 类 | 变更 |
| --- | --- |
| **McpBootstrap** | + `disposers` Map\<String, List\<Runnable\>\> 字段；+ `disconnectServer(name)` / `isConnected(name)` / `connectedServers()` |
| **McpRuntime**（新增） | 替代 `McpBootstrapConfig` 中的直接 bootstrap，`connectAll(configs)` / `disconnectAll()` |
| **McpBootstrapConfig** | 依赖从 `ToolRegistry` + `McpBootstrap` 改为单一 `McpRuntime`；`run` 调 `mcpRuntime.connectAll`；`onApplicationEvent` 调 `mcpRuntime.disconnectAll` |

### 7.2 扩展工具

| 类 | 说明 |
| --- | --- |
| **ExtensionTools**（新增） | 6 个工具：`extension_skill_install` / `extension_skill_list` / `extension_skill_manage` / `extension_mcp_connect` / `extension_mcp_disconnect` / `extension_mcp_list` |
| **IExtensionManagementPort**（新增） | 扩展管理端口接口，5 个内部类：安装/卸载/列表/启用/禁用命令 |
| **ExtensionManagementService**（新增） | 实现 `IExtensionManagementPort` |
| **ExtensionAgentToolRegistrar**（新增） | 扩展工具注册器 |
| **ExtensionConfigStore**（新增） | 扩展配置存储 |
| **ExtensionBeanConfig**（新增） | 扩展 Bean 配置 |
| **ExtensionCommandController**（新增） | 扩展命令 HTTP 端点 |
| **ExtensionQueryController**（新增） | 扩展查询 HTTP 端点 |

## 8. 沙箱额外根目录

### 8.1 设计目标

允许请求级追加沙箱可写根目录，不局限于启动时配置的 workspace。请求中携带 `sandboxRoots` 时更新全局注册表，并确保 cwd 包含在内。

### 8.2 实现

| 类 | 变更 |
| --- | --- |
| **SandboxExtraRootsRegistry**（新增） | 全局注册表，`update(List<Path>)` / `current()` |
| **ISandboxPolicyService** | + `default resolveWithExtraRoots(request, List<Path>)` |
| **SandboxPolicyService** | + `resolveWithExtraRoots` 实现 |
| **SandboxExecutionPolicy** | record 2→3 组件（+workspaceRoots）；+ `workspaceWrite(List<Path>)` 静态工厂 / `allowsWriteWithin(Path)` / `workspaceRoots()` |
| **SandboxedFsService** | + `extraRootsSupplier` 字段；`execute` 改用 `resolveWithExtraRoots`；`write` 的 `WORKSPACE_WRITE` 分支改为 `delegate.write(path, content, policy.workspaceRoots())` |
| **SandboxedShellExecutor** | + `extraRootsSupplier` 字段；`execute` 改用 `resolveWithExtraRoots`；`executeInWorkspace` 多根边界检查 |
| **LocalFsService** | + `resolveForWrite(path, allowedRoots)` 按额外可写根集合校验写边界 |
| **HarnessApplicationConfig** | `shellExecutor` / `fsService` Bean 注入 `SandboxExtraRootsRegistry`，构造时传 `() -> sandboxExtraRootsRegistry.current()` |
| **AgentResolveNode** | 请求 `sandboxRoots` 非空时更新 `sandboxExtraRootsRegistry`，确保 cwd 包含在内 |
| **AgentMessageRequestDTO** | + `sandboxRoots` (List\<String\>) 字段 |

## 9. 运行期问答

### 9.1 设计目标

当 Agent 工具执行需要人工审批或选择时，通过 HTTP 接口查询待处理问题并提交答案。

### 9.2 链路

```text
RuntimeQuestionController → RuntimeQuestionApi → IRuntimeQuestionCase → RuntimeQuestionCaseImpl → InMemoryUserQuestionGateway
```

### 9.3 契约

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/runtime/questions` | `GET` | 查询待处理问题列表 |
| `/api/runtime/questions/{questionId}/answer` | `POST` | 提交用户答案 |

请求/响应 DTO：

| DTO | 字段 |
| --- | --- |
| `RuntimeQuestionResponseDTO` | questionId, agentId, questions (List\<RuntimeQuestionItemDTO\>), createdAt |
| `RuntimeQuestionItemDTO` | id, question, header, options (List\<RuntimeQuestionOptionDTO\>), multiSelect |
| `RuntimeQuestionOptionDTO` | label, description |
| `SubmitUserAnswerRequestDTO` | answers (List\<AnswerItemDTO\>) |
| `AnswerItemDTO` | id, selected (List\<String\>), custom |
| `SubmitUserAnswerResponseDTO` | submitted, error |

### 9.4 Gateway 增强

| 类 | 变更 |
| --- | --- |
| **InMemoryUserQuestionGateway** | + `pendingMeta` ConcurrentMap\<String, PendingQuestion\> + `PendingQuestion` 内部 record（requestId, agentId, questions, createdAt）；+ `getPendingQuestions()` |
| **RuntimeQuestionCaseImpl** | `listPending` 转换 `getPendingQuestions` 为 DTO；`submitAnswer` 校验后委托 `gateway.submitAnswer` |

## 10. 会话恢复

### 10.1 设计目标

Agent 内存实例缺失时，从事件日志恢复会话，避免重启后丢失上下文。

### 10.2 实现

`AgentResolveNode.findPersistedSession(agentId)`：

1. 从 `sessionEventStore.listHeaders(200)` 查找 `agentPreset` 匹配的 `SessionHeader`；
2. 找到后用 `PersistingSessionLog` 重建事件序列；
3. 调用 `agentFactory.resume(agentId, options, workDir, restoredSession)` 恢复 Agent。

### 10.3 冲突检测

Agent 已存在且正在执行时，若新请求是流式请求（`deltaSink` 或 `toolCallEventSink` 非空）且 `agent.status() == RUNNING`，抛出 `IllegalStateException`，提示等待完成或先停止当前生成。

## 11. 流式增强

### 11.1 DeltaBatch 批处理

`DeltaBatch` 对模型输出文本/推理增量做批处理，在 toolCall/finish/done/error 事件前先 flush，减少 SSE 帧数量。

### 11.2 心跳保活

| 组件 | 说明 |
| --- | --- |
| `HEARTBEAT_INTERVAL_MS` | 15 秒 |
| `heartbeatExecutor` | 单线程调度池 |
| `startHeartbeat` | 定时发送 `:keepalive` 注释帧 |
| SSE 超时 | 从 300s 改为 0（不超时，靠心跳保活） |

`AgentStreamApi`、`GatewayStreamApi`、`WorkflowStreamApi` 均引入心跳保活，`onCompletion`/`onTimeout`/`onError` 触发 `stopHeartbeat`。

### 11.3 DonePayloads 精简

`DonePayloads` 封装 done 载荷，`GatewayStreamApi.slimAgentDone` 只含 `agentId`/`sessionId`/`status`/`messages`/`messageCount`/`artifacts`/`error`，减少冗余字段。

### 11.4 全链路日志

`GatewayStreamApi` 新增 `requestId`(UUID) 和 `startedAt`，`onCompletion`/`onTimeout`/`onError` 记录耗时日志；`summarizeRequest`/`summarizeAgentRequest`/`summarizeWorkflowRequest` 辅助方法摘要含 `reasoningEffort`、`sandboxRoots` 等新字段。

### 11.5 toolCall status 修正

`AgentApi.sendMessageStreaming` 中 status 判定从 `"result".equals(phase) ? status : "running"` 改为 `"result".equals(phase) || status != null ? status : "running"`，非 result 阶段若已有 status 则保留。

## 12. 数字人 / 协作 / A2A

### 12.1 新增服务

| 类 | 说明 |
| --- | --- |
| **DigitalHumanService** | 数字人服务 |
| **CollaborationService** | 协作服务 |
| **PlannerService** | 规划服务 |
| **RemoteAgentGateway** | 远程 Agent 网关 |
| **RemoteEndpointCredentialRegistry** | 远程端点凭证注册表 |
| **AggregatingRuntimeApprovalGateway** | 聚合审批网关（broker + endpointDao + humanDao + remoteCredentials） |

### 12.2 新增 DAO / PO

| 类 | 说明 |
| --- | --- |
| **ICollaborationDao** / **CollaborationPO** | 协作数据访问 |
| **IDigitalHumanDao** / **DigitalHumanPO** | 数字人数据访问 |
| **IDigitalHumanEndpointDao** / **DigitalHumanEndpointPO** | 数字人端点数据访问 |

### 12.3 新增 Controller

| Controller | 说明 |
| --- | --- |
| **A2AController** | Agent-to-Agent 通信端点 |
| **AgentCardController** | Agent Card 元数据端点 |
| **CollaborationController** | 协作管理端点 |
| **DigitalHumanController** | 数字人管理端点 |
| **AttachmentUploadController** | 附件上传端点 |
| **RuntimeQuestionController** | 运行期问答端点 |
| **ExtensionCommandController** | 扩展命令端点 |
| **ExtensionQueryController** | 扩展查询端点 |
| **WorkspaceFileQueryController** | 工作区文件查询端点 |
| **WorkspaceFileTreeQueryController** | 工作区文件树查询端点 |

## 13. OpenAI 兼容 URI 统一解析与推理力度

### 13.1 URI 解析上移

`OpenAiCompatibleUriResolver` 静态工具类统一解析 OpenAI 兼容端点：

| 方法 | 说明 |
| --- | --- |
| `resolveModelsUri(baseUrl)` | 解析模型列表端点（自动补 /v1） |
| `resolveChatCompletionsUri(baseUrl)` | 解析对话补全端点 |

以下类的本地 URI 解析方法删除，改用 `OpenAiCompatibleUriResolver`：

- `DeepSeekAdapter`（`resolveChatCompletionsUri` / `resolveModelsUri`）
- `UpstreamModelSyncService`（`resolveModelsUri`）
- `ModelDiscoverySyncNode`（`resolveModelsUri`）

### 13.2 推理力度透传

| 类 | 变更 |
| --- | --- |
| **AgentOptions** | + `reasoningEffort` 字段；+ `withReasoningEffort(String)` / `withApprovalMode(ApprovalModeVO)` 方法；构造 8→9 参 |
| **AgentMessageRequestDTO** | + `reasoningEffort` (String) 字段 |
| **DeepSeekAdapter.serializeRequest** | 写入 `reasoning_effort` 字段（temperature 之后、max_tokens 之前） |
| **AgentResolveNode.buildAgentOptions** | 链式 `.withReasoningEffort(request.reasoningEffort()).withApprovalMode(approvalMode(request))` |

## 14. 会话摘要增强

### 14.1 可见文本剥离

`ConversationQueryService` 新增 `visibleUserText` / `previewText` 私有方法：

| 方法 | 说明 |
| --- | --- |
| `visibleUserText(content)` | 移除 `<hidden-context>...</hidden-context>` 及未闭合残片；若含"用户原始请求："标记，截取标记之后内容 |
| `previewText(content)` | 优先使用 `visibleUserText`，不可见时回退到原始内容 |

`toSummary` / `toSummaryWithAdmin` / `toSummaryFromHeader` 改用可见文本生成标题和预览，标题为空时回退"新会话"，预览为空时回退"等待消息"。

### 14.2 workspaceId 透传

| 类 | 变更 |
| --- | --- |
| **ConversationSessionSummaryResponseDTO** | + `workspaceId` (String) 字段 |
| **ConversationRecentSessionsNode** | DTO 构造透传 `summary.workspaceId()` |

### 14.3 批量写入

| 类 | 变更 |
| --- | --- |
| **ISessionEventRowDao** | + `insertBatch(@Param("rows") List<SessionEventRowPO>)` 方法，`<script>`+`<foreach>` 批量插入 |
| **JdbcSessionEventStore** | `appendBatch` 从循环 `dao.insert` 改为 `dao.insertBatch(rows)` 批量写入 |

### 14.4 工作区文件查询

| 类 | 变更 |
| --- | --- |
| **WorkspaceRegistryService** | `safeName` 正则放宽（仅替换路径分隔符，允许中文等字符）；+ `fileTree(path, maxDepth)` 递归构建目录树；+ `resolveWorkspaceDir(name)` / `resolveInsideRoot(rawPath)` 方法 |

## 15. 全局 CORS 与认证增强

### 15.1 全局 CORS

`WebMvcConfig.addCorsMappings`：

| 配置 | 值 |
| --- | --- |
| 路径模式 | `/**` |
| allowedOriginPatterns | `*` |
| allowedMethods | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| allowedHeaders | `*` |
| allowCredentials | false |
| maxAge | 3600 |

### 15.2 认证增强

`ApiKeyAuthInterceptor`：

| 变更 | 说明 |
| --- | --- |
| OPTIONS 预检 | 直接放行 |
| `/.well-known/` 前缀 | 公开静态资源放行 |
| `/api/gateway/stream` | 进入/放行/通过/失败各阶段记录 info/warn 日志 |

### 15.3 请求进入日志

`GatewayStreamController.stream()` 入口记录 `source` 和 `requestType`（agent/workflow/empty/unknown）。

## 16. 其他增强

### 16.1 Skill Provider

| 类 | 变更 |
| --- | --- |
| **FilesystemSkillProviderPort** | + `disabledNames` volatile Set 字段 + `setDisabledNames` / `dshHome` / `customRoots` 方法；`discover` 读取 `disabledNames` 快照并跳过被禁用的目录名和 `.md` 技能名 |
| **HarnessApplicationConfig** | `skillProviderPort` 返回类型从 `ISkillProviderPort` 改为具体类型 `FilesystemSkillProviderPort` |

### 16.2 Subagent Provider

| 类 | 变更 |
| --- | --- |
| **OutOfProcessSubagentProvider** | `command` 字段从 `final` 改为 `volatile`；+ `setCommand(String)` 方法 |
| **AgentRunBeanConfig** | `agentRunFactorySubagentInit` 新增 `ObjectProvider<ClaudeCodeSubagentProvider/CodexSubagentProvider/AcpSubagentProvider>` 参数，按可用性注册到 `subagentRegistry` |

### 16.3 Persona 增强

`AgentRunBeanConfig` persona 文本新增三段：
1. 扩展管理（extension_skill_install/list/manage、extension_mcp_*）
2. `shell_execute` 优先用 `cwd` 参数提示
3. 网页与图表产物（HTML/ECharts/draw.io 内嵌预览）

## 17. 接口与配置速查

### 17.1 新增接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/runtime/questions` | `GET` | 查询待处理问题 |
| `/api/runtime/questions/{id}/answer` | `POST` | 提交答案 |
| `/api/a2a/...` | - | A2A 通信 |
| `/api/agent-card/...` | - | Agent Card 元数据 |
| `/api/collaboration/...` | - | 协作管理 |
| `/api/digital-human/...` | - | 数字人管理 |
| `/api/attachments/upload` | `POST` | 附件上传 |
| `/api/extensions/command/...` | - | 扩展命令 |
| `/api/extensions/query/...` | - | 扩展查询 |
| `/api/workspace/files/...` | - | 工作区文件查询 |
| `/api/workspace/file-tree/...` | - | 工作区文件树查询 |

### 17.2 数据契约变化

| 对象 | 变化 |
| --- | --- |
| `AgentMessageRequestDTO` | + `reasoningEffort` (String)、`sandboxRoots` (List\<String\>) |
| `AgentMessageResponseDTO` | + `artifacts` (List\<String\>)、`error` (String) |
| `ConversationSessionSummaryResponseDTO` | + `workspaceId` (String) |
| `RuntimeQuestionResponseDTO` | 新增 |
| `SubmitUserAnswerRequestDTO` | 新增 |
| `SubmitUserAnswerResponseDTO` | 新增 |
| `ToolRunContext` | + `artifacts` Set + `recordArtifact` / `drainArtifacts` |
| `AgentOptions` | + `reasoningEffort` 字段 + `withReasoningEffort` / `withApprovalMode` 方法 |
| `SandboxExecutionPolicy` | record 2→3 组件（+workspaceRoots） |

### 17.3 配置变化

| 参数 | 默认值 | 来源 |
| --- | --- | --- |
| `harness.agent.tool-timeout-ms` | 300000 | `AgentRunBeanConfig` `@Value` |
| `harness.agent.cwd` | `user.dir` | `AgentRunBeanConfig` `@Value` |
| `harness.agent.temperature` | null | `ReactLoopAgent` 系统属性/环境变量 |
| SSE 超时 | 0（不超时） | `AgentStreamApi` 代码常量 |
| 心跳间隔 | 15s | `AgentStreamApi` / `GatewayStreamApi` / `WorkflowStreamApi` 代码常量 |

### 17.4 新增依赖

| 依赖 | 版本 |
| --- | --- |
| spring-boot-actuator | 3.3.2 |
| spring-boot-actuator-autoconfigure | 3.3.2 |
| micrometer-core | 1.13.2 |
| micrometer-jakarta9 | 1.13.2 |
| HdrHistogram | 2.2.2 |
| LatencyUtils | 2.0.3 |

## 18. 兼容性变化

| 变化 | 兼容性 |
| --- | --- |
| `AgentMessageRequestDTO` 新增字段 | 原有字段语义不变；新字段为空时走默认逻辑 |
| `AgentMessageResponseDTO` 新增字段 | 原有字段语义不变；`artifacts` 为空列表，`error` 为 null |
| `ConversationSessionSummaryResponseDTO` 新增字段 | `workspaceId` 可为 null |
| `SandboxExecutionPolicy` 构造变更 | 旧 2 参构造委托到 3 参，workspaceRoots 为空列表 |
| `AgentOptions` 构造变更 | 旧 8 参构造保留，新 9 参构造末尾加 reasoningEffort |
| `ToolCallExecutor` 构造变更 | 旧 3/4 参构造保留，委托到 5 参，toolTimeoutMs 默认 300000 |
| URI 解析方法上移 | `DeepSeekAdapter`/`UpstreamModelSyncService`/`ModelDiscoverySyncNode` 本地方法删除，改用 `OpenAiCompatibleUriResolver` 静态调用 |
| `McpBootstrapConfig` 依赖变更 | 从 `McpBootstrap` 改为 `McpRuntime` |
| 全局 CORS | 不影响既有端点，OPTIONS 预检直接放行 |
| 既有 Agent / Workflow API | 未调整调用方式 |

## 19. 升级注意

1. `AgentMessageRequestDTO` 新增 `reasoningEffort` 和 `sandboxRoots` 字段，前端调用时按需传入；
2. `AgentMessageResponseDTO` 新增 `artifacts` 和 `error` 字段，前端需处理错误展示；
3. `SandboxExecutionPolicy` 构造从 2 参变 3 参，直接构造的代码需适配；
4. `AgentOptions` 构造从 8 参变 9 参，直接构造的代码需适配；
5. `ToolCallExecutor` 构造新增 5 参重载，直接构造的代码需适配；
6. `DeepSeekAdapter`/`UpstreamModelSyncService`/`ModelDiscoverySyncNode` 的 `resolveModelsUri` 本地方法已删除，改用 `OpenAiCompatibleUriResolver`；
7. `McpBootstrapConfig` 依赖从 `McpBootstrap` 改为 `McpRuntime`，Bean 配置需调整；
8. 全局 CORS 已开启，公网部署需确认安全策略；
9. SSE 超时改为 0（不超时），靠 15s 心跳保活，确认前端兼容注释帧；
10. Maven POM 仍是 `0.1.6`，正式发布前需要统一升到 `0.1.8` 并同步镜像与部署脚本。

## 20. 已知限制

| 事项 | 说明 |
| --- | --- |
| 工具超时单机有效 | `TOOL_WORKER` 是 JVM 内线程池，超时 cancel 只能中断 future，不能强制终止工具线程 |
| 沙箱额外根目录全局生效 | `SandboxExtraRootsRegistry` 是全局注册表，多请求并发时可能互相影响 |
| 会话恢复最多 200 条 | `findPersistedSession` 从 `listHeaders(200)` 查找，超过 200 条会话时不保证找到 |
| 心跳保活单机有效 | 心跳是 JVM 内调度，多实例部署时每个实例独立心跳 |
| 数字人/协作/A2A 初版 | 新增服务为初始版本，功能完整度和稳定性待验证 |
| 扩展工具初版 | `ExtensionTools` 为初始版本，MCP 连接管理功能待验证 |
| 全局 CORS allowCredentials=false | 不支持携带凭证的跨域请求，需要认证的跨域场景需额外处理 |

## 21. 后续建议

1. 为工具超时增加可配置的全局上限和按工具名差异化超时；
2. 为沙箱额外根目录增加请求级隔离，避免多请求并发互相影响；
3. 为会话恢复增加分页查找和按时间范围过滤；
4. 为数字人/协作/A2A 补齐端到端测试和文档；
5. 为扩展工具补齐 MCP 连接管理的端到端验证；
6. 为全局 CORS 增加按路径细粒度配置；
7. 将心跳间隔和 SSE 超时改为可配置；
8. 发布前完成版本号、Docker 镜像、部署脚本和 release tag 的统一升级。

## 22. 结论

v0.1.8 把 Agent 运行期的安全控制从"启动时配置"推进到"请求级动态感知"：cwd 可按用户指定、沙箱根目录可请求级追加、审批门禁可运行期裁决、工具执行可超时取消。同时补齐了工件收集、会话恢复、流式增强（DeltaBatch + 心跳保活）等可观测性能力，并引入数字人/协作/A2A 和扩展工具为后续生态扩展打下基础。

下一阶段的重点是补齐新增功能的端到端验证、按工具名差异化超时、沙箱根目录请求级隔离，并在进入正式发布前统一版本与镜像链路。

