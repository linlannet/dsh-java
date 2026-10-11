# 领域层模块速查（Domain Modules Reference）

> 本文按"包名 → 子包分层 → 关键对象 → 端口 → 与谁打交道"的口径，逐个讲清楚 `dsh-java-domain` 下所有 37 个一级 / 二级模块（包）。
>
> **阅读建议**：
> - 想理解"为什么这样分"：先看 `docs/md/domain-design.md`（事件风暴 + 限界上下文 + 端口-适配器）。
> - 想理解"具体代码怎么走"：再看 `docs/md/domain-case-deep-dive.md`（策略树 + ReactLoopAgent 详解）。
> - 想查"某个模块是干什么的 / 该找哪个类 / 该注入哪个端口"：本文。

---

## 0. 模块总览（一行清单）

| # | 模块 | 一句话用途 |
| - | --- | --- |
| 1 | **agent** | Agent 执行引擎核心：ReAct 循环、消息编排、系统提示词组装、子 Agent、审批与中断 |
| 2 | agent.ask | 面向用户提问 / 确认的弹窗式交互工具与端口 |
| 3 | agent.compaction | 对 Agent 上下文（事件流）进行压缩、裁剪、归纳，控制上下文长度 |
| 4 | agent.subagent | 子 Agent 注册表与 Fork / Spawn 两种 in-process 启动方式 |
| 5 | **acp** | ACP（Agent Client Protocol）服务端与对外暴露的协议消息 |
| 6 | **coderuntime** | 代码运行时抽象（命名空间、运行请求与结果），为远程代码执行做端口封装 |
| 7 | **credentials** | 凭据管理：解析凭据引用、统一提供凭据给 LLM/插件等 |
| 8 | **e2b** | E2B 远程沙箱的执行与文件系统端口封装（当前仅有 Stub） |
| 9 | **goal** | 目标管理：聚合 + 命令/查询服务 + 仓储 |
| 10 | **guard** | 运行守卫：工具超时策略、重复调用提醒引擎 |
| 11 | **hooks** | 生命周期 Hook 机制：匹配器、输出合并器、HooK 点枚举 |
| 12 | **jobs** | 后台任务（job）的注册表：提交、列表、读取、终止 |
| 13 | **llm** | LLM 策略抽象：流式块、重试策略、Token 用量、错误码 |
| 14 | **lsp** | LSP（语言服务）能力封装：location / hover / references 查询 |
| 15 | **plan** | 计划模式：状态切换 + 退出计划模式的工具 |
| 16 | **plugin** | 插件域"门面"（接口根 + 三个子域协调） |
| 17 | plugin.bridge | 插件桥接层：识别接入方式（Java / Node / MCP / 其他），产出绑定计划 |
| 18 | plugin.registry | 插件注册层：安装、启停状态、安装计划与结果 |
| 19 | plugin.runtime | 插件运行时层：启动、运行、停止插件实例 |
| 20 | **runtime** | 运行环境总模块入口（接口 + 路由点） |
| 21 | runtime.model | 模型路由：按可选渠道编码解析上游模型 |
| 22 | runtime.setting | 模型渠道：用户保存、激活和删除模型 / API key 配置 |
| 23 | runtime.tool | 工具解析：内置 + 插件工具目录的查询、选择与解析 |
| 24 | runtime.profile | 执行画像：headless / web 等 Profile 策略选择 |
| 25 | **sandbox** | 沙箱执行策略：模式 + 工作区根目录 + request-time 解析 |
| 26 | **schedule** | 调度任务（计划任务）的持久化与服务 |
| 27 | **sdk** | SDK 层：对外客户端 / 服务端的统一封装（transport + handler） |
| 28 | **session** | 会话域"门面"（接口根 + 三个子域协调） |
| 29 | **shared** | 通用共享基础设施：跨域枚举 + 策略路由抽象基类 |
| 30 | **skill** | 技能系统：技能定义、候选匹配、调用策略、提供方适配 |
| 31 | **storage** | KV 存储抽象：键值、快照、错误码、存储后端端口 |
| 32 | **task** | 任务域"门面"（接口根 + 五个子域协调） |
| 33 | **terminal** | 终端交互：发送命令、读取输出、会话快照 |
| 34 | **todo** | 待办事项：值对象 + 写入工具 + 服务实现 |
| 35 | **tool** | 工具域"门面"（接口根 + 各工具族子包 + 执行核心） |
| 36 | **typert** | 类型注册 / 远程类型协议：本地与远程 registry、lookup 契约 |
| 37 | **workflow** | 工作流编排：多步骤流程定义、执行、状态与停止原因 |

> 标粗的"门面"包通常仅放接口根（如 `IHarnessSessionRegistryService`）和少量 re-export；真正逻辑在子包里。

---

## 1. 子包分层约定

领域包统一按以下目录骨架组织：

```
<module>/
├── adapter/
│   ├── port/                 # 出口能力端口（Domain 侧接口）
│   └── repository/           # 仓储端口（Domain 侧接口）
├── model/
│   ├── aggregate/            # 聚合根
│   ├── entity/               # 实体（含状态机与行为）
│   └── valobj/               # 值对象（不可变 record）
└── service/                  # 领域服务实现
    └── <Module>Service.java
<module>.java                  # 领域服务接口根（部分模块用 IxxxService 形式）
```

