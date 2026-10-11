# 第 9 章 · 协议与工作流

> 本章覆盖：`acp`、`sdk`、`workflow`。
> 解决的核心问题：**怎么把 Agent 暴露成外部协议？怎么编排多步骤？**

---

## 学习目标

读完本章你能：

1. 区分 ACP 与 SDK 的差异——ACP 是"被外部编辑器 / IDE 调"，SDK 是"被上层的 SDK 客户端 / 服务端调"。
2. 跟踪一次工作流启动从 `POST /api/workflow/start` 到 `WorkflowResult` 的完整路径。
3. 知道工作流 SSE 当前为什么"只推终态事件"是已知隐患 #7。

---

## 章节导读

```
        外部编辑器 / IDE
              │ (JSON-RPC over stdio)
              ▼
       ┌────────────────┐
       │     acp        │ ← Agent Client Protocol 服务端
       └────────────────┘
              │ IAcpAgentPort
              ▼
    ReactLoopAgent + ToolRegistry + SessionLog

       自己的 SDK 客户端 / 服务端
              │ (transport 中立：jsonrpc / http / ws)
              ▼
       ┌────────────────┐
       │     sdk        │ ← 对外 SDK 入口
       └────────────────┘
              │ ISdkServerHandler / ISdkTransportPort
              ▼
       IModelExecutionPort.execute

       内部工作流
              │ (workflow.start(req))
              ▼
       ┌────────────────┐
       │   workflow     │ ← 多步骤编排
       └────────────────┘
              │ IWorkflowEnginePort
              ▼
       内部按节点调 ReactLoopAgent + deltaSink/toolCallSink 推 SSE
```

三个模块都用"完整 7 节"模板（核心模块）。

---

## 9.1 `acp`：ACP 协议服务端

### ① 解决什么问题

把 Agent 暴露为 ACP（Agent Client Protocol）服务端，让外部编辑器 / IDE 通过 stdio 上的标准协议直接接入，避免每家 IDE 各做一套适配。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `acp/service/AcpServerService.java` / `IAcpServerService.java` | 服务实现 / 接口 |
| `acp/adapter/port/IAcpAgentPort.java` | 协议侧 → Agent 适配端口 |
| `acp/model/valobj/AcpSessionInfo.java` / `AcpMessage.java` / `AcpPromptRequest.java` / `AcpPromptResponse.java` / `AcpStopReason.java` | 协议消息 VO |

### ③ 关键签名

```java
public class AcpServerService implements IAcpServerService {
    private final IAcpAgentPort agentPort;

    public AcpSessionInfo newSession(String cwd, String mcpConfig) {
        return agentPort.initialize(cwd, mcpConfig);
    }
    public AcpPromptResponse prompt(AcpPromptRequest request) {
        return agentPort.prompt(request);
    }
    public void cancel(String sessionId)       { agentPort.cancel(sessionId); }
    public void closeSession(String sessionId) { agentPort.closeSession(sessionId); }
}
```

### ④ 流程图

```
外部编辑器 → JSON-RPC over stdio
   ↓
AcpServerService.prompt(req)
   ↓
IAcpAgentPort.prompt(req)
   ↓
infra AcpAgentPort 实现（持有 ReactLoopAgent + deltaSink/toolCallSink）
   ↓
将 AcpPromptResponse 流式返回（cursor 风格）
```

### ⑤ 设计意图

> **"ACP = 标准协议适配层"** —— 业务侧只需要 `IAcpAgentPort`，infra 在这个 port 内把协议消息 ↔ 内部事件流互转。

### ⑥ 与谁打交道

- **被消费**：外部 ACP 客户端（IDE 插件等）。
- **依赖**：`IAcpAgentPort`（infra 实现）；间接复用 `ReactLoopAgent` + `SessionLog` + `ToolRegistry`。

### ⑦ 注意事项

1. **协议版本钉死**（随 ACP 演进）。
2. 长 prompt 的流式响应边界需在 `AcpPromptResponse` 上携带 cursor。
3. 当前没看到多 session 并发上限说明——ACP 协议本身没有硬上限，要自己控。

---

## 9.2 `sdk`：对外 SDK 层

### ① 解决什么问题

提供一个 transport 中立的 SDK 入口（客户端 + 服务端），让上层应用（不论是 CLI / HTTP / WebSocket）都能用同一套接口调 Agent。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `sdk/service/SdkClientService.java` / `ISdkClientService.java` | 客户端 SDK |
| `sdk/service/SdkServerService.java` / `ISdkServerService.java` | 服务端 SDK |
| `sdk/adapter/port/ISdkTransportPort.java` | 传输端口（jsonrpc / http / ws） |
| `sdk/adapter/port/ISdkServerHandler.java` | 服务端处理器端口（infra 绑定到 ReactLoopAgent） |
| `sdk/model/valobj/InitializeParams.java` / `InitializeResult.java` / `SessionPromptParams.java` / `SessionPromptResult.java` / `SessionStatus.java` / `NotificationType.java` | 协议消息 VO |

### ③ 关键签名

