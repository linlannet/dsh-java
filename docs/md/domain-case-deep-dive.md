# Domain & Case 模块深度解读

> 本文档面向 dsh-java 的维护者和深入学习者，聚焦课程里**覆盖不全**的核心实现：
> 领域层的执行引擎、事件日志、状态机，以及用例层的策略树（责任链）编排模式。
> 所有结论均来自直接阅读源码，重点章节都标注了源文件路径与关键行号。

---

## 1. 模块边界与依赖关系

```
api  ←  trigger  ←  case  →  domain  ←  infrastructure
                                   ↑
                                 types
```

| 模块        | 类数  | 角色                                                         |
| ----------- | ----- | ------------------------------------------------------------ |
| `api`       | ~7    | DTO、接口契约、纯模型                                        |
| `trigger`   | ~5    | Spring MVC Controller（HTTP/SSE 入口）                       |
| `case`      | 45    | **用例编排层**：策略树 + 工厂 + 动态上下文                   |
| `domain`    | 344   | **领域核心**：模型、策略、引擎、端口                         |
| `infrastructure` | — | MyBatis DAO、HTTP 客户端、Sidecar 进程、配置适配            |
| `types`     | ~5    | 跨模块公共枚举/常量                                          |

**关键约束**：

1. `case` 只依赖 `domain` 与 `api`，**绝不能** `import` 任何 `infrastructure` 类（这是六边形架构的依赖方向）。
2. `domain` 不依赖 Spring 容器（只有少量 `@Service` 注解用于代理管理；运行时不强制）。
3. 任何外部能力（LLM HTTP、文件系统、Shell）必须经 `domain/adapter/port` 暴露的端口访问，由 `infrastructure` 实现。

---

## 2. Domain 核心：策略树基础设施

### 2.1 `StrategyHandler<I, C, O>` —— 链式节点契约

源：`domain/shared/strategy/StrategyHandler.java`

```java
@FunctionalInterface
public interface StrategyHandler<I, C, O> {
    O apply(I requestParameter, C dynamicContext) throws Exception;
}
```

三个泛型：
- `I`：入参（不变，沿链共享）
- `C`：动态上下文（可变，节点间通过它通信）
- `O`：返回结果（任一节点返回非 null 即短路返回）

### 2.2 `AbstractStrategyRouter<I, C, O>` —— 责任链骨架

源：`domain/shared/strategy/AbstractStrategyRouter.java`

模板方法：子类只实现 `doApply` 与 `getNext`。

```java
public final O apply(I req, C ctx) throws Exception {
    O result = doApply(req, ctx);
    if (result != null) return result;      // 短路返回（错误、未匹配）
    return router(req, ctx);                // 否则路由到下一节点
}
```

- `defaultStrategyHandler` 默认是 `(req, ctx) -> null`，作为叶子节点的兜底。
- 该抽象是 `cases` 中所有节点（`AbstractAgentMessageNode`、`AbstractSubmitTaskNode`）的父类。

> **设计意图**：把"业务流程"拆成"一段段不相关的单元"，每段只关心自己的输入和上下文，把"决策下一步是谁"的关注点显性化为 `getNext()`。比 `if-else` 链 / 状态机表更直白，比责任链更灵活（节点可以持有 Spring Bean）。

---

## 3. Domain 核心：执行引擎 `ReactLoopAgent`

源：`domain/agent/service/ReactLoopAgent.java`（约 770 行）

这是整个 Agent 的**唯一驱动器**，相当于 Claude Code / Anthropic SDK 里的 `AgentLoop`。每个 `ReactLoopAgent` 实例对应一个会话。

### 3.1 状态机：`Phase` sealed 接口

源：`domain/agent/model/entity/Phase.java`

```
           wakeRequested
   Idle ─────────────────► Running ─────► Maintenance
     ▲                       │                   │
     │                       │       runMaintenance 完成
     └───────────────────────┴───────────────────┘
                  任意阶段完成 → Idle
```

三种状态：
- `Idle(lastTurn)` —— 空闲，记录已完成的最大 turn 号。
- `Running(abort, turn, step, wakeRequested)` —— 驱动器正在跑。
- `Maintenance(abort, lastTurn, wakeRequested)` —— 非 turn 的维护任务。

