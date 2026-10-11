# 第 7 章 · 治理与后台

> 本章覆盖：`guard`、`hooks`、`jobs`。
> 解决的核心问题：**工具执行前后怎么拦截？后台任务怎么注册与查询？**

---

## 学习目标

读完本章你能：

1. 区分"运行期守卫（guard）"和"运行期 Hook（hooks）"的差异。
2. 知道 `guard.timeout.ToolTimeoutPolicy` 与 `guard.reminder.RepeatToolReminderEngine` 各自管什么。
3. 知道 `IHookRunnerPort` 的 hook 点（`PRE_TOOL_USE` / `POST_TOOL_USE`）和合并机制。
4. 说出 `jobs` 域"重启即丢"的隐患是什么、它跟 `schedule` 有什么共性。

---

## 章节导读

三个模块都属于"工具执行周围的辅助能力"——`guard` 拦截危险行为，`hooks` 提供扩展点，`jobs` 让 Agent 派发后台任务。

```
tool.service.ToolCallExecutor.execute(...)
   ↓
① ToolArgumentsParser.parse                     ← 5.1 章
② GuardService.preCheck (←──────── guard)
   ├─ ToolTimeoutPolicy.isWithinTimeout
   └─ RepeatToolReminderEngine.check
③ IHookRunnerPort.runPreToolUse (←──────── hooks)
④ RuntimeApprovalGate.checkApproval             ← 5.1 章
⑤ ToolRegistry.lookup.execute
⑥ IHookRunnerPort.runPostToolUse
⑦ SessionLog.append(ToolResult)
   ↓
[若工具想派生后台任务] tool.jobs.JobRunTool → jobs.JobRegistryService
```

---

## 7.1 `guard`：运行守卫

### ① 解决什么问题

工具执行前后的一道"安全闸"：超时策略（同工具不能超过 N 秒）、重复调用提醒（防止 LLM 进入无限循环调同一个工具）。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `guard/service/GuardService.java` / `IGuardService.java` | 服务入口 |
| `guard/timeout/ToolTimeoutPolicy.java` | 工具级超时策略 |
| `guard/reminder/RepeatToolReminderEngine.java` | 重复调用提醒 |
| `guard/model/valobj/GuardDecision.java` / `ToolTimeoutResult.java` | 结果 VO |

### ③ 关键签名

```java
public interface IGuardService {
    GuardDecision preCheck(TurnContext turn, String toolName, Map<String, Object> args);
    void recordInvocation(TurnContext turn, String toolName);
}

public class ToolTimeoutPolicy {
    public boolean isWithinTimeout(String toolName, Duration elapsed);
    public Duration timeoutFor(String toolName);
}

public class RepeatToolReminderEngine {
    public Reminder check(String toolName, InvocationHistory history);
}
```

### ④ 流程图

```
ToolCallExecutor.execute → 调 IGuardService.preCheck
   ↓
GuardService.preCheck
   ├─ ToolTimeoutPolicy.isWithinTimeout  → 超时则抛 TimeoutException
   ├─ RepeatToolReminderEngine.check     → 重复则返回 "已调用 N 次，是否继续" reminder
   └─ 返回 GuardDecision.Allow / Reject
```

### ⑤ 设计意图

> **"守卫"和"Hook"分清楚**：Guard 是"业务级规则"（超时 / 重复）；Hook 是"扩展点"（用户 / 插件可以塞自定义逻辑）。两者职责不混。

### ⑥ 与谁打交道

- **被调用**：`tool.service.ToolCallExecutor`（preCheck 步骤）。
- **依赖**：infra 配置（`harness.guard.*`）。

### ⑦ 注意事项

1. 当前实现是轻量级规则，**没有"语义级"守卫**（比如 "shell 命令语义解析"）。生产化前需补。
2. 超时和 `orTimeout` 是**两个机制**：`ToolTimeoutPolicy` 测 elapsed；`ToolCallExecutor` 用 `orTimeout` 抛错。两者要协同。
3. `RepeatToolReminderEngine` 当前是 hint 不是 hard block，LLM 收到 hint 后自己决定是否继续。

---

## 7.2 `hooks`：生命周期 Hook

### ① 解决什么问题

在工具执行前后暴露"扩展点"，让用户 / 插件可以塞入自定义逻辑（日志、审计、转换、过滤）。相比 Guard，Hook 更通用、更可配置。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `hooks/service/HookService.java` / `IHookService.java` | 服务入口 |
| `hooks/service/HookMatcher.java` | 匹配器（按工具名 / matcher group） |
| `hooks/service/HookOutputMerger.java` | 多 hook 输出合并 |
| `hooks/adapter/port/IHookRunnerPort.java` | 执行端口（infra 提供本地或远端 runner） |
| `hooks/model/valobj/HookPoint.java` | hook 点枚举 |
| `hooks/model/valobj/CommandHook.java` | hook 配置 |
| `hooks/model/valobj/MatcherGroup.java` | 匹配规则 |
| `hooks/model/valobj/HookDialect.java` / `HookOutput.java` / `MergedHookOutcome.java` | 枚举与 VO |

