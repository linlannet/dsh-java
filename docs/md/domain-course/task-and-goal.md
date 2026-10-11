# 第 2 章 · 任务与目标

> 本章覆盖：`task`（含 5 个子域）、`goal`、`plan`、`todo`。
> 解决的核心问题：**一个 Harness Task 从提交到跑完要怎么过五关？**

---

## 学习目标

读完本章你能：

1. 说出 `task` 的 5 个子域各自的边界。
2. 跟踪一个提交请求从 `POST /api/harness/tasks/submit` 到 `RUNNING` 的完整路径。
3. 区分"权限矩阵（permission）"和"审批决策（approval）"两个把关点。
4. 理解 `goal`/`plan`/`todo` 三个"目标/计划"小模块各自的差异。

---

## 章节导读

任务域是项目里**第二条业务主线**——不同于对话主线（用户直接发消息），任务主线是"异步提交、可能要审批、跑很多步"。它由五个子域协作：

```
trigger.POST /api/harness/tasks/submit
  ↓
task.submission（过滤参数）
  ↓
task.permission（评估权限矩阵）
  ↓
task.approval（决定是否需要审批）
  ↓
task.queue（入队持久化）
  ↓
task.execution（执行入口 → 复用 Agent 链路）
```

`goal` / `plan` / `todo` 是与"目标 / 计划 / 待办"相关的三个轻量模块，分别支撑 Agent 在执行期的"长程目标 / 计划模式切换 / 即时 todo 列表"。

整章源码主要在 `domain/task/`（约 1100 行）+ `domain/goal/`、`domain/plan/`、`domain/todo/`。

---

## 2.1 `task`（顶级）：五段策略链 + 状态机

### ① 解决什么问题

把"接收用户提交"到"开始执行"之间这一段（参数校验、权限评估、审批决策、入队），拆成 5 段独立节点，每个节点只关心自己的输入和上下文。最终状态写到 `HarnessTaskEntity`（`task.queue.model.entity`）。

### ② 状态机：`HarnessStatusEnumVO`

> 项目唯一的状态机枚举（定义在 `shared/`）。

```
CREATED ───→ PENDING_APPROVAL ───→ QUEUED ───→ RUNNING ───→ COMPLETED
                                       ↑                       ↘
                                       |                        FAILED
                                    (approve)
                                       |
                            (runnable=false / 拒绝)
                                       ↓
                                    REJECTED
```

### ③ 五段子域一览

| 子域 | 职责 | 关键对象 |
| --- | --- | --- |
| `task.submission` | 提交参数过滤链 | `TaskSubmissionVO`、`ProfileCodeFilter`、`PromptNotBlankFilter`、`ToolSelectionFilter`、`TaskSubmissionFilter` |
| `task.permission` | 权限矩阵评估 | `PermissionAssessmentVO`、`PermissionMatrixVO`、`PermissionPolicyService`、`IPermissionMatrixPort` |
| `task.approval` | 审批决策 + 命令 + 查询 | `ApprovalDecisionVO`、`ApprovalActionVO`、`ApprovalPolicyVO`、`PendingApprovalVO`、`ApprovalPolicyService`、`ApprovalCommandService`、`ApprovalQueryService` |
| `task.queue` | 任务队列持久化 | `HarnessTaskEntity`、`HarnessTaskQueueService`、`IHarnessTaskQueueRepository`、`QueuedTaskResultVO` |
| `task.execution` | 会话执行入口 | `HarnessExecutionService`、`IModelExecutionPort`、`RuntimeExecutionPlanVO` |

### ④ 入口接口（顶级）

```java
// 顶级接口都放在 task 包根
IHarnessExecutionService        // 外部（trigger）真正调的入口
IHarnessTaskQueueService        // 队列查询
ITaskSubmissionPolicyService    // 提交过滤链
IPermissionPolicyService        // 权限评估
IApprovalPolicyService          // 审批决策
IApprovalCommandService         // 审批命令（approve/reject）
IApprovalQueryService           // 审批查询
```

### ⑤ 设计意图

> **把"提交决策"和"任务执行"彻底分开** —— 提交链做的是决策（要不要让它跑），执行链做的是干活（让它跑起来）。决策可以是异步（等用户审批），执行必须可控。

---

## 2.2 `task.submission`：提交参数过滤链

### 关键源码位置

- `submission/model/valobj/TaskSubmissionVO.java`
- `submission/service/TaskSubmissionPolicyService.java`
- `submission/service/filter/TaskSubmissionFilter.java`（接口）
- `submission/service/filter/ProfileCodeFilter.java`
- `submission/service/filter/PromptNotBlankFilter.java`
- `submission/service/filter/ToolSelectionFilter.java`