`abort` 是 `AtomicBoolean`，每个 step/turn 检查；`wakeRequested` 用于"中止期间收到新输入"的延迟唤醒。

### 3.2 Inbox 双队列

源：`domain/agent/service/Inbox.java`

`Inbox` 拥有两个有序列表：
- `nextTurn` —— 等一个完整 turn 的消息（用户输入）
- `nextStep` —— 等下一步的注入（如计划模式提示）

每次变更（追加/插入/删除）都会先持久化一条 `agent/inbox/spliced` 会话事件，**然后**才更新内存视图。这保证了"先有持久化、再有状态变化"的因果序，回放/恢复永远一致。

关键 API：
- `claim(target, turn)` —— 抢占下一批消息；纯删除（不会记录新的 insert）。
- `append(target, message)` —— 追加，durable + live 一起写。
- `replace(messageId, newMsg)` —— 同位替换，发布 `discarded` + `inserted` 两个通知。

### 3.3 turn 循环：核心算法

源：`ReactLoopAgent.turn()`（约 280 行起）

每个 turn 的执行流程：

```
open turn
loop (step) {
  if step >= 50        →  TurnEndReason.Error(MAX_STEPS)         return false
  if abort             →  TurnEndReason.Aborted                  return false
  if first step        →  inbox.claim()   // 取用户消息
  execute step()       // LLM 流式 + 工具调用
  if stepResult == null   →  midTurnContinuation = true, continue   // 工具后继续
  if Completed         →  return true
  if MaxTokens         →  触发受控续写，最多 4 次
  if Blocked/Aborted/Error →  return false
}
```

`step()` 是单步执行（关键代码 404–576 行）：

```
1. systemPromptAssembler.assemble(...)   // 系统提示词（异步 join）
2. buildRequest(history)                // 从 SessionLog.deriveMessages() 重建上下文，按 budget 截断
3. 记录 RequestHeader / RequestContext 事件
4. BlockAssembler + subscriber:
     推 TextDelta → streamDeltaSink.accept()  →  SSE 前端增量更新
     推 Retry      →  sink.accept("重试 ...") 
     推 BlockEnd   →  组装 ContentBlock
5. 检查 FinishReason
   - Error      →  追加可见错误消息 + 返回 TurnEndReason.Error
   - MaxTokens  →  返回 TurnEndReason.MaxTokens 触发续写
   - Aborted    →  返回 TurnEndReason.Aborted
   - Stop       →  返回 TurnEndReason.Completed
6. 收集 ToolCallBlock 列表
   - 若空 →  Completed
   - 若非空 → streamToolCallSink.accept()（推 step_break 给 SSE）→ executor.execute()
            →  return null（让 turn 进入下一步）
```

### 3.4 关键技巧

1. **`midTurnContinuation` 标志**：工具执行后不能让 `inbox.hasPending()` 误判为空导致 turn 退出。修过的 bug（见 `MEMORY.md` #1）。
2. **续写 `continuationRequested`**：模型因 max_tokens 截断时，`buildRequest()` 会注入一条用户消息：
   ```
   The previous assistant response was cut off by the output token limit.
   Continue from the exact next character without repeating completed text...
   ```
   最多重试 4 次。
3. **`streamDeltaSink` 与 `streamToolCallSink`**：都是 `volatile Consumer`，由 SSE controller 按请求注入，结束后清空（`AgentUseCase.sendMessageStreaming()` 的 try-finally）。
4. **`temperature` 可配置**：`harness.agent.temperature` 系统属性或 `HARNESS_AGENT_TEMPERATURE` 环境变量；null/空 = 不发送 temperature（适配只接受 1 的网关）。
5. **token 预算粗估**：`(int) Math.ceil(text.length() / 4.0)`；`budget = 128_000 - systemTokens - maxTokens - 1024`；最低保留 4096；保留最少 6 条历史消息（避免完全丢失上下文）。

---

## 4. Domain 核心：会话事件日志 `SessionLog`

源：`domain/session/event/model/entity/SessionLog.java`

### 4.1 单一职责