- **adapter/port 与 adapter/repository 是"内"边界**，由 infrastructure 实现。
- **model 下严禁出现 Spring 注解**，允许使用 `@Value` / `record` / sealed。
- 工具型模块（如 `tool/fs`、`tool/web`）额外有 `adapter/` 直接包含"被工具包装的端口适配器"骨架，但仍由 infra 注入。

---

## 2. Agent 执行核心

### 2.1 `agent`（顶级）

**职责**：Agent 执行引擎的核心：ReAct 循环、消息编排、系统提示词组装、审批与中断。

**子包**：`service/`（主）、`model/entity/`、`model/valobj/`、`adapter/port/`；以及 `agent.ask`、`agent.compaction`、`agent.subagent`（见下文）。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `service/ReactLoopAgent.java` | 唯一的驱动器；`turn()` 循环 + LLM 流式 + 工具执行 + 状态机 |
| `service/Agent.java` | Agent 抽象接口（send / cancel / runMaintenance） |
| `agentrun/AgentRunFactoryPort.java` | 组合根端口，由 infra `AgentFactory` 实现 |
| `service/Inbox.java` | 用户消息收件箱；驱动 `kick()` |
| `service/SystemPromptAssembler.java` + `ScopedSystemPromptAssembler.java` | 系统提示词组装（按 profile 注入） |
| `service/InboxNotifications.java` / `LoggingInboxNotifications.java` | inbox 事件监听（用于 UI 提醒） |
| `service/AgentEventListener.java` | Agent 事件监听器 SPI |
| `model/entity/Phase.java` | sealed 状态机（Idle / Running / Maintenance） |
| `model/entity/AgentOptions.java` | Agent 配置项 |
| `model/valobj/AgentCancelCause.java` | 取消原因枚举 |
| `model/valobj/InboxTarget.java` | inbox 投递目标（NEXT_TURN / 这一轮 / ...） |
| `model/valobj/AgentStatus.java` | 对外暴露状态 |
| `model/valobj/InboxSplice.java` | 拼接消息结构 |
| `adapter/port/IRuntimeApprovalBroker.java` | 运行期审批 broker 端口（含 PendingApprovalView） |

**关键端口与消费方**：

| 端口 | 谁来用 |
| --- | --- |
| `IRuntimeApprovalBroker` | infra 的 `RuntimeApprovalBroker`；`AgentFactory` 在 wire Agent 时通过 `MatrixRuntimeApprovalGate` 接入 |
| `AgentRunFactoryPort` | infra 的 `AgentFactory`；case 层 `AgentResolveNode` 通过它创建 / 复用 AgentRun |

**注意事项**：

- 状态机用 `AtomicBoolean` + 100ms 轮询，不适合超高频取消场景。
- 单 turn 上限 50 步；`max_tokens` 受控续写最多 4 次；`midTurnContinuation` 标志用于工具调用后跳过 inbox 检查（修过一个工具结果丢失的 Bug）。
- `ReactLoopAgent` 持有 `setStreamDeltaSink` / `setStreamToolCallSink` 用于 SSE 流式输出。

---

### 2.2 `agent.ask`

**职责**：把"向用户提问"建模成一个工具 + 一个端口，让 Agent 在需要用户确认 / 补充信息时调用。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `AskUserTool.java` | `name=ask_user_question` 工具定义（多选项 / 自由文本） |
| `UserQuestionsPort.java` | 用户问题对外端口（case 层实现弹窗推送） |

**与谁打交道**：`tool.service.ToolRegistry` 注册；`ReactLoopAgent` 通过 `ToolCallExecutor` 派发；前端 / 控制台弹窗由 trigger 层订阅。

---

### 2.3 `agent.compaction`

**职责**：上下文压缩。当事件日志超出阈值时由 LLM 总结，保留最近一段原文。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `CompactionEngine.java` | 压缩引擎接口 |
| `BasicCompactionEngine.java` | 默认实现：保留最近 1/4 事件，其余 LLM 总结 |
| `CompactionResult.java` | 压缩结果 VO |

**与谁打交道**：`ReactLoopAgent.runMaintenance(...)` 在每轮收尾时调用。

**注意事项**：压缩后 deriveMessages 全量重建，**长会话是 O(n²)**（已有隐患）。

---

### 2.4 `agent.subagent`

**职责**：子 Agent 注册表与启动方式抽象。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `SubagentRegistry.java` | 子 Agent 注册表（按 id 查定义） |
| `SubagentProvider.java` | 启动抽象（提供 fork / spawn 能力） |
| `ForkInProcessProvider.java` | fork：复用父 Agent 的会话日志，独立 turn |
| `SpawnInProcessProvider.java` | spawn：全新子 Agent 跑一次性任务 |
| `SubagentTool.java` | 由 Agent 调用的工具入口 |

**与谁打交道**：`ReactLoopAgent` 在收到 `SpawnInProcessProvider` 调用时构造子 Agent。

---

## 3. 任务 / 目标 / 规划

### 3.1 `task`（顶级）——五个子域

任务域是 `task` 下五个独立子域的"协调门面"，并在顶级放入口接口。

**顶级接口**：

