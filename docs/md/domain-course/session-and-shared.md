# 第 3 章 · 会话与事件日志

> 本章覆盖：`session.registry`、`session.event`、`session.log`、`session.conversation`、`shared`。
> 解决的核心问题：**事件日志为什么是这个项目唯一的"领域事件机制"？**

---

## 学习目标

读完本章你能：

1. 说出 `SessionEvent` 是 sealed 接口并列出至少 6 个事件变体。
2. 区分事件源（`session.event`）和事件存储（`session.log`）的边界。
3. 解释 `SurfaceProjector` 怎么把事件流投影成 UI 可见的 `SurfaceOp`。
4. 知道 `shared` 包里的 `HarnessStatusEnumVO` 和策略树抽象为什么是"跨域共享"。

---

## 章节导读

事件溯源（Event Sourcing）在该项目的体现**只有一个地方**：SessionLog。其他域（task / goal / workflow / jobs）的状态变更都是从事件 derive 出来，或者直接走 status 字段。

```
+-------------------+           +----------------------+
| ReactLoopAgent    |  写事件  | SessionLog (内存 WAL) |
+-------------------+ -------> +----------------------+
                                              |
                                              | 投影
                                              v
                                   +-----------------------+
                                   | SurfaceProjector      |
                                   | → SurfaceOp(Append)   | → UI
                                   | → SurfaceOp(Replace)  |
                                   +-----------------------+
                                              |
                                              | 落库
                                              v
                                   +-----------------------+
                                   | ISessionEventStore    | → MyBatis → MySQL
                                   +-----------------------+
```

整章源码主要在 `domain/session/`（约 1500 行）+ `domain/shared/`（约 80 行策略基础设施）。

---

## 3.1 `session.registry`：会话聚合根

### ① 解决什么问题

把 `sessionId` / `cwd` / `profile` / `sessionLog` / `inbox` 这些零散字段绑成一个对象（聚合根），方便跨层传递。infra 层只关心 `HarnessSessionRepository`（MyBatis 持久化会话元数据），不直接管 `SessionLog`。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `registry/service/HarnessSessionRegistryService.java` | 会话注册入口 |
| `registry/model/aggregate/HarnessSessionAggregate.java` | 聚合根 |
| `registry/model/valobj/SessionOpenedResultVO.java` | 打开会话返回 |
| `registry/adapter/repository/IHarnessSessionRepository.java` | 仓储端口 |
| `IHarnessSessionRegistryService.java`（顶级接口） | case/trigger 调用入口 |

### ③ 关键签名

```java
public interface IHarnessSessionRegistryService {
    SessionOpenedResultVO open(OpenSessionRequest req);     // 创建/复用会话
    HarnessSessionAggregate load(String sessionId);
    void close(String sessionId);
}
```

### ④ 设计意图

> **"会话"是一个聚合根** —— 它绑定了多个零散字段和子对象。infra 层通过 `IHarnessSessionRepository` 只落元数据；`SessionLog`（运行时）在 domain 层独立管理，不强制落库。

---

## 3.2 `session.event`：事件溯源核心

### ① 关键源码位置

| 文件 | 行数 | 角色 |
| --- | --- | --- |
| `event/model/entity/SessionEvent.java` | 155 | **sealed 接口**（每种事件一个 record 变体） |
| `event/model/entity/SessionHeader.java` | — | 会话头（profile / cwd / 创建时间） |
| `event/model/entity/SessionLog.java` | — | 内存追加式 WAL |
| `event/model/valobj/SessionEventType.java` | — | 类型枚举（带 `wireName` / `ignorable`） |
| `event/model/valobj/*Payload.java` | — | 每种事件一个 payload record |
| `event/service/SessionEventFactory.java` | 204 | 构造各种事件的工厂 |
| `event/service/SurfaceProjector.java` | — | 事件 → UI `SurfaceOp` 投影 |
| `event/service/SessionRebuilderService.java` | — | 事件流 → 消息列表 derive |
| `event/service/LegacyEventAdapter.java` | — | 旧事件兼容适配 |
| `event/adapter/repository/ISessionEventStore.java` | — | 事件存储端口 |

### ② `SessionEvent` 是 sealed 接口

```java
public sealed interface SessionEvent
        permits TurnStart, TurnEnd, StepStart, StepEnd,
                UserMessage, AssistantChunk, AssistantMessage,
                ToolCall, ToolResult,
                TodoWrite, RequestHeader, RequestContext,
                SessionEndSeed, PlanModeChange, AgentInboxSpliced {

    SessionEventType type();
    String sessionId();
    Instant occurredAt();
    // ...
}
```

