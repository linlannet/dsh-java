# 第 5 章 · 工具与执行

> 本章覆盖：`tool.service`（执行核心）+ 12 个工具族（`tool.fs` / `tool.shell` / `tool.web` / `tool.mcp` / `tool.plugin` / `tool.skill` / `tool.jobs` / `tool.terminal` / `tool.schedule` / `tool.goal` / `tool.lsp` / `tool.session`）。
> 解决的核心问题：**工具怎么派发？三类工具（内置 / 协议 / 平台）怎么共存？**

---

## 学习目标

读完本章你能：

1. 说出 `ToolCallExecutor.execute()` 的 5 个步骤（解析 → 守卫 → hook → 派发 → 回填）。
2. 区分内置 / 插件 / MCP 三类工具的命名空间前缀与注册路径。
3. 列出至少 6 个内置或扩展工具族及其对应的领域能力。
4. 知道 `RuntimeApprovalGate` 与提交期审批的边界差异。

---

## 章节导读

工具是 Agent 的"双手"。本章第一部分讲**派发核心**（`tool.service`），第二部分讲**12 个工具族**（每个工具族都遵循同构：`<Tool>.java` + `adapter/port/I<Family>Port.java`）。

```
ReactLoopAgent.turn() 解析到 tool_calls
   ↓
tool.service.ToolCallExecutor.execute(turn, step, toolCalls)
   ↓
   1) ToolArgumentsParser.parse(toolName, args)             // 参数解析
   2) guard.service.GuardService.preCheck(...)              // 超时 / 重复提醒
   3) hooks.adapter.port.IHookRunnerPort.runPreToolUse(...)  // PRE_HOOK
   4) RuntimeApprovalGate.checkApproval(...)                // 运行期审批
   5) ToolRegistry.lookup(toolName).execute(args)            // 派发
   6) IHookRunnerPort.runPostToolUse(...)                   // POST_HOOK
   7) SessionEventFactory.toolResult(...) → SessionLog      // 回填
```

### 工具命名约定

| 来源 | 命名 | 示例 |
| --- | --- | --- |
| 内置 | 直接名 | `fs_read`, `shell_execute`, `web_search`, `ask_user_question` |
| 插件 | `plugin__<id>__<tool>` | `plugin__sample-tools-plugin__say_hello` |
| MCP | `mcp__<server>__<tool>` | `mcp__filesystem__read_file` |

> **前缀分支**在 `ToolRegistry.lookup` 内部根据命名空间前缀决定走哪条解析路径。

---

## 5.1 `tool.service`：执行核心（**完整 7 节**）

### ① 解决什么问题

任何"想执行工具"的需求都必须经过同一个执行核心。这要求核心承担：参数解析、守卫、Hook、运行期审批、派发、结果回填。每一层都可以独立替换（Hook 引擎 / 审批 gate / ToolRegistry 实现）。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `tool/service/ToolCallExecutor.java` | **核心派发器**（约 470 行） |
| `tool/service/ToolRegistry.java` | 工具注册表（含前缀路由） |
| `tool/service/ToolArgumentsParser.java` | 参数解析与校验 |
| `tool/service/RuntimeApprovalGate.java` | 运行期审批 gate 接口 |
| `tool/service/MatrixRuntimeApprovalGate.java` | 默认实现（matrix + broker） |

### ③ 关键签名

```java
// ToolCallExecutor.java
public class ToolCallExecutor {
    public ToolCallExecutor(ToolRegistry registry, SessionLog log, int maxParallel) {...}

    public void execute(TurnContext turn, int stepIdx, List<ToolCallBlock> toolCalls) {
        // 1. 参数解析
        // 2. guard.preCheck
        // 3. hook PRE_TOOL_USE
        // 4. RuntimeApprovalGate.checkApproval
        // 5. registry.lookup(name).execute(args)        // 派发
        // 6. hook POST_TOOL_USE
        // 7. log.append(ToolResult(callId, status, out))
    }
}

// ToolRegistry.java
public class ToolRegistry {
    public void register(String name, Tool tool);            // 内置用
    public void registerPluginTool(String pluginId, String name, Tool tool);
    public void registerMcpTool(String serverId, String name, Tool tool);
    public Tool lookup(String fullName);                      // 按前缀路由
}

// RuntimeApprovalGate.java
public interface RuntimeApprovalGate {
    ApprovalCheck checkApproval(String toolName, Map<String, Object> args);
    // 简单实现：allowAll()；复合实现：查矩阵 + 调 broker
}
```