- 维护一个会话的有序事件序列（带全局单调 seq 0、1、2、…）。
- 提供三种**投影**（派生视图）：

| 投影                | 方法                | 用途                                 |
| ------------------- | ------------------- | ------------------------------------ |
| 消息历史            | `deriveMessages()`  | 给 LLM 用：user / assistant / tool   |
| 请求头（折叠）      | `requestHeader()`   | 比较 system prompt 是否变化          |
| 路由上下文（折叠）  | `requestContext()`  | 当前 provider / model / context_window |

### 4.2 `SurfaceOp` —— 可变对话视图的真相

源：`domain/session/event/model/valobj/SurfaceOp.java`

```java
sealed interface SurfaceOp permits SurfaceOp.Append, SurfaceOp.Replace {
    record Append() implements SurfaceOp {}
    record Replace(long start, long end) implements SurfaceOp { /* 校验非负、end >= start */ }
}
```

**核心思想**：事件日志是**追加写**的，但对话视图是**可替换**的。

- `Append`：事件对应的内容追加到对话末尾。
- `Replace(start, end)`：把 seq 在 `[start, end]` 区间内的可见节点全部"遮蔽"，用本事件的内容替代。

**应用场景**：
1. 压缩引擎（`BasicCompactionEngine`）—— 用一条摘要消息 `Replace` 覆盖被压缩的 N 条历史。
2. 修正 / 重写某条 assistant 消息而不丢历史。

### 4.3 `SurfaceProjector`

源：`domain/session/event/service/SurfaceProjector.java`

`project(events)`：从事件日志产出有序的 `SurfaceNode`，遵循 `SurfaceOp` 语义。`extractChatMessages` 进一步过滤出 user + assistant 用于纯聊天展示。

### 4.4 `deriveMessages` 的简易投影

为简化实现，Java 版每次都**全量重算**（TS 版用增量缓存）。对中小规模会话成本可接受；超大上下文时是 `O(n²)` 隐患（`MEMORY.md` 已知）。

---

## 5. Domain 核心：流式组装器 `BlockAssembler`

源：`domain/llm/service/BlockAssembler.java`

适配器只发 `StreamChunk`，业务需要把增量拼回 `ContentBlock`。`BlockAssembler` 是核心粘合层。

`StreamChunk` 的 sealed 类型：

| Chunk               | 作用                                                         |
| ------------------- | ------------------------------------------------------------ |
| `BlockStart(idx, type)` | 通知某 idx 处开始一个新块（text/reasoning/tool-call）     |
| `TextDelta(idx, text)`  | 文本增量                                                  |
| `ReasoningDelta(idx, text)` | 推理内容（如 OpenAI o1 的 thinking）                    |
| `ToolCallDelta(idx, id, name, argsDelta)` | 工具调用的 JSON 增量               |
| `BlockEnd(idx, block)`  | 块结束，携带完整块（可选；流式协议不强求）              |
| `Usage(TokenUsage)`    | token 计数（必须在 Finish 之前）                        |
| `Finish(FinishReason)`  | 终止信号                                                |
| `Retry(attempt, max, msg)` | 重试提示（被 ReactLoopAgent 改写成增量文本）         |

**关键容错**：
- 支持 delta-only 协议（没有 BlockStart/End）。
- `block-end` 第一次生效，重复忽略。
- `FinishReason.MaxTokens` 时**丢弃所有 `ToolCallBlock`**（半截参数不安全执行）。

---

## 6. Domain 核心：工具执行器 `ToolCallExecutor`

源：`domain/tool/service/ToolCallExecutor.java`

`execute()` 是单个 step 内多工具调用的总调度入口。

### 6.1 分组策略

```java
while (next < planned.size()) {
    PlannedCall first = planned.get(next);
    ToolExecutionMode mode = registry.executionMode(first);
    List<PlannedCall> group = mode == PARALLEL
            ? planned.subList(next, planned.size())  // 并行组：剩余全部
            : List.of(first);                         // 串行组：只跑这一个
    GroupOutcome outcome = runGroup(group, mode, abort, accept);
    next += outcome.consumed;
}
```