| 文件 | 角色 |
| --- | --- |
| `IHarnessExecutionService.java` | 执行会话入口（供 trigger 调用） |
| `IHarnessTaskQueueService.java` | 任务队列入队 / 查询 |
| `ITaskSubmissionPolicyService.java` | 提交参数过滤链入口 |
| `IPermissionPolicyService.java` | 权限评估入口 |
| `IApprovalPolicyService.java` | 审批策略入口 |
| `IApprovalCommandService.java` | 审批命令（approve / reject） |
| `IApprovalQueryService.java` | 审批查询 |

**子域汇总**：

| 子域 | 职责 | 关键对象 |
| --- | --- | --- |
| `task.submission` | 入参过滤 | `TaskSubmissionVO`、`TaskSubmissionFilter`、`ProfileCodeFilter`、`PromptNotBlankFilter`、`ToolSelectionFilter` |
| `task.permission` | 权限矩阵评估 | `PermissionAssessmentVO`、`PermissionMatrixVO`、`PermissionPolicyService`、`IPermissionMatrixPort` |
| `task.approval` | 审批决策 | `ApprovalDecisionVO`、`ApprovalActionVO`、`ApprovalPolicyVO`、`PendingApprovalVO`、`ApprovalPolicyService`、`ApprovalCommandService`、`ApprovalQueryService` |
| `task.queue` | 任务队列持久化 | `HarnessTaskEntity`、`HarnessTaskQueueService`、`IHarnessTaskQueueRepository`、`QueuedTaskResultVO` |
| `task.execution` | 会话执行 | `HarnessExecutionService`、`IModelExecutionPort`、`RuntimeExecutionPlanVO`、`HarnessExecutionResultVO`、`ModelExecutionResultVO` |

**关键链路**：

```
Trigger → SubmitHarnessTaskCaseImpl
       → SubmissionRootNode → ProfileResolutionNode → PermissionCheckNode → ToolResolutionNode → EnqueueNode
       → [需审批] PENDING_APPROVAL → POST /approvals/{sessionId}/approve → QUEUED → executeSession → RUNNING
       → [免审批] 直接 executeSession → RUNNING
```

**注意事项**：执行入口对 `PENDING_APPROVAL` 状态直接抛异常，防止绕过审批兜底。

---

### 3.2 `goal`

**职责**：目标（goal）聚合的创建 / 推进 / 查询。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `service/GoalService.java` | 目标服务实现（创建 / 推进） |
| `IGoalCommandService.java` / `IGoalQueryService.java` | 命令 / 查询接口 |
| `model/entity/GoalAggregate.java` | 聚合根 |
| `model/valobj/GoalSnapshotVO.java` / `GoalCommandVO.java` | 快照 / 命令 VO |
| `model/valobj/GoalAction.java` / `GoalStatus.java` | 枚举 |
| `adapter/repository/IGoalRepository.java` | 仓储端口 |

**与谁打交道**：case 层 `Submit*` / `Approve*` 节点；infra `GoalRepository`（MyBatis）。

---

### 3.3 `plan`

**职责**：计划模式（Claude-like plan mode）：进入计划、退出计划。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `service/PlanModeService.java` | 模式切换实现 |
| `ExitPlanModeTool.java` | Agent 调用的"退出计划模式"工具 |
| `model/valobj/PlanModeState.java` | 模式状态枚举 |

**与谁打交道**：trigger `/api/agent/plan` 类接口；agent 在 SYSTEM_PROMPT 中感知当前模式。

---

### 3.4 `todo`

**职责**：待办列表（Agent 用来分解任务）。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `TodoWriteTool.java` | Agent 调用的"写入 todo"工具 |
| `service/TodoWriteService.java` | 内部实现 |
| `model/valobj/TodoItem.java` / `TodoWriteResultVO.java` | 值对象 |

---

## 4. 会话与事件日志

### 4.1 `session`（顶级）

会话域是 `session.registry` / `session.event` / `session.log` / `session.conversation` 的协调门面。

**顶级接口**：

| 文件 | 角色 |
| --- | --- |
| `IHarnessSessionRegistryService.java` | 会话注册 / 打开 / 关闭入口 |
| `ISessionLogService.java` | 会话日志服务入口（兼容别名） |
| `IConversationQueryService.java` | 会话读模型查询入口 |

**子域汇总**：

| 子域 | 职责 | 关键对象 |
| --- | --- | --- |
| `session.registry` | 会话聚合根 | `HarnessSessionAggregate`、`HarnessSessionRegistryService`、`SessionOpenedResultVO`、`IHarnessSessionRepository` |
| `session.event` | Session 事件定义与投影 | `SessionEvent`（sealed）、`SessionHeader`、各 *Payload、`SurfaceOp`、`SurfaceIntent`、`SessionEventType`、`SurfaceProjector`、`SessionRebuilderService`、`SessionEventFactory`、`LegacyEventAdapter`、`ISessionEventStore` |
| `session.log` | 会话事件持久化（MySQL） | `SessionEventEntity`、`SessionLogService`、`ISessionEventRepository` |
| `session.conversation` | 读模型 VO | `ConversationSessionSummaryVO`、`ConversationSessionDetailVO`、`ConversationMessageVO`、`ConversationQueryService` |

**事件协议要点**：