### ③ 事件分类

| 类别 | 事件 |
| --- | --- |
| 轮/步边界 | `TurnStart`、`TurnEnd(reason)`、`StepStart`、`StepEnd` |
| 消息 | `UserMessage(Message)`、`AssistantChunk`、`AssistantMessage(Message)` |
| 工具 | `ToolCall(callId, name, arguments)`、`ToolResult(callId, ...)` |
| 计划/待办 | `TodoWrite`、`PlanModeChange` |
| 控制流 | `RequestHeader`、`RequestContext`、`SessionEndSeed`、`AgentInboxSpliced` |

### ④ 关键设计：`SessionEventType` 携带 `wireName` 与 `ignorable`

```java
public enum SessionEventType {
    TURN_START("turn_start", false),
    USER_MESSAGE("user_message", false),
    ASSISTANT_CHUNK("assistant_chunk", false),
    TOOL_CALL("tool_call", false),
    // 旧任务事件，全部 ignorable=true，投影时跳过
    TASK_SUBMITTED("task_submitted", true),
    TASK_AUTO_APPROVED("task_auto_approved", true),
    // ...
}
```

### ⑤ `SessionEventFactory` 怎么用

```java
public interface SessionEventFactory {
    SessionEvent userMessage(String sessionId, Message msg);
    SessionEvent assistantChunk(String sessionId, int index, String text);
    SessionEvent toolCall(String sessionId, String callId, String name, JsonNode args);
    SessionEvent toolResult(String sessionId, String callId, ToolResultBlock result);
    SessionEvent turnStart(String sessionId, String requestId);
    SessionEvent turnEnd(String sessionId, TurnEndReason reason);
    // ...
}
```

### ⑥ `SurfaceProjector`：事件 → UI

```java
// 简化伪代码
public List<SurfaceOp> project(List<SessionEvent> events) {
    List<SurfaceOp> ops = new ArrayList<>();
    for (SessionEvent e : events) {
        if (e.type().ignorable()) continue;       // 跳过旧任务事件
        ops.add(switch (e) {
            case UserMessage m       -> SurfaceOp.Append(message(m));
            case AssistantChunk c    -> SurfaceOp.Append(partial(c));
            case AssistantMessage m  -> SurfaceOp.Append(message(m));
            case ToolCall tc         -> SurfaceOp.InsertToolCard(...);
            case ToolResult tr       -> SurfaceOp.UpdateToolCard(callId, status, result);
            case TurnStart ts        -> SurfaceOp.TurnBoundary(...);
            case TurnEnd te          -> SurfaceOp.TurnBoundary(...);
            default                  -> SurfaceOp.Noop();
        });
    }
    return ops;
}
```

### ⑦ 设计意图

> **把"事件 = 真相"和"UI = 投影"严格分离**。UI 想要什么字段，由 projector 决定；改 UI 不影响 SessionLog 存储，回放 / 调试 / 压缩都是同一个事件流。

### ⑧ 与谁打交道

- **写者**：`ReactLoopAgent` 写入；`ToolCallExecutor` 写入；`TaskExecutionService` 写入。
- **读者**：`SessionRebuilderService.deriveMessages()`（全量重建 O(n²)）；`ConversationQueryService`（读模型）；`SurfaceProjector`（UI 投影）。
- **存储**：`session.log.SessionLogService`（写 MyBatis / `session_event` 表）。

### ⑨ 注意事项

1. **`SessionLog` 是内存 WAL**：进程重启会丢未落库的事件。
2. **`LegacyEventAdapter` 处理 `TASK_*` 旧事件**：投影时跳过但保留序列号，便于老数据回放不报错。

---

## 3.3 `session.log`：事件 MyBatis 持久化

### ① 解决什么问题

把内存 `SessionLog` 的事件落库到 MySQL，让进程重启后能续回。`session.event` 定义契约（事件形状），`session.log` 定义存储（行怎么落）。

### ② 关键源码位置

- `log/service/SessionLogService.java`
- `log/model/entity/SessionEventEntity.java`
- `log/adapter/repository/ISessionEventRepository.java`

### ③ 表结构（`schema.sql`）

```sql
CREATE TABLE harness_session_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    type VARCHAR(40) NOT NULL,
    payload JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    KEY idx_session (session_id, id)
) ... ;
```

### ④ 注意事项

1. **payload 是 JSON**，跨版本兼容靠 `LegacyEventAdapter` 兜底。
2. 没有事件版本字段（`schema_version`）；如上线破坏性变更需要新表或迁移工具。