### ③ hook 点

```java
public enum HookPoint {
    PRE_TOOL_USE,        // 工具执行前
    POST_TOOL_USE,       // 工具执行后
    // 未来可扩展：SESSION_START, TURN_END, ...
}
```

### ④ 关键签名

```java
public interface IHookRunnerPort {
    HookOutcome runPreToolUse(TurnContext turn, String toolName, Map<String,Object> args);
    HookOutcome runPostToolUse(TurnContext turn, String toolName, Map<String,Object> args, Object result);
}

public class HookMatcher {
    public List<CommandHook> match(HookPoint point, String toolName);
}

public class HookOutputMerger {
    public MergedHookOutcome merge(List<HookOutcome> outcomes);  // 多 hook 合并
}
```

### ⑤ 调用栈

```
ToolCallExecutor.execute
   ↓
IHookRunnerPort.runPreToolUse(turn, toolName, args)
   ↓
HookService.resolveMatches(HookPoint.PRE_TOOL_USE, toolName)
   ├─ HookMatcher.match(...)       // 按 matcher group 挑出 hook 列表
   └─ 顺序执行 / 并行执行 / 合并结果
   ↓
MergedHookOutcome (allow / deny / modify args)
   ↓
[若 deny] ToolResult.denied; 否则继续派发
```

### ⑥ 设计意图

> **Hook 是"可被外部配置的扩展点"**。配置可来自项目 `harness.hooks.*` yaml，也可来自外部服务（infra 提供远端 runner 时）。

### ⑦ 与谁打交道

- **被调用**：`tool.service.ToolCallExecutor`（PRE/POST 步骤）。
- **依赖**：infra `IHookRunnerPort`（本地或远端）；`HookMatcher` 配置。

### ⑧ 注意事项

1. **PRE hook 可以 deny**（合并结果 `denied` → 不派发）；**POST hook 不能 deny**（已经是结果）。
2. 多 hook 合并有顺序敏感：deny 优先 / args 修改有覆盖策略。
3. Hook 配置改动不会触发已运行会话的 Agent 重建。

---

## 7.3 `jobs`：后台任务注册表

### ① 解决什么问题

让 Agent 把长任务派发到"后台"跑（`tool.jobs.JobRunTool`），后续能用 `job_output` / `job_list` / `job_kill` 跟踪结果。当前是内存实现。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `jobs/service/JobRegistryService.java` / `IJobRegistryService.java` | 任务注册表实现 / 接口 |
| `jobs/model/entity/JobRecord.java` / `JobStart.java` | 实体 |
| `jobs/model/valobj/JobStatus.java` / `JobKind.java` / `JobSnapshot.java` / `JobOutcome.java` | 枚举与 VO |
| `jobs/adapter/repository/IJobRegistryRepository.java` | 仓储端口 |

### ③ 状态机

```
SUBMITTED → RUNNING → COMPLETED
                    ↘ FAILED
                    ↘ KILLED
```

### ④ 与谁打交道

- **被使用**：`tool.jobs.JobRunTool` / `JobListTool` / `JobOutputTool` / `JobKillTool`。
- **依赖**：infra `IJobRegistryRepository`（**当前 InMemory**）；`JobRegistryService` 内部用 `newCachedThreadPool`（无界，已知隐患）。

### ⑤ 注意事项

1. **当前仓储 InMemory，重启即丢**（已知隐患）。
2. **无界线程池** 是真生产隐患：要监控 pool size。
3. 没有"超过 N 小时自动 kill"机制——超长任务会一直在 JobRecord 里挂着。
4. 任务结果写 `JobSnapshot`（脱敏后），原 stdout/stderr 不直接落库——日志路径依赖运行方式。

---

## 小结

1. **Guard vs Hook**：Guard 是"业务规则"（超时 / 重复提醒），Hook 是"扩展点"（可配置）。两者职责不混。
2. **Hook 点**：`PRE_TOOL_USE` 可 deny；`POST_TOOL_USE` 不可 deny。
3. **jobs / schedule 都未真正生产化**：均 InMemory，且 schedule `drive()` 无调用方。
4. **`newCachedThreadPool` 无界**：`ReactLoopAgent.kick()` 与 `JobRegistryService` 都是这个隐患。

---

## 思考题

1. **【Easy】** 给 `HookPoint` 加一个新点 `TURN_END`。要在哪些文件里加代码？`IHookRunnerPort` 接口要不要改？
2. **【Medium】** 想让 `JobRegistryService` 持久化到 MySQL（不再 InMemory）：要改哪些文件？数据迁移怎么处理？
3. **【Hard】** 当前 `RepeatToolReminderEngine` 是 hint（不阻断）；假如你要换成 hard block（同一 tool 在 5 步内调 ≥3 次 → block），同时保留"用户配可豁免"的能力，怎么改？看 `guard.IGuardService.preCheck` 的契约和 `HookMatcher` 的 group 匹配。