- `SessionEvent` 是 sealed 接口，record 变体（`TurnStart/End`、`StepStart/End`、`User/AssistantMessage`、`AssistantChunk`、`ToolCall/ToolResult`、`TodoWrite`、`PlanModeChange`、`RequestHeader/Context`、`SessionEndSeed`、`InboxSpliced`）。
- `SessionEventType` 携带 `wireName`（SSE 序列化名）与 `ignorable` 标志。
- 旧任务事件（`TASK_*`）全部 `ignorable=true`，投影到 UI 时跳过。

---

### 4.2 `shared`

**职责**：跨域共享基础设施，**必看**。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `model/valobj/HarnessStatusEnumVO.java` | 全局统一的状态机：`CREATED → PENDING_APPROVAL → QUEUED → RUNNING → COMPLETED/FAILED` |
| `strategy/StrategyHandler.java` | 策略节点契约（`@FunctionalInterface`，三泛型 `I/C/O`） |
| `strategy/AbstractStrategyRouter.java` | 责任链抽象：`doApply` + `getNext` |

**与谁打交道**：`case` 层所有 `Abstract*Node` 都继承 `AbstractStrategyRouter`；trigger 借 `HarnessStatusEnumVO` 做统一过滤。

---

## 5. LLM 与工具

### 5.1 `llm`

**职责**：LLM 调用策略抽象，流式返回 `Flow.Publisher<StreamChunk>`。

**关键类**：

| 文件 | 角色 |
| --- | --- |
| `service/LlmGenerationService.java` | LLM 生成领域服务 |
| `service/BlockAssembler.java` | 把流式 chunk 装配成完整 `ContentBlock` |
| `adapter/LlmAdapter.java` | 适配器骨架（infra 提供具体实现） |
| `adapter/port/ILlmRuntimePort.java` | 核心端口：`stream(...)` 返回 `Flow.Publisher<StreamChunk>`；重试在该端口实现层而非 adapter |
| `adapter/ResolvedRetryPolicy.java` | 重试策略 |
| `model/valobj/StreamChunk.java` | sealed 流式块 |
| `model/valobj/FinishReason.java` / `TokenUsage.java` / `LlmErrorCodes.java` / `LlmFailure.java` | 枚举与结果对象 |
| `model/valobj/LlmCallConfig.java` / `GenerateOptions.java` | 调用配置（temperature 可空，null = 不发送） |

---

### 5.2 `tool`（顶级）—— 12 个工具族 + 执行核心

**顶级**：`tool/adapter/ToolRegistry.java` 自身就是所有工具的注册中心。

**执行核心（`tool/service/`）**：

| 文件 | 角色 |
| --- | --- |
| `ToolCallExecutor.java` | 工具派发器：参数解析 → `PRE_TOOL_USE` Hook → 执行 → `POST` Hook → 回填结果；并发上限由 `maxParallel` 控制 |
| `ToolRegistry.java` | 工具注册表（内置 + 插件 `plugin__<id>__<tool>` + MCP `mcp__<server>__<tool>`） |
| `ToolArgumentsParser.java` | 参数解析与校验 |
| `RuntimeApprovalGate.java` / `MatrixRuntimeApprovalGate.java` | 运行期审批 gate（默认 allowAll） |

**工具族（`tool/<family>/`）**：

| 子包 | 工具类 | 对外端口 / 端口实现 |
| --- | --- | --- |
| `tool.fs` | `FsReadTool`、`FsWriteTool`、`FsEditTool`、`FsGrepTool`、`FsSearchTool`、`FsReadImageTool`、`StrReplaceEditorTool` | `IFsPort` |
| `tool.shell` | `ShellExecuteTool`、`DangerousCommandGuard`（危险命令黑名单） | `IShellExecutor` |
| `tool.web` | `WebFetchTool`、`WebSearchTool` | `IWebPort` |
| `tool.mcp` | `McpToolAdapter`、`McpBootstrap`、`McpServerConfig`、`IMcpClient` | （MCP 客户端核心） |
| `tool.plugin` | `PluginToolBridgeService`、`IPluginToolBridge`、`PluginToolDefinition` | 桥接 `plugin.runtime` 暴露的工具定义 |
| `tool.skill` | `SkillTool` | 调用 `skill` 域 |
| `tool.jobs` | `JobRunTool`、`JobListTool`、`JobOutputTool`、`JobKillTool` | 调用 `jobs` 域 |
| `tool.terminal` | `TerminalTools` | 调用 `terminal` 域 |
| `tool.schedule` | `ScheduleTools` | 调用 `schedule` 域 |
| `tool.goal` | `GoalTools` | 调用 `goal` 域 |
| `tool.lsp` | `LspTool` | 调用 `lsp` 域 |
| `tool.session` | `SessionSearchTool` | 调用 `session.conversation` 域 |

**注意事项**：

- `shell_execute` 走 `/bin/sh -c` **零沙箱**，仅靠任务提交期审批兜底（已知隐患）。
- `DangerousCommandGuard` 是黑名单，不是白名单。
- 插件工具命名：`plugin__<pluginId>__<toolName>`；MCP 工具命名：`mcp__<server>__<tool>`。
- 工具执行超时由 `guard.ToolTimeoutPolicy` 协作控制（`orTimeout`）。

---

### 5.3 `runtime`（顶级 + 4 个子域）

运行时域"总入口" + 四个子域。

**顶级接口**：

| 文件 | 角色 |
| --- | --- |
| `IModelRoutingService.java` | 模型路由入口 |
| `IToolResolutionService.java` | 工具解析入口 |
| `IExecutionProfileService.java` | 执行画像入口 |