### 关键签名

```java
// TaskSubmissionFilter.java（接口）
public interface TaskSubmissionFilter {
    void apply(TaskSubmissionVO vo);          // 不通过则抛 SubmissionRejectedException
}

// TaskSubmissionPolicyService.java（伪代码）
public class TaskSubmissionPolicyService {
    private final List<TaskSubmissionFilter> filters;

    public void validate(TaskSubmissionVO vo) {
        for (var f : filters) f.apply(vo);    // 任一不过 → 抛错返回 400
    }
}
```

### 责任链示意

```
TaskSubmissionPolicyService.apply(VO)
   ↓
ProfileCodeFilter       // profile 必须是已知 profile
   ↓
PromptNotBlankFilter    // prompt 不能空
   ↓
ToolSelectionFilter     // enabledTools 必须是 ToolCatalogEntryVO 子集
   ↓
下一个节点（case 层）
```

---

## 2.3 `task.permission`：权限矩阵评估

### 关键源码位置

- `permission/service/PermissionPolicyService.java`
- `permission/model/valobj/PermissionAssessmentVO.java`
- `permission/model/valobj/PermissionMatrixVO.java`
- `permission/adapter/port/IPermissionMatrixPort.java`

### 评估规则（伪代码）

```java
// PermissionPolicyService.java
public PermissionAssessmentVO assess(TaskSubmissionVO submission) {
    Set<String> tools = submission.enabledTools();
    Set<String> profiles = Set.of("headless", "web");

    Map<String, Boolean> decisions = new HashMap<>();
    for (String tool : tools) {
        boolean allowed = matrixPort.isAllowed(profiles, tool);
        decisions.put(tool, allowed);
    }
    return new PermissionAssessmentVO(decisions);
}
```

### 关键设计

- **配置**：权限矩阵由 infra `PermissionMatrixPort` 提供，定义在 `harness.permission.matrix` 下。
- **粒度**：每个工具一票，不是整体 allow/deny。
- **与审批的关系**：评估结果只告诉审批"哪些工具需要人盯"，审批会综合 profile 决定是否整体拦。

---

## 2.4 `task.approval`：审批决策

### 关键源码位置

- `approval/service/ApprovalPolicyService.java`
- `approval/service/ApprovalCommandService.java`
- `approval/service/ApprovalQueryService.java`
- `approval/model/valobj/ApprovalDecisionVO.java` / `ApprovalActionVO.java` / `ApprovalPolicyVO.java` / `PendingApprovalVO.java`
- `approval/adapter/port/IApprovalPolicyPort.java`

### 决策示意

```java
// ApprovalPolicyService.java（伪代码）
public ApprovalDecisionVO decide(String profile, PermissionAssessmentVO a) {
    boolean webNeedsApproval = "web".equals(profile);
    boolean anySensitive     = a.containsAny("shell_execute", "fs_write", "plugin.run");

    if (webNeedsApproval || anySensitive) return ApprovalDecisionVO.PENDING_APPROVAL;
    return ApprovalDecisionVO.AUTO_APPROVED;
}
```

### 兜底

`HarnessExecutionService.executeSession(...)` 入口对 `PENDING_APPROVAL` 状态的任务**直接抛异常**——防止有人绕过审批 API 直接调用执行。

### 恢复链路

```
POST /api/harness/approvals/{sessionId}/approve
  ↓
ApprovalCommandService.approve(sessionId)
  ↓
任务状态 QUEUED 落库
  ↓
HarnessExecutionService.executeSession
  ↓
IModelExecutionPort.execute
  ↓
RUNNING → COMPLETED / FAILED
```

---

## 2.5 `task.queue`：入队与持久化

### 关键源码位置

- `queue/service/HarnessTaskQueueService.java`
- `queue/model/entity/HarnessTaskEntity.java`
- `queue/adapter/repository/IHarnessTaskQueueRepository.java`
- `queue/model/valobj/QueuedTaskResultVO.java`

### 状态落库时机

| 时机 | 状态 |
| --- | --- |
| 提交成功 | `CREATED` |
| 提交审批中 | `PENDING_APPROVAL` |
| 审批通过 | `QUEUED` |
| 真正开始执行 | `RUNNING` |
| 执行完 | `COMPLETED` / `FAILED` |

> **历史坑**：`schema.sql` 用 `WHERE NOT EXISTS` + `INSERT IGNORE` 初始化默认模型，遇到新模型上线不会 UPDATE 老行。真正下线/上切模型需要手动 SQL（详见 `runtime.setting`）。