### ④ 派发核心流程（伪代码）

```java
public void execute(TurnContext turn, int step, List<ToolCallBlock> calls) {
    var batched = planBatching(calls, maxParallel);    // 并发上限
    for (var batch : batched) {
        batch.stream().parallel().forEach(call -> {
            try {
                var args   = parser.parse(call.name(), call.arguments());
                guard.preCheck(turn, call.name(), args);
                hookRunner.runPreToolUse(turn, call.name(), args);
                approvalGate.checkApproval(call.name(), args);   // 默认 allowAll
                var result = registry.lookup(call.name()).execute(args);
                hookRunner.runPostToolUse(turn, call.name(), args, result);
                sessionLog.append(ToolResultPayload(call.callId(), result, "success"));
            } catch (ApprovalDeniedException denied) {
                sessionLog.append(ToolResultPayload(call.callId(), denied.message(), "denied"));
            } catch (Exception ex) {
                sessionLog.append(ToolResultPayload(call.callId(), ex.getMessage(), "error"));
            }
        });
    }
}
```

### ⑤ 设计意图

> **ToolCallExecutor 是工具唯一入口** —— 不允许任何"绕开执行核心直接调工具"的情况。这保证了：
> - 工具一定过守卫 + hook + 审批 + 日志；
> - 工具并行度由 `maxParallel` 控，不被并发调用方决定；
> - 任意一层的实现可独立替换（Hook 引擎 / 审批策略 / Registry 数据源）。

### ⑥ 与谁打交道

- **被调用**：`ReactLoopAgent.turn()` 解析 tool_calls 后。
- **依赖**：
  - `tool.adapter.ToolRegistry`（工具查找）
  - `guard.service.GuardService`（守卫）
  - `hooks.adapter.port.IHookRunnerPort`（Hook）
  - `agent.adapter.port.IRuntimeApprovalBroker`（运行期审批）
  - `session.event.SessionLog`（结果落库）

### ⑦ 注意事项

1. **超时用 `orTimeout`**（协作式）—— 子任务抛 `TimeoutException`，executor 转 `ToolResult.error(callId, "TIMEOUT", ...)`。
2. **`RuntimeApprovalGate` 默认 allowAll**（`AgentFactory` 12 参构造时 `approvalBroker=null`）——审批只在任务提交期拦截（`task.approval`），运行期默认放行（已知隐患 #1）。
3. 内置 `DangerousCommandGuard` 黑名单防御，但**不是白名单**——`shell_execute` 仍零沙箱（已知隐患 #2）。
4. **前缀命名空间** 不要随意破坏：`plugin__` / `mcp__` 是硬约定，破坏会导致 `lookup` 错路径。

---

## 5.2 内置工具族：`tool.fs` / `tool.shell` / `tool.web`

> 这一组是 Agent 最常用的"动手"工具。每个族用"轻量 5 节"模板。

### 5.2.1 `tool.fs`（文件）

#### ① 一句话作用

一组围绕工作区文件的读/写/编辑工具，是 Agent 改代码、调配置的入口。

#### ② 关键源码位置

- `tool/fs/FsReadTool.java`、`FsWriteTool.java`、`FsEditTool.java`、`FsGrepTool.java`、`FsSearchTool.java`、`FsReadImageTool.java`、`StrReplaceEditorTool.java`
- `tool/fs/adapter/port/IFsPort.java`（infra 实现）

#### ③ 关键类（一览）

```java
public interface IFsPort {
    String read(Path file, long offset, long limit);
    void   write(Path file, String content, boolean create);
    void   edit(Path file, String oldText, String newText);
    List<Match>  grep(Path root, String pattern, boolean recursive);
    List<Path>   search(Path root, String glob);    // glob 文件搜索
    byte[]      readImage(Path file);
}
```

#### ④ 与谁打交道

- **被使用**：`ToolCallExecutor` 派发（`fs_read`/`fs_write`/`fs_edit`/`fs_grep`/`fs_search`/`fs_read_image`/`str_replace_editor`）。
- **依赖**：`sandbox.service.SandboxPolicyService.resolve(...)` 拿 cwd 与权限；infra `IFsPort`（落本地或远程）。

#### ⑤ 注意事项