#### 5.3.1 `runtime.model` —— 模型路由

| 文件 | 角色 |
| --- | --- |
| `service/ModelRoutingService.java` | 模型路由实现：按渠道编码解析上游模型 |
| `adapter/port/IModelProviderPort.java` | 上游模型发现端口 |
| `model/model/valobj/ModelProviderProfileVO.java` | 提供方画像 |

#### 5.3.2 `runtime.setting` —— 模型设置（用户保存的偏好）

| 文件 | 角色 |
| --- | --- |
| `service/ModelSettingService.java` | 实现 |
| `IModelSettingCommandService.java` / `IModelSettingQueryService.java` | CQRS 接口 |
| `model/entity/ModelSettingEntity.java` / `valobj/ModelSettingVO.java` | 实体 / VO |
| `adapter/repository/IModelSettingRepository.java` | 仓储端口 |

#### 5.3.3 `runtime.tool` —— 工具目录

| 文件 | 角色 |
| --- | --- |
| `service/ToolResolutionService.java` | 工具解析：根据 enabledTools / profile 返回可选工具 |
| `adapter/port/IToolCatalogPort.java` | 工具目录端口（infra 提供 `ToolCatalogPort`） |
| `model/entity/ToolCatalogEntryEntity.java` / `valobj/ToolCatalogEntryVO.java` / `ToolSelectionVO.java` | 实体 / VO |

#### 5.3.4 `runtime.profile` —— 执行画像

| 文件 | 角色 |
| --- | --- |
| `service/ExecutionProfileService.java` | 选择 / 解析执行画像 |
| `service/router/ExecutionProfileStrategy.java` | 策略接口 |
| `service/router/HeadlessExecutionProfileStrategy.java` | headless 模式 |
| `service/router/WebExecutionProfileStrategy.java` | web 模式（带交互） |
| `model/valobj/ExecutionProfileVO.java` | 画像 VO |

---

## 6. 插件 / 桥接 / 运行时

### 6.1 `plugin`（顶级）

插件域"门面"，顶级只放接口根：

| 文件 | 角色 |
| --- | --- |
| `IPluginRegistryService.java` | 注册 / 安装入口 |
| `IPluginBridgeService.java` | 桥接识别入口 |
| `IPluginRuntimeService.java` | 启动 / 运行入口 |

### 6.2 `plugin.registry` —— 注册与安装

| 文件 | 角色 |
| --- | --- |
| `service/PluginRegistryService.java` | 注册 / 安装实现 |
| `model/entity/HarnessPluginEntity.java` | 插件实体 |
| `model/valobj/PluginArtifactInstallPlanVO.java` / `PluginInstallationResultVO.java` | 安装计划 / 结果 |
| `model/valobj/PluginInstallModeEnumVO.java` / `PluginInstallStatusEnumVO.java` / `PluginRuntimeTypeEnumVO.java` | 枚举 |
| `adapter/port/IPluginArtifactInstallerPort.java` | 安装器端口 |
| `adapter/repository/IHarnessPluginRepository.java` | 仓储端口 |

### 6.3 `plugin.bridge` —— 桥接与识别

| 文件 | 角色 |
| --- | --- |
| `service/PluginBridgeService.java` | 桥接实现：识别接入方式（`JAVA_NATIVE` / `DSH_NODE_BRIDGE` / `DSH_PACKAGE` / `CODEX` / `CORDIS`） |
| `model/valobj/PluginBridgePlanVO.java` / `PluginActivationResultVO.java` | 计划 / 结果 |
| `model/valobj/PluginPackageTypeEnumVO.java` | 包类型枚举 |
| `adapter/port/IPluginRuntimeBridgePort.java` | 桥接端口 |
| `adapter/repository/IHarnessPluginRuntimeBindingRepository.java` | 绑定仓储端口 |

**注意**：`DSH_PACKAGE` / `CODEX` / `CORDIS` 识别后 `runnable=false` 死路径（保留扩展点）。

### 6.4 `plugin.runtime` —— 启动与运行

| 文件 | 角色 |
| --- | --- |
| `service/PluginRuntimeService.java` | 启动 / 运行 / 停止实现 |
| `model/valobj/PluginProcessStatusVO.java` / `PluginRunResultVO.java` | 状态 / 结果 |
| `adapter/port/IPluginProcessPort.java` | 进程端口 |

**运行时隔离**：

| 接入方式 | 隔离方式 |
| --- | --- |
| `JAVA_NATIVE` | 进程内 `URLClassLoader` 隔离 |
| `DSH_NODE_BRIDGE` | Node sidecar（`node <entrypoint>`，JSON-RPC stdio，入口限 `installRoot` 内 `.js`/`.mjs`/`.cjs`） |
| `MCP` | `StdioMcpClient` + `McpToolAdapter` |

---

## 7. 治理 / Hooks / Jobs

### 7.1 `guard` —— 运行守卫

**职责**：在工具执行前后做守卫（超时、重复调用提醒等）。

| 文件 | 角色 |
| --- | --- |
| `service/GuardService.java` / `IGuardService.java` | 服务入口 |
| `timeout/ToolTimeoutPolicy.java` | 工具级超时策略（与 `orTimeout` 协作） |
| `reminder/RepeatToolReminderEngine.java` | 重复调用提醒（如连续调用 `ls`） |
| `model/valobj/GuardDecision.java` / `ToolTimeoutResult.java` | 结果 VO |