并行模式会在每次提交新调用前**重新检查**后续调用的 `executionMode`，遇到必须串行的工具就停下来。

### 6.2 调度循环（`runGroup`）

```
while (nextToStart < groupSize && inFlight.size() < maxParallel && !aborted) {
    PRE_TOOL_USE Hook 拦截 →  写 HOOK_BLOCKED 失败
    approvalGate.check() 拒绝 →  写 APPROVAL_REQUIRED 失败
    append tool/call 事件 →  记录 seq
    CompletableFuture<ToolExecutionResult> = def.execute(args, ctx)
    inFlight.put(index, tracked)
    if mode != PARALLEL → future.join()  // 串行：阻塞等结果
    commitReady()   // 提交所有已就绪槽位
}
join 等待全部在途完成 → 补流（同样路径）→  commitReady
aborted → 对未开始的调用 appendSkippedToolCall（写入 ABORTED_BEFORE_DISPATCH 错误）
```

### 6.3 持久化与可重放

**铁律**：先写 `tool/call` 事件，再执行；执行结果出来后写 `tool/result`。两者通过 `callSeq` 关联。

- 若执行被中止、未开始的调用 → 写 `tool/call` + 写合成 `tool/result`（带 `ABORTED_BEFORE_DISPATCH` 错误），**保证回放一致**。
- 若整个 `runGroup` 抛异常（调度器失败）→ **不补写结果**，保留已记录的 `tool/call`，让上层感知调度失败。

### 6.4 hook 与审批

- `IHookService` 走 Claude Code 风格方言：触发 `PRE_TOOL_USE`（BLOCK/DENY → 拒绝）/ `POST_TOOL_USE`（通知）。
- `RuntimeApprovalGate` 是一个 SPI 接口；`MatrixRuntimeApprovalGate` 是默认实现，把决策委托给 `permissionMatrixPort` + `approvalBroker.requestApproval()`，阻塞等待人工裁决。

---

## 7. Domain 核心：审批链路

| 角色                | 接口                                          | 默认实现                    |
| ------------------- | --------------------------------------------- | --------------------------- |
| 权限矩阵            | `IPermissionMatrixPort`                       | DB / 配置加载               |
| 审批策略            | `IApprovalPolicyPort`                         | DB / 配置加载               |
| 权限评估            | `IPermissionPolicyService`                    | `PermissionPolicyService`   |
| 审批决策            | `IApprovalPolicyService`                      | `ApprovalPolicyService`     |
| 提交校验链          | `List<TaskSubmissionFilter>`                  | profile / prompt / tools    |
| 运行期审批 broker   | `IRuntimeApprovalBroker`（在 domain/agent/adapter） | `RuntimeApprovalBroker`（infrastructure） |
| 运行期审批 gate     | `RuntimeApprovalGate`                         | `MatrixRuntimeApprovalGate` |

提交期（在 `SubmitHarnessTaskCaseImpl` 中）：
1. `TaskSubmissionPolicyService.validate()` — 过滤链（profile 合法、prompt 非空、工具选择合法）。
2. `PermissionPolicyService.assess()` — 拆 `enabledTools` 为 `gated` 与 `autoApproved`。
3. `ApprovalPolicyService.decide()` — profile 在 `approvalRequiredProfiles` 或任意工具在 `approvalRequiredTools` → `PENDING_APPROVAL`；否则 `QUEUED`。

运行期（在 `ToolCallExecutor` 中）：
- 每次 `runGroup` 提交前 `approvalGate.check()`，被拒时写 `APPROVAL_REQUIRED` 失败结果。

---

## 8. Case 核心：Agent 消息策略树

```
AgentController
    └── AgentUseCase.sendMessage(req)
            └── AgentMessageFactory.strategyHandler()
                    ├── AgentResolveNode   // 拿/创建 ReactLoopAgent
                    ├── AgentIntentNode    // 正则分类 CHAT/CODE_QUESTION/TASK_EXECUTION/CLARIFICATION
                    ├── AgentDispatchNode  // 构造 Message 推入 inbox（带 image 解析）
                    └── AgentCollectNode   // 等 whenIdle，按事件流投影 messages
```

### 8.1 `AgentMessageDynamicContext`