- `fs_search` 用 `glob`（`pattern="*"` 会**递归**匹配所有文件），**不是列目录**。`AgentDispatchNode.buildDirectoryInstruction()` 推荐用 `shell_execute("ls -la")` 或 `find -maxdepth 1 -type d` 来列一级目录。
- `StrReplaceEditorTool` 比 `FsEditTool` 更精细（保留前 N 行 + match-block 精确替换），agent 写文件的主入口。

---

### 5.2.2 `tool.shell`（Shell）

#### ① 一句话作用

把用户指令交给本地 shell 执行（`/bin/sh -c`），是"万能手"——也是**零沙箱**的高风险工具。

#### ② 关键源码位置

- `tool/shell/ShellExecuteTool.java`
- `tool/shell/DangerousCommandGuard.java`（黑名单：`rm -rf /` / `shutdown` / `mkfs` / `dd if=...` …）
- `tool/shell/IShellExecutor.java`（infra 端口）
- `tool/shell/ShellResult.java`

#### ③ 关键类

```java
public interface IShellExecutor {
    ShellResult exec(String command, Duration timeout);
}

public class DangerousCommandGuard {
    public boolean isDangerous(String command);    // 黑名单正则
}
```

#### ④ 与谁打交道

- **被使用**：`ToolCallExecutor`（`shell_execute`）。
- **依赖**：infra `IShellExecutor`（直接 `/bin/sh -c`，无沙箱）；`DangerousCommandGuard`（黑名单）。
- **风险**：黑名单之外的命令仍可执行；当前任务提交期 `task.approval` 会拦 shell_execute。

#### ⑤ 注意事项