### 7.2 `hooks` —— 生命周期 Hook

| 文件 | 角色 |
| --- | --- |
| `service/HookService.java` / `IHookService.java` | 服务入口 |
| `service/HookMatcher.java` | 匹配器：按工具名 / matcher group 匹配 hook |
| `service/HookOutputMerger.java` | 多 hook 输出合并 |
| `adapter/port/IHookRunnerPort.java` | 执行端口（infra 提供本地或远端运行器） |
| `model/valobj/HookPoint.java` / `CommandHook.java` / `MatcherGroup.java` / `HookDialect.java` / `HookOutput.java` / `MergedHookOutcome.java` | 枚举与 VO |

**调用点**：工具执行前的 `PRE_TOOL_USE`、执行后的 `POST_TOOL_USE`。

### 7.3 `jobs` —— 后台任务

| 文件 | 角色 |
| --- | --- |
| `service/JobRegistryService.java` / `IJobRegistryService.java` | 任务注册表实现 / 接口 |
| `model/entity/JobRecord.java` / `JobStart.java` | 实体 |
| `model/valobj/JobStatus.java` / `JobKind.java` / `JobSnapshot.java` / `JobOutcome.java` | 枚举与 VO |
| `adapter/repository/IJobRegistryRepository.java` | 仓储端口 |

**对外面向工具**：`tool.jobs` 提供 `JobRun/List/Output/Kill` 四个工具。

**注意事项**：仓储**当前为 InMemory**，重启即丢（已知隐患）。

---

## 8. 运行时 / 平台能力

### 8.1 `coderuntime`

| 文件 | 角色 |
| --- | --- |
| `service/CodeRuntimeService.java` / `ICodeRuntimeService.java` | 代码运行时服务 |
| `adapter/port/ICodeRuntimePort.java` | 执行端口 |
| `model/valobj/CodeBindNamespace.java` / `CodeRunRequest.java` / `CodeRunResult.java` | 命名空间 / 请求 / 结果 |

### 8.2 `e2b`

| 文件 | 角色 |
| --- | --- |
| `service/E2BSandboxService.java` / `IE2BSandboxService.java` | 沙箱服务 |
| `adapter/port/IE2BSubprocessPort.java` / `IE2BFsPort.java` | 子进程 / 文件系统端口 |
| `model/valobj/E2BSandboxEnv.java` / `E2BExecResult.java` | 环境 / 执行结果 |

**注意事项**：当前**只有 Stub 实现**（已知隐患第 6 条）。

### 8.3 `credentials`

| 文件 | 角色 |
| --- | --- |
| `service/CredentialService.java` / `ICredentialService.java` | 凭据服务（解析 / 提供） |
| `adapter/port/ICredentialProviderPort.java` | 提供方端口（明文 yml / KMS / vault 等由 infra 实现） |
| `model/valobj/CredentialRef.java` / `CredentialInfoVO.java` / `ResolvedCredentialVO.java` | 引用 / 信息 / 解析结果 |

### 8.4 `lsp` —— 语言服务

| 文件 | 角色 |
| --- | --- |
| `service/LspService.java` / `ILspService.java` | LSP 服务 |
| `adapter/port/ILspProviderPort.java` | 提供方端口 |
| `model/valobj/LspQueryType.java` / `LspLocation.java` / `LspQueryResult.java` | 查询类型 / 位置 / 结果 |

**注意**：与 `tool.lsp.LspTool` 是两个层级：域 `lsp/` 是能力抽象；`tool/lsp/` 是把能力包成工具给 Agent 调。

### 8.5 `sandbox` —— 沙箱策略

| 文件 | 角色 |
| --- | --- |
| `service/SandboxPolicyService.java` / `ISandboxPolicyService.java` | 沙箱策略服务 |
| `model/valobj/SandboxMode.java` / `SandboxPolicyRequest.java` / `SandboxExecutionPolicy.java` | 模式 / 请求 / 执行策略 |

**机制**：默认模式 + fallback workspace root；每次工具执行都 `resolve` 一份 `SandboxExecutionPolicy`，让 session 模式 + 不可变 cwd 一起送到所有强制能力。

### 8.6 `schedule` —— 调度任务

| 文件 | 角色 |
| --- | --- |
| `service/ScheduleService.java` / `IScheduleService.java` | 调度服务 |
| `model/entity/ScheduleRecord.java` | 实体 |
| `model/valobj/ScheduleKind.java` / `ScheduleState.java` / `ScheduleView.java` | 枚举与 VO |
| `adapter/repository/IScheduleRepository.java` | 仓储端口 |

**注意事项**：`ScheduleService.drive()` **当前无调用方**（无 `@Scheduled`/Quartz），仓储也是 InMemory。**形同虚设**（已知隐患第 3 条）。

### 8.7 `terminal` —— 终端

| 文件 | 角色 |
| --- | --- |
| `service/TerminalService.java` / `ITerminalCommandService.java` / `ITerminalQueryService.java` | 命令 / 查询 |
| `adapter/port/ITerminalPort.java` | 终端端口 |
| `model/valobj/TerminalSessionSnapshotVO.java` / `TerminalSendResultVO.java` / `TerminalReadResultVO.java` / `TerminalSessionStatus.java` | 会话快照 / 发送结果 / 读取结果 / 状态 |