---

## 3.4 `session.conversation`：读模型

### ① 关键源码位置

- `conversation/service/ConversationQueryService.java` + `IConversationQueryService.java`（顶级接口）
- `conversation/model/valobj/ConversationMessageVO.java`
- `conversation/model/valobj/ConversationSessionSummaryVO.java`
- `conversation/model/valobj/ConversationSessionDetailVO.java`

### ② 解决的什么问题

UI 需要**会话列表 + 会话详情 + 消息列表**的"读模型"。这个子域把 `SessionEvent` 流投影成扁平 `ConversationMessageVO[]`，并附带摘要（首条消息、最后活跃时间、消息数）。

### ③ 调用栈

```
trigger GET /api/harness/conversations?sessionId=...
   ↓
ConversationQueryService.listSummaries()
   ↓
读 SessionEventRepository
   ↓
SessionRebuilderService.deriveMessages(events)   ← 这里 O(n²)
   ↓
取首条 + 末条 + count → ConversationSessionSummaryVO
```

### ④ 设计意图

> **读模型从事件 derive，不用单独维护"会话 summary 表"**。代价是每次查询要全量 rebuild（小会话没影响，长会话会慢——这正是 `domain-case-deep-dive.md` 里讨论的 O(n²) 隐患）。

---

## 3.5 `shared`：跨域基础设施

### ① 关键源码位置

- `shared/model/valobj/HarnessStatusEnumVO.java`
- `shared/strategy/StrategyHandler.java`
- `shared/strategy/AbstractStrategyRouter.java`

### ② `HarnessStatusEnumVO`：全局唯一的任务状态枚举

```java
public enum HarnessStatusEnumVO {
    CREATED,
    PENDING_APPROVAL,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    REJECTED
}
```

全项目（`task.queue.entity.HarnessTaskEntity.status`、trigger 状态查询、UI 颜色编码）都用这个枚举，**不要自己再写一个 StatusVO**。

### ③ 策略树抽象：`StrategyHandler` + `AbstractStrategyRouter`

```java
// StrategyHandler.java
@FunctionalInterface
public interface StrategyHandler<I, C, O> {
    O apply(I requestParameter, C dynamicContext) throws Exception;
}

// AbstractStrategyRouter.java
public abstract class AbstractStrategyRouter<I, C, O> implements StrategyHandler<I, C, O> {
    @Override
    public final O apply(I req, C ctx) throws Exception {
        O result = doApply(req, ctx);
        if (result != null) return result;            // 短路返回
        return router(req, ctx);                       // 路由到下一节点
    }
    protected abstract O doApply(I req, C ctx) throws Exception;
    protected abstract StrategyHandler<I, C, O> getNext(I req, C ctx) throws Exception;
}
```

### ④ 设计意图

> **把"业务流程"拆成"一段段不相关的单元"** —— 比 `if-else` 链清晰，比 GoF 责任链灵活（节点可以持有 Spring Bean）。所有 `Abstract*Node`（case 层）都继承它。

### ⑤ 与谁打交道

- **被继承**：`case` 层所有 `Abstract*Node`。
- **状态枚举被引用**：`task` / `workflow` / `trigger` 三层。

---

## 小结

1. **`SessionEvent` 是 sealed 接口**，每种事件一个 record 变体；类型枚举带 `wireName` + `ignorable` 两标志。
2. **`session.event` 定义契约**、`session.log` 定义存储、`session.conversation` 定义读模型投影——三者严格分层。
3. **`shared` 是跨域基类**：唯一的任务状态枚举 + 唯一的策略树抽象。
4. **派生开销 O(n²)** 是长会话性能隐患的根。

---

## 思考题

1. **【Easy】** 假如要新增一个事件 `CompactionApplied`（当 `agent.compaction` 跑完时记录），你需要改哪几个文件？事件如何在 `SurfaceProjector` 里展现给用户？
2. **【Medium】** `LegacyEventAdapter` 当前只兜底 `TASK_*` 旧事件。如果上线破坏性变更（比如把 `ToolCall.arguments` 从 `JsonNode` 改成 `Map<String,Object>`），靠 LegacyEventAdapter 行不行？为什么？
3. **【Hard】** 你想加一个"事件回放"功能：用户能选某个 sessionId 把所有事件按时间重放到 sandbox 里跑。看 `SessionRebuilderService.deriveMessages` 和 `LegacyEventAdapter`，讨论下方案的可行性。

> 提示：所有答案与 `domain/session/` 与 `domain/shared/` 有关。