- 已知隐患 #2：零沙箱。生产化前必须做"目录沙箱 + 命令白名单"。
- `DangerousCommandGuard` 不是白名单，要绕很容易（编码 / `\` 续行）。

---

### 5.2.3 `tool.web`（Web）

#### ① 一句话作用

让 Agent 联网：`WebSearchTool` 搜索、`WebFetchTool` 抓取网页正文。

#### ② 关键源码位置

- `tool/web/WebFetchTool.java`、`WebSearchTool.java`
- `tool/web/adapter/port/IWebPort.java`（infra 端口）

#### ③ 关键类

```java
public interface IWebPort {
    List<SearchResult> search(String query);
    String fetch(String url, int maxBytes);
}
```

#### ④ 与谁打交道

- **被使用**：`ToolCallExecutor`（`web_search` / `web_fetch`）。
- **依赖**：infra `IWebPort`（可对接 Google / Bing / 内部搜索）。

#### ⑤ 注意事项

- `web` profile 默认要走审批（`task.approval` 把 web 工具划入 PENDING_APPROVAL）。

---

## 5.3 协议工具族：`tool.mcp` / `tool.plugin`

> 这两个族是把"外部协议/外部包"包成标准工具的桥。

### 5.3.1 `tool.mcp`

#### ① 一句话作用

把 MCP（Model Context Protocol）服务器里的工具注册进 `ToolRegistry`，命名空间 `mcp__<server>__<tool>`。

#### ② 关键源码位置

- `tool/mcp/McpToolAdapter.java`（单工具适配器）
- `tool/mcp/McpBootstrap.java`（启动器：读 `mcp-server.json`，spawn 客户端，注册工具）
- `tool/mcp/McpServerConfig.java`（配置）
- `tool/mcp/IMcpClient.java`（客户端接口，infra `StdioMcpClient` 实现）

#### ③ 与谁打交道

- **依赖**：JSON-RPC 2.0 via stdio；`McpBootstrap` 在 Agent 创建时触发。
- **被使用**：`ToolCallExecutor`（`mcp__*`）。
- **不协作**：`plugin.bridge`（MCP 与插件 Bridge 互不打通——已知隐患）。

#### ④ 注意事项

- MCP 客户端是 spawn 子进程；进程管理归属 `McpBootstrap`。
- 协议版本严格依赖 MCP spec 演进，需要版本钉住。

---

### 5.3.2 `tool.plugin`

#### ① 一句话作用

把 `plugin.runtime` 暴露的 `PluginToolDefinition` 包成标准 `Tool`，注册到 `ToolRegistry`（前缀 `plugin__<pluginId>__`）。

#### ② 关键源码位置

- `tool/plugin/PluginToolBridgeService.java`
- `tool/plugin/IPluginToolBridge.java`
- `tool/plugin/PluginToolDefinition.java`

#### ③ 与谁打交道

- **被使用**：`ToolCallExecutor`（`plugin__<id>__<tool>`）。
- **依赖**：`plugin.runtime.PluginRuntimeService`（提供 `ToolDefinition` 列表）。

#### ④ 注意事项

- 启动时同步注册到 `ToolRegistry` + `ToolCatalogPort`（避免 enabledTools 子集校验漏）。

---

## 5.4 平台工具族（7 个，把领域能力包成工具）

> 这 7 个族都遵循相同模式：**领域服务实现 + 工具族壳**，每个族只有 1-2 个工具类。下表汇总：

### 5.4.1 `tool.jobs`（后台任务）

#### ① 一句话作用

把 `jobs.JobRegistryService` 的能力（运行 / 列表 / 输出 / 终止）包成 4 个工具：`job_run` / `job_list` / `job_output` / `job_kill`。

#### ② 关键源码

- `tool/jobs/JobRunTool.java`、`JobListTool.java`、`JobOutputTool.java`、`JobKillTool.java`
- 依赖 `jobs.service.JobRegistryService`

#### ④ 与谁打交道

- **消费**：`tool.service.ToolCallExecutor` 派发
- **依赖**：`jobs` 域（**InMemory 仓储，已知隐患**）

#### ⑤ 注意事项

- 重启即丢，与 `schedule` 同属"未真正生产化"模块。

---

### 5.4.2 `tool.terminal`（终端）

#### ① 一句话作用

把 `terminal` 域的"打开终端 / 发送命令 / 读取输出 / 看快照"包成工具集。

#### ② 关键源码

- `tool/terminal/TerminalTools.java`
- 依赖 `terminal.service.TerminalService`

#### ④ 与谁打交道

- **依赖**：`terminal` 域（`ITerminalCommandService` / `ITerminalQueryService`）。

#### ⑤ 注意事项

- 与 `shell_execute` 不同：终端是**会话级**的（持续 stdin/stdout），shell 是一次性命令。

---

### 5.4.3 `tool.schedule`（定时任务）

#### ① 一句话作用

把 `schedule.ScheduleService` 包成 `schedule_*` 工具。

#### ② 关键源码

- `tool/schedule/ScheduleTools.java`
- 依赖 `schedule.service.ScheduleService`

#### ④ 与谁打交道

- **依赖**：`schedule` 域（**ScheduleService.drive() 形同虚设，已知隐患**）。

---

### 5.4.4 `tool.goal` / `tool.lsp` / `tool.session` / `tool.skill`

> 这 4 个族都遵循同构："工具壳 + 领域服务依赖"，按字面理解即可。

| 工具族 | 工具类 | 领域依赖 |
| --- | --- | --- |
| `tool.goal` | `tool/goal/GoalTools.java` | `goal.service.GoalService` |
| `tool.lsp` | `tool/lsp/LspTool.java` | `lsp.service.LspService`（LSP hover / references） |
| `tool.session` | `tool/session/SessionSearchTool.java` | `session.conversation.service.ConversationQueryService` |
| `tool.skill` | `tool/skill/SkillTool.java` | `skill.service.SkillService` |

#### 共同注意事项

- 这些"thin 工具族"职责单一，只做参数对齐 + 调用底域 + 包装 ToolResult。
- 若新增领域能力（`X`），习惯做法是新增 `tool/X/XTools.java` + 新增 `agent`/`chapter` 文档入口。

---

## 小结

1. **ToolCallExecutor 是工具唯一入口**：参数解析 → 守卫 → hook → 审批 → 派发 → 回填。
2. **三类工具通过前缀名空间** 在 `ToolRegistry` 内部路由：`fs_read` / `plugin__<id>__<tool>` / `mcp__<server>__<tool>`。
3. **运行时审批默认 allowAll**——审批只有在 `task.approval` 把危险工具拦截。
4. **12 个工具族组织同构**：`<族>/<族>Tool.java` + `<族>/adapter/port/I<族>Port.java`，新增一族时不要破坏这个约定。

---

## 思考题

1. **【Easy】** 给 `ToolRegistry` 加一个新工具 `echo`（参数 `{message}` 直接回显）。需要改哪些文件？注册时机是 Spring 启动时还是 Agent 创建时？
2. **【Medium】** 看 `BlockAssembler.assemble`，假设 chunk #3 网络丢了，前端会怎样？应该在哪里加"前向纠错"？
3. **【Hard】** 假如要让 `ToolCallExecutor` 支持"工具结果中间件"（每个工具结果再过一层 transform，比如脱敏），你打算塞在哪一层？参看 `IHookRunnerPort` 的 hook 机制。