### 8.8 `storage` —— KV 存储抽象

| 文件 | 角色 |
| --- | --- |
| `service/StorageService.java` / `IStorageService.java` | 存储服务 |
| `adapter/port/IStorageBackendPort.java` | 后端端口 |
| `model/valobj/KvSnapshot.java` / `KvUnitDescriptor.java` / `StorageErrorCode.java` / `StorageException.java` | 快照 / 描述符 / 错误码 / 异常 |

### 8.9 `typert` —— 类型注册协议

| 文件 | 角色 |
| --- | --- |
| `service/TypertService.java` / `ITypertService.java` | 服务 |
| `adapter/port/ITypertRegistryContract.java` / `ITypertLocalRegistry.java` / `ITypertRemoteRegistry.java` / `ITypertLookupRegistry.java` / `ITypertContextRegistry.java` | 注册协议家族 |
| `model/entity/TypertContribution.java` / `InvocationDescriptor.java` | 实体 |
| `model/valobj/TypertFace.java` / `RemoteResult.java` / `RemoteFailure.java` | VO |

### 8.10 `skill` —— 技能系统

| 文件 | 角色 |
| --- | --- |
| `service/SkillService.java` / `ISkillService.java` | 服务 |
| `adapter/port/ISkillProviderPort.java` | 提供方端口（支持多源） |
| `model/entity/SkillDefinition.java` / `SkillSummary.java` / `SkillCandidate.java` | 实体 |
| `model/valobj/SkillSource.java` / `SkillInvocationPolicy.java` | 来源 / 调用策略 |

---

## 9. 协议 / 客户端 / 工作流

### 9.1 `acp` —— ACP 协议服务端

**职责**：把 Agent 暴露为 ACP（Agent Client Protocol）服务端，让外部编辑器 / IDE 通过标准协议接入。

| 文件 | 角色 |
| --- | --- |
| `service/AcpServerService.java` / `IAcpServerService.java` | 服务实现 / 接口 |
| `adapter/port/IAcpAgentPort.java` | 协议侧 → Agent 适配端口（infra 实现，绑定到 HarnessExecutionService + ReactLoopAgent） |
| `model/valobj/AcpSessionInfo.java` / `AcpMessage.java` / `AcpPromptRequest.java` / `AcpPromptResponse.java` / `AcpStopReason.java` | 协议消息 VO |

### 9.2 `sdk` —— 对外 SDK

**职责**：统一 SDK 入口（客户端 + 服务端），向上层暴露 stdio / HTTP / WebSocket 等 transport 中立接口。

| 文件 | 角色 |
| --- | --- |
| `service/SdkClientService.java` / `ISdkClientService.java` | 客户端 SDK |
| `service/SdkServerService.java` / `ISdkServerService.java` | 服务端 SDK |
| `adapter/port/ISdkTransportPort.java` | 传输端口（infra 提供 jsonrpc / http / ws 实现） |
| `adapter/port/ISdkServerHandler.java` | 服务端处理器端口（infra 把它绑定到 ReactLoopAgent / HarnessExecutionService） |
| `model/valobj/InitializeParams.java` / `InitializeResult.java` / `SessionPromptParams.java` / `SessionPromptResult.java` / `SessionStatus.java` / `NotificationType.java` | 协议消息 VO |

### 9.3 `workflow` —— 工作流

| 文件 | 角色 |
| --- | --- |
| `service/WorkflowService.java` / `IWorkflowService.java` | 工作流服务 |
| `adapter/port/IWorkflowEnginePort.java` | 引擎端口（infra `LocalWorkflowEnginePort`） |
| `model/entity/WorkflowRun.java` / `WorkflowStartRequest.java` | 实体 |
| `model/valobj/WorkflowMeta.java` / `WorkflowResult.java` / `WorkflowPhase.java` / `WorkflowStopReason.java` | VO |

**链路**：`IWorkflowService.start(req)` → `IWorkflowEnginePort.execute` → `WorkflowRun`（`CompletableFuture<WorkflowResult>`）→ **流式复用** `ReactLoopAgent` 的 deltaSink / toolCallSink 推送 SSE。

**注意事项**：当前 SSE 只推**终态**事件，无增量（已知隐患第 7 条）。

---

## 10. 模块依赖关系（领域内）

```
                          shared  ←——— 所有模块共用（策略路由 + 统一状态）
                             ▲
           ┌─────────────────┼─────────────────────────┐
           │                 │                         │
        agent ←———————→ session.event                 tool.service
           │    （event log / 事件流）                  ▲   ▲   ▲
           │                                            │   │   │
           ├→ llm                                         │   │   │
           ├→ runtime.model / runtime.tool               │   │   │
           ├→ hook                                         │   │   │
           ├→ guard                                        │   │   │
           ├→ task.approval/task.permission/task.queue    │   │   │
           │    ↑                                          │   │   │
           │ task.submission                               │   │   │
           ├→ agent.ask / agent.compaction / agent.subagent│  │   │
           │                                                │  │   │
           └→ plugin.registry ─→ plugin.bridge ─→ plugin.runtime ─→ tool.plugin
                                                                          │
       runtime.profile ─→ ExecutionProfileStrategy（headless / web）        │
                                                                          │
       跨域调用（工具族）：tool.{fs,shell,web,mcp,plugin,skill,jobs,
                                terminal,schedule,goal,lsp,session}
              → 对应 domain（fs→tool.fs.port，shell→tool.shell.port 等等）
```