```java
public class AgentMessageDynamicContext {
    private ReactLoopAgent agent;
    private Message userMessage;
    private MessageIntent intent;
}
```

只有这三个字段是必要的——典型"小上下文"模式，每个节点只读写自己关心的字段。

### 8.2 `AgentIntentNode` —— 规则式意图分类

`classify(String)` 用 9 个 `Pattern` 打分：

| 命中条件                                            | 意图                |
| --------------------------------------------------- | ------------------- |
| 含文件路径 / 目录路径 / 代码块 / (代码关键词+任务动词) | `TASK_EXECUTION`    |
| 寒暄词 / 仅问候                                       | `CHAT`              |
| 代码关键词（无任务动词）                              | `CODE_QUESTION`     |
| 解释词 + 任务动词                                     | `CODE_QUESTION`     |
| 任务动词（创建、删除、修复…）                         | `TASK_EXECUTION`    |
| 文件系统词 + 问号结尾                                 | `TASK_EXECUTION`    |
| 兜底                                                | `CHAT`              |

注意是**正则优先于 ML** 的轻量做法，性能高、可解释，但容易被绕；适合作为快速分流，不适合做严肃意图理解。

### 8.3 `AgentDispatchNode` —— 消息构造与注入

- 如果 `intent == TASK_EXECUTION`：
  - 如果是"列目录"问（`DIRECTORY_QUESTION` 命中且含 `ABSOLUTE_PATH`），改写为强提示：
    ```
    你需要列出这个目录的一级子目录和文件。推荐用 shell_execute 工具执行 ls -la "..."
    ```
    （修过的 bug：`MEMORY.md` #5。原来硬编码要求用 `fs_search`，但 `fs_search` 是 glob 文件搜索不适合列一级目录。）
  - 否则注入"先调工具再下结论"的提醒前缀。
- 图片附件走 base64 / data URL 解析（容错：失败跳过单个附件，不打断整条消息）。

### 8.4 `AgentCollectNode` —— 投影与回传

`agent.whenIdle().join()` 阻塞；遍历 `SessionLog.events()` 投影成前端要的消息列表：

- `UserMessage` → `{role: user, content}`
- `AssistantMessage` → `{role: assistant, content}`，把 `ReasoningBlock` 拼到 `<think>…</think>` 之后
- `ToolCall` → `{role: tool, toolName, callId, args, status: running}`，按 callId 入 map
- `ToolResult` → 合并到 callId 对应的 tool 消息：`status: success/error`，`result` 截断到 8192

**callId 的坑（已修）**：模型消息 `Message.id` 是 UUID，**不能**用作工具关联键。真正的 callId 来自 `MessageSource.ToolMessageSource.callId()`。前端 SSE rebuild 才能把同一工具的 call + result 合并到一张卡。

### 8.5 流式：`sendMessageStreaming`

```java
public AgentMessageResponse sendMessageStreaming(req, deltaSink, toolCallSink) {
    agent = agentFactory.get/create(req.agentId());
    agent.updateOptions(...);
    agent.setStreamDeltaSink(deltaSink);
    agent.setStreamToolCallSink(toolCallSink);
    try {
        return sendMessage(req);   // 走策略树
    } finally {
        agent.setStreamDeltaSink(null);
        agent.setStreamToolCallSink(null);
    }
}
```

**关键**：sink 在策略树**之前**绑定，这样 `ReactLoopAgent.turn()` 里的 `publisher.subscribe(...)` 在推 `TextDelta` 时能直接转发给 SSE。

---

## 9. Case 核心：任务提交策略树

```
SubmitHarnessController
    └── SubmitHarnessTaskCaseImpl.execute(req)
            └── SubmitHarnessTaskFactory.strategyHandler()
                    ├── SubmissionRootNode        // ITaskSubmissionPolicyService.validate
                    ├── ProfileResolutionNode     // ExecutionProfile + ModelChannel
                    ├── ToolResolutionNode        // IToolResolutionService
                    ├── PermissionCheckNode       // assess + decide
                    └── EnqueueNode               // 开 session、入队、日志、必要时立刻 executeSession
```

### 9.1 `SubmitTaskDynamicContext`