---

## 2.6 `task.execution`：执行入口

### 关键源码位置

- `execution/service/HarnessExecutionService.java`
- `execution/adapter/port/IModelExecutionPort.java`
- `execution/model/valobj/RuntimeExecutionPlanVO.java` / `ModelExecutionResultVO.java` / `HarnessExecutionResultVO.java`

### 与 Agent 主线的关系

```
HarnessExecutionService.executeSession(sessionId)
   ↓
ModelExecutionPort.execute(plan)         // infra 实现
   ↓
复用 ReactLoopAgent + ToolRegistry + SessionLog
   ↓
   收尾 → SessionEndSeed 事件 → status=COMPLETED
```

**核心收益**：任务主线和对话主线共用同一套执行引擎，省下一份"异步执行"代码。

---

## 2.7 `goal`：目标聚合

### 关键源码位置

- `goal/service/GoalService.java`
- `goal/model/entity/GoalAggregate.java`
- `goal/model/valobj/GoalSnapshotVO.java` / `GoalCommandVO.java` / `GoalAction.java` / `GoalStatus.java`
- `goal/adapter/repository/IGoalRepository.java`
- `goal/IGoalCommandService.java` / `IGoalQueryService.java`

### 解决的问题

Agent 在执行长任务时需要一个"长程目标"概念（不是 todo，是 goal）。每次推进记录 `GoalAction`，定期落快照 `GoalSnapshotVO`。

> 当前 UI 没有专门的目标视图，主要服务于"目标规划 / 复盘 / 评估"，调用入口在 trigger 层 `/api/harness/goals/*`。

---

## 2.8 `plan`：计划模式

### 关键源码位置

- `plan/service/PlanModeService.java`
- `plan/model/valobj/PlanModeState.java`
- `ExitPlanModeTool.java`（`tool/plan/`，被 Agent 调）

### 解决的问题

让 Agent 进入"先列计划再执行"模式（类似 Claude 的 plan mode）。三态切换：`OFF → PLAN_ONLY → EXEC`。

### 关键点

- `PlanModeService` 切换状态，事件流写入 `PlanModePayload`。
- LLM 在 system prompt 中感知当前模式，决定是否生成"计划"而非"动作"。

---

## 2.9 `todo`：即时待办

### 关键源码位置

- `todo/service/TodoWriteService.java`
- `todo/TodoWriteTool.java`（被 Agent 调）
- `todo/model/valobj/TodoItem.java` / `TodoWriteResultVO.java`

### 解决的问题

Agent 在执行复杂任务前会先拆解一份 todo（"1. 读文件 2. 写测试 3. 跑测试 4. 提交"），然后逐步推进。

### 与 plan 的差异

| | plan | todo |
| --- | --- | --- |
| 模式 | 模式切换（OFF → PLAN_ONLY → EXEC） | 在执行中持续更新 |
| 数据形态 | `PlanModeState` 单例 | `List<TodoItem>` 列表 |
| 触发 | 用户主动 | Agent 内部决定 |
| 存储 | 模式会话级 | todo 事件流式 |

---

## 小结

1. **任务域是 5 段子域 + 1 个执行入口**：`submission / permission / approval / queue / execution`。任何"提交→跑完"的工作都会过这 5 关。
2. **状态机统一用 `HarnessStatusEnumVO`**——全项目唯一的任务状态枚举，跨子域共享。
3. **执行入口（`task.execution`）与对话入口（`agent`）共用同一套 ReactLoopAgent**——这是任务主线和对话主线"合流"的关键。
4. **`goal` / `plan` / `todo` 三个轻量模块解决"目标 / 计划模式 / 即时待办"三种不同尺度的"非动作性"概念**。

---

## 思考题

1. **【Easy】** 假如要让 `task.approval` 支持**自定义审批人**（"工具 X 的审批必须由管理员来批"），你会改哪几个文件？审批命令接口（`IApprovalCommandService`）的签名要不要动？
2. **【Medium】** "需要审批"和"权限被拒绝"是两回事。当前 `task.permission` 只产出 `PermissionAssessmentVO`，审批要不要审批其实是 `task.approval` 决定的。如果让你把"权限拒绝"从源头拦掉（在 `task.submission` 阶段就 403），你怎么改？
3. **【Hard】** 任务主线和对话主线共用 ReactLoopAgent，如果 task.execution 报错（比如 LLM 401），应该怎么把错误同时写进 `SessionLog`（给用户看）和 `HarnessTaskEntity.status=FAILED`（给运维看）？看 `task.execution.service.HarnessExecutionService` 和 `session.event.service.SessionEventFactory`。