简化版的核心循环：

```
trigger.submitMessage
  → case.AgentUseCase (AgentResolve → AgentIntent → AgentDispatch → AgentCollect)
     → agent.ReactLoopAgent (service)
        → llm.ILlmRuntimePort.stream    （产生 AssistantChunk 事件 → session.event）
        → tool.service.ToolCallExecutor  （参数解析 → guards/hooks → 执行 → 回填 ToolCall/ToolResult）
           → tool.<family>.tool          （fs/shell/web/...）
              → <family>.adapter.port    （infra 实现）
```

两条并行主线：

- **任务主线**：`trigger.submitTask` → `task.submission` 过滤 → `task.permission` 评估 → `task.approval` 决策 → `task.queue` 入队 → 触发 `task.execution` → 与 Agent 主线合流。
- **审批主线**：`POST /api/harness/approvals/{id}/approve` → `task.approval.command` → 状态落到 `QUEUED` → `task.execution.executeSession`。

---

## 11. 与已知隐患的对应

| 模块 | 已知问题 |
| --- | --- |
| `agent` | 运行期审批 gate 默认未启用；`midTurnContinuation` 是 09-05 修复；`kick()` 用 `newCachedThreadPool` 无限界 |
| `tool.shell` | 零沙箱；只靠 `DangerousCommandGuard` 黑名单 + 任务提交期审批 |
| `schedule` | `drive()` 无调用方；仓储 InMemory，重启即丢 |
| `jobs` | 仓储 InMemory，重启即丢；用 `newCachedThreadPool` |
| `tool.service.ToolCallExecutor` | `AgentCollectNode` 用 `.join()` 阻塞请求线程 |
| `agent.compaction` | 全量重建 O(n²)；token 预算 `length/4` 粗估 |
| `e2b` | 只有 Stub |
| `plugin.bridge` | `CODEX`/`CORDIS` 识别后 `runnable=false` 死路径 |
| `tool.mcp` 与 `plugin.bridge` | 互不打通 |
| `workflow` | SSE 只推终态事件 |

---

## 12. 速查：找端口找类

| 我想找… | 看这里 |
| --- | --- |
| Agent 主入口 | `agent.service.ReactLoopAgent` |
| AgentRun 组合根 | `agentrun.AgentRunFactoryPort`（infra `AgentFactory` 实现） |
| LLM 流式接口 | `llm.adapter.port.ILlmRuntimePort.stream(...)` |
| 工具执行 | `tool.service.ToolCallExecutor` |
| 工具注册表 | `tool.adapter.ToolRegistry` |
| 运行期审批 | `agent.adapter.port.IRuntimeApprovalBroker` + `tool.service.MatrixRuntimeApprovalGate` |
| 任务提交主入口 | `task.IHarnessExecutionService` / `task.submission` 五 Filter |
| 权限评估 | `task.permission.PermissionPolicyService.assess` |
| 审批决策 | `task.approval.ApprovalPolicyService.decide` |
| 会话事件写 | `session.event.SessionEventFactory` + `session.log.SessionLogService` |
| 会话事件读 | `session.event.SessionRebuilderService` + `session.conversation.ConversationQueryService` |
| 模型目录 / 路由 | `runtime.model.service.ModelRoutingService` + `IModelProviderPort` |
| 工具目录 | `runtime.tool.service.ToolResolutionService` + `IToolCatalogPort` |
| 模型设置 | `runtime.setting.ModelSettingService` + `IModelSettingRepository` |
| 执行画像 | `runtime.profile.service.ExecutionProfileService` |
| 沙箱策略 | `sandbox.service.SandboxPolicyService.resolve` |
| 插件安装 | `plugin.registry.PluginRegistryService.installPlugin` |
| 插件桥接 | `plugin.bridge.PluginBridgeService.inspect` |
| 插件启动 | `plugin.runtime.PluginRuntimeService.start` |
| 工作流入口 | `workflow.WorkflowService.start` |
| ACP / SDK 服务 | `acp.AcpServerService` / `sdk.SdkServerService` |
| 策略树抽象 | `shared.strategy.AbstractStrategyRouter` |
| 统一状态枚举 | `shared.model.valobj.HarnessStatusEnumVO` |

---

**附：本次章节与既有文档的关系**

- `domain-design.md`：写"为什么这样分 + 怎么流"，**事件风暴全景 / 限界上下文 / 核心聚合 / 领域事件机制 / 策略树 / 端口-适配器对照**。
- `domain-case-deep-dive.md`：写"具体代码关键节点详解"，**策略树基础设施 / ReactLoopAgent 状态机 / Inbox 与回填 / 任务提交链 / 审批恢复链**。
- **本文**：写"每个模块是干什么的"，**逐个 37 个模块给出用途 / 子包 / 关键类 / 端口 / 关系 / 注意事项**。

> 三份文档优先级：先读 `domain-design.md` 建立结构感 → 读 `domain-case-deep-dive.md` 看代码细节 → 写代码时回来查本文档定位类与端口。