8 个字段，从 `submission` 一路累积到 `currentStatus`。每个节点读完即写、写完即返回 `null` 让下一节点接手。**任何节点返回非 null 都直接短路**（用于错误场景，本链路未使用）。

### 9.2 五节点分工

| 节点                  | 输入                                | 输出                                                  |
| --------------------- | ----------------------------------- | ----------------------------------------------------- |
| `SubmissionRootNode`  | `SubmitTaskRequest`                 | `TaskSubmissionVO`                                    |
| `ProfileResolutionNode` | `profileCode` + optional `channelCode` | `ExecutionProfileVO` + resolved model channel       |
| `ToolResolutionNode`  | `toolSelection.toolCodes()`         | `List<String> enabledTools`                           |
| `PermissionCheckNode` | `enabledTools`                      | `PermissionAssessmentVO` + `ApprovalDecisionVO`      |
| `EnqueueNode`         | 全部上下文                          | `SessionOpenedResultVO` + `QueuedTaskResultVO`        |

`EnqueueNode` 是叶子：
- 构造 `RuntimeExecutionPlanVO`（打包 plan 入库）。
- `harnessSessionRegistryService.openQueuedSession(plan)` → `SessionOpenedResultVO`。
- `harnessTaskQueueService.createQueuedTask(sessionId, plan)` → `QueuedTaskResultVO`。
- `sessionLogService.recordTaskSubmission(...)` —— 写审计日志。
- 若 `!approval.approvalRequired()` → `harnessExecutionService.executeSession(sessionId)` 立即驱动；否则停在 `PENDING_APPROVAL` 等人工审批。

> 注意 `HarnessExecutionService.executeSession()` 是**直接驱动一次模型调用**，不经过 ReactLoopAgent；这是"短任务"路径，与 AgentUseCase 的"长会话"路径互不干扰。

---

## 10. Case 核心：薄委托类（CRUD 用例）

`cases/session/*` 与 `cases/plugin/*` 的实现几乎都是**薄委托**：

| 文件                                       | 委托目标                                   |
| ------------------------------------------ | ------------------------------------------ |
| `SessionQueryCaseImpl`                     | `ConversationQueryService` / `IConversationQueryService` |
| `SessionRestoreCaseImpl`                   | `SessionRebuilderService.rebuild(sessionId)` |
| `SessionEventQueryCaseImpl`                | `ISessionEventLogService`                  |
| `InstallHarnessPluginCaseImpl`             | `IPluginRegistryService.installPlugin(...)` |
| `ManageHarnessPluginCaseImpl`              | `IPluginRegistryService.enable/disable/uninstall` + `IPluginRuntimeService.stop` |
| `RunHarnessPluginCaseImpl` / `Query...CaseImpl` | `IPluginRuntimeService`               |

这些类**没有业务规则**——纯适配层。`cases` 同时承担"用例编排（策略树）"与"用例适配（薄委托）"两种风格。架构上虽合理，但当业务规则扩张时应避免继续往里塞 if-else；新增逻辑优先考虑抽 `StrategyHandler` 节点。

---

## 11. 设计模式小结

| 模式               | 在哪里体现                                                 |
| ------------------ | ---------------------------------------------------------- |
| **策略树 / 责任链** | `AbstractStrategyRouter` + 9 个 Node                       |
| **工厂 + 动态上下文** | `AgentMessageFactory` / `SubmitHarnessTaskFactory` + 两个 `DynamicContext` |
| **状态机**         | `Phase` sealed + `ReactLoopAgent` 内部切换                |
| **端口-适配器（六边形）** | `domain/.../adapter/port` vs `infrastructure/.../adapter` |
| **事件溯源（轻量）** | `SessionLog` 追加 + `SurfaceOp.Append/Replace` 投影        |
| **流式响应（Reactive Streams）** | `ILlmRuntimePort.stream()` 返回 `Flow.Publisher<StreamChunk>` |
| **策略 + 规约**    | `PermissionPolicyService` / `ApprovalPolicyService` 用策略矩阵拆分 |
| **过滤器链**       | `TaskSubmissionPolicyService` 注入 `List<TaskSubmissionFilter>` |
| **装饰器 / 包裹**  | `toolView` 包装 `agentTools + sharedToolRegistry` 做合并 |
| **幂等补偿**       | `ToolCallExecutor` 在 abort 后写合成 `tool/result` 让回放一致 |