```java
public interface ISdkTransportPort {
    void send(String method, Object payload);
    <T> T request(String method, Object payload, Class<T> resultType);
}

public interface ISdkServerHandler {
    InitializeResult onInitialize(InitializeParams params);
    SessionPromptResult onPrompt(SessionPromptParams params);
    void onCancel(String sessionId);
    // notification: Progress / Log / ...
}
```

### ④ 与谁打交道

- **消费方**：上层应用（CLI / 后台服务 / WebSocket gateway）。
- **服务端实现**：infra `ISdkServerHandler` 绑定到 `IModelExecutionPort.execute` / `ReactLoopAgent`。

### ⑤ 设计意图

> **"SDK = 把 Agent 包成稳定协议"** —— 上层不用关心 LLM / 工具 / 事件流，只要 transport 能跑就行。

### ⑥ 注意事项

1. 协议版本与命名空间要在 `InitializeParams` 携带，便于多版本共存。
2. Notification（异步推送）走 `NotificationType` 枚举；不要把 stream 与 request 混在同一 channel。
3. **不要在 domain 层实现 transport**——那是 infra 的事。

---

## 9.3 `workflow`：工作流编排

### ① 解决什么问题

让业务方能定义"多步骤流程"（Step1 → Step2 → Step3），用 ReactLoopAgent 作为步骤执行器，每个步骤的 stdout / 工具卡片通过 SSE 推给前端。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `workflow/service/WorkflowService.java` / `IWorkflowService.java` | 工作流服务 |
| `workflow/adapter/port/IWorkflowEnginePort.java` | 引擎端口（infra `LocalWorkflowEnginePort`） |
| `workflow/model/entity/WorkflowRun.java` / `WorkflowStartRequest.java` | 实体 |
| `workflow/model/valobj/WorkflowMeta.java` / `WorkflowResult.java` / `WorkflowPhase.java` / `WorkflowStopReason.java` | VO |

### ③ 关键签名

```java
public class WorkflowService {
    public WorkflowRun start(WorkflowStartRequest req) {
        return enginePort.execute(req);   // 立即返回 WorkflowRun（带 runId）
    }
}

public class LocalWorkflowEnginePort implements IWorkflowEnginePort {
    public WorkflowRun execute(WorkflowStartRequest req) {
        // 1. 按 steps 顺序展开
        // 2. 复用 ReactLoopAgent 跑每一步（含 deltaSink / toolCallSink 推 SSE）
        // 3. 收尾 → WorkflowResult
        return new WorkflowRun(runId, CompletableFuture.completedFuture(result));
    }
}
```

### ④ 流程图

```
POST /api/workflow/start
   ↓
WorkflowService.start(req)
   ↓
IWorkflowEnginePort.execute(req)            ← infra 实现
   ↓
LocalWorkflowEnginePort.runGraph(steps)
   ├─ step 1 → ReactLoopAgent.run(...).stream(...)
   │            ├─ deltaSink → SSE 'chunk'
   │            └─ toolCallSink → SSE 'step_break' / 'tool_card'
   ├─ step 2 → 同上
   └─ ...
   ↓
WorkflowResult
   ↓
SSE 'workflow_done'  ← 当前只推终态，无中间增量
```

### ⑤ 设计意图

> **复用 ReactLoopAgent 作步骤执行器** —— 工作流"壳"不引入新执行引擎，省一份心智负担。

### ⑥ 与谁打交道

- **被依赖**：trigger `/api/workflow/*` + 各业务侧（plugin / task 都可能触发 workflow）。
- **依赖**：`agent.ReactLoopAgent` + `session.event.SessionLog`（与对话主线共用）。

### ⑦ 注意事项

1. **SSE 当前只推终态事件，无中间增量**（已知隐患 #7）——workflow 长时间跑，前端只能等。
2. 步骤间数据传递走 `Context`（key-value），**不要**让步骤隐式共享状态。
3. `WorkflowStopReason` 枚举要穷举（SUCCESS / FAILED / CANCELED / TIMEOUT），不要塞字符串。

---

## 小结

1. **ACP 是"被外部 IDE 调"**；**SDK 是"被上层应用调"**；**Workflow 是"内部多步骤编排"**。
2. 三者都是"包装层"——底下都是 `ReactLoopAgent + ToolRegistry + SessionLog`。
3. **Workflow SSE 隐患** 是当前最影响用户体验的部分：长工作流没有中间反馈。

---

## 思考题

1. **【Easy】** 想给 `IWorkflowEnginePort` 加一个 `pause/resume`（让用户在 workflow 中途暂停审批）。要改几个接口？UI 怎么配合？
2. **【Medium】** ACP 与 SDK 都是"暴露层"——两者能不能合并？看 `IAcpServerService` 与 `ISdkServerHandler` 的方法签名差异，决定合并是简化还是复杂化。
3. **【Hard】** 给 workflow 加**中间 SSE 增量**：每一步的 `AssistantChunk`、`ToolCall`、`ToolResult` 实时推到前端。看 `LocalWorkflowEnginePort` 和 `ReactLoopAgent.setStreamDeltaSink`，讨论方案。