---

## 12. 扩展点速查

| 想做什么                              | 改哪里                                                          |
| ------------------------------------- | --------------------------------------------------------------- |
| 增加一种意图                          | `AgentMessageDynamicContext.MessageIntent` + `AgentIntentNode.classify()` |
| 改写任务提交前校验                    | 实现 `TaskSubmissionFilter`，Spring 自动注入                      |
| 接管运行期审批                        | 实现 `RuntimeApprovalGate`；`AgentFactory` 12 参构造器接入        |
| 接入新的 LLM provider                 | 实现 `LlmAdapter`，注册到 `InMemoryLlmRuntimePort`（或自实现 port） |
| 增加一种事件类型                      | `SessionEvent` 添加 record + `SessionEventType` 枚举 + `reassignSeq` 分支 |
| 让前端展示更细分                      | `SurfaceProjector.project()` 加分支                              |
| 替换系统提示词模板                    | 实现 `SystemPromptAssembler`，改 `AgentFactory.wireAgent` 注入    |
| 修改上下文压缩策略                    | 实现 `CompactionEngine`；`BasicCompactionEngine` 是模板           |

---

## 13. 已知隐患（与 `MEMORY.md` 互补）

> 源码层面已发现但未修，建议在重构阶段处理：

1. **`SessionLog.deriveMessages()` 全量重算**（O(n²)）—— 大会话下成为热点。`derivedCache` 字段已声明但未启用。
2. **`ReactLoopAgent.kick()` 用 `newCachedThreadPool()`** —— 无界队列 + 无命名，长期运行/异常下难以诊断。建议改为命名工厂 + 共享池 + 监控。
3. **`AgentUseCase.sendMessageStreaming` 的 `agent.whenIdle().join()` 阻塞请求线程** —— 高并发时把 Tomcat 工作线程吃光。
4. **`TokenUsage` 估算 `text.length() / 4`** —— 对中文 / 代码块极不准确（中文一字 ≈ 1.5~2 token，代码标识符约 0.25 token/字符）。压缩阈值可能误触发。
5. **`ToolCallExecutor` 串行模式用 `future.join()`** —— 与外层 abort 检查解耦不彻底，取消语义依赖 `AtomicBoolean`，工具内部不一定响应。
6. **`IRuntimeApprovalBroker.requestApproval` 同步阻塞** —— 在 case 层 `AgentCollectNode.whenIdle().join()` 里被调用时，前端不响应审批就会一直挂；缺乏超时回退。
7. **`SurfaceProjector.extractChatMessages` 把 ToolResult 排除** —— 但 `deriveMessages` 把 ToolResult 包含——两条投影路径口径不一致，需要在 UI 渲染层做归一。
8. **`PermissionPolicyService.assess()` 同步加载整张矩阵** —— 没有缓存，每次提交都重建。
9. **`HarnessExecutionService` 是直接驱动模型路径**，不走 ReactLoopAgent，所以**没有 step 流、没有工具、没有 SSE**——这条路径和 `AgentUseCase` 形成"双轨"。
10. **`ScheduleService.drive()` 无调用方**（详见 `MEMORY.md` 隐患 #2）—— `domain/schedule` 子包实际是死代码。

---

## 14. 一句话总结

> **domain 是引擎，case 是装配图。**
>
> - domain 给出会话驱动（`ReactLoopAgent` + `Phase` + `Inbox` + `SessionLog`）和策略基础（`AbstractStrategyRouter`），不依赖任何具体业务。
> - case 把"用户说一句话"翻译成"引擎该怎么跑"——两条策略树（消息流、任务提交），外加若干薄委托。
> - 学懂这两个模块的关键，是先抓 `AbstractStrategyRouter.apply()` 与 `ReactLoopAgent.turn()` 这两条主链，再回头补 `BlockAssembler` / `SurfaceProjector` / `ToolCallExecutor` 等支撑组件。

—— 完 ——
