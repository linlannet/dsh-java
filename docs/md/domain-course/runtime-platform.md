# 第 8 章 · 运行时平台能力

> 本章覆盖：`coderuntime`、`e2b`、`credentials`、`lsp`、`sandbox`、`schedule`、`terminal`、`storage`、`typert`、`skill`（共 10 个小模块）。
> 解决的核心问题：**平台能力边界各自负责什么？**

---

## 学习目标

读完本章你能：

1. 一句话说明每个平台模块的职责，不混淆。
2. 知道哪些模块是"真实可用"的，哪些是"Stub"或"形同虚设"。
3. 知道 `sandbox` 模块是怎么把"session cwd + 模式"一起下发给所有强制能力的。

---

## 章节导读

这 10 个模块都是"平台能力"——给 Agent 提供基础设施（沙箱 / 存储 / 凭据 / 类型注册）而不是直接参与 turn 循环。本章统一用"**轻量 5 节**"模板（每个模块各 5 小节），避免每个都全套 7 节导致本章过长。

> 阅读建议：先看依赖图找方位，再按模块顺序略读。

```
+--------------+        +------------+        +------------------+
| sandbox      |        | credentials|        | typert           |
| (策略决议)    |        | (凭据提供)  |        | (远程类型注册)    |
+--------------+        +------------+        +------------------+

+--------------+        +------------+        +------------------+
| storage      |        | lsp        |        | skill            |
| (KV 抽象)     |        | (语言服务)  |        | (技能系统)        |
+--------------+        +------------+        +------------------+

+--------------+        +------------+        +------------------+
| coderuntime  |        | e2b        |        | terminal         |
| (代码运行时)   |        | (沙箱,Stub) |        | (终端会话)        |
+--------------+        +------------+        +------------------+

                                                 +------------+
                                                 | schedule   | ← 形同虚设
                                                 +------------+
```

---

## 8.1 `coderuntime`：代码运行时

### ① 一句话作用

把"代码块"提交到一个隔离运行时的端口，infra 可对接外部沙箱（如 Docker / Firecracker）。

### ② 关键源码位置

- `coderuntime/service/CodeRuntimeService.java` / `ICodeRuntimeService.java`
- `coderuntime/adapter/port/ICodeRuntimePort.java`
- `coderuntime/model/valobj/CodeBindNamespace.java` / `CodeRunRequest.java` / `CodeRunResult.java`

### ③ 关键类

```java
public interface ICodeRuntimePort {
    CodeRunResult run(CodeBindNamespace ns, CodeRunRequest req);  // 在给定 ns 下跑代码
}
```

### ④ 与谁打交道

- **依赖**：infra `ICodeRuntimePort`（外部沙箱）。
- **被依赖**：第 9 章 `acp` / `sdk` 的"代码执行"路径。

### ⑤ 注意事项

- 与 `e2b` 的关系：`coderuntime` 是抽象，`e2b` 是其中一个具体沙箱实现。

---

## 8.2 `e2b`：E2B 沙箱

### ① 一句话作用

对接 E2B（外部 SaaS 沙箱）的 subprocess + filesystem 端口。当前**只有 Stub**。

### ② 关键源码位置

- `e2b/service/E2BSandboxService.java` / `IE2BSandboxService.java`
- `e2b/adapter/port/IE2BSubprocessPort.java` / `IE2BFsPort.java`
- `e2b/model/valobj/E2BSandboxEnv.java` / `E2BExecResult.java`

### ③ 关键类

```java
public interface IE2BFsPort {
    byte[] read(String remotePath);
    void   write(String remotePath, byte[] content);
}
public interface IE2BSubprocessPort {
    E2BExecResult exec(String command, Duration timeout);
}
```

### ④ 与谁打交道

- **被依赖**：`coderuntime` 如果选 E2B 实现，会用这两个 port。
- **真实状态**：Stub，**已知隐患 #6**（`E2BSandboxService` 当前实现返回 mock result）。

### ⑤ 注意事项

- 不要在生产流程里依赖 `e2b` 路径。

---

## 8.3 `credentials`：凭据管理

### ① 一句话作用

把"凭据引用"（`CredentialRef`）解析成"凭据值"（`ResolvedCredentialVO`），提供统一的 `ICredentialProviderPort` 给 LLM / 插件使用。

### ② 关键源码位置

- `credentials/service/CredentialService.java` / `ICredentialService.java`
- `credentials/adapter/port/ICredentialProviderPort.java`
- `credentials/model/valobj/CredentialRef.java` / `CredentialInfoVO.java` / `ResolvedCredentialVO.java`

### ③ 关键类

```java
public interface ICredentialProviderPort {
    ResolvedCredentialVO resolve(CredentialRef ref);    // 引用 → 真实凭据
}
```

### ④ 与谁打交道

- **被依赖**：`llm.ILlmRuntimePort`（凭 API key）、`plugin.runtime`（凭插件认证信息）。
- **实现**：infra `ICredentialProviderPort`（明文 yml / KMS / vault 各种实现）。

### ⑤ 注意事项

- 当前最常用的实现是"明文 yml 兜底"——生产化前必须切到 KMS / vault。
- **不要**让 `agent` 持有真实 key，只能持有 `CredentialRef`。

---

## 8.4 `lsp`：语言服务

### ① 一句话作用

抽象 Language Server Protocol 的能力（location / hover / references），让 Agent 能做"代码定位"。

### ② 关键源码位置

- `lsp/service/LspService.java` / `ILspService.java`
- `lsp/adapter/port/ILspProviderPort.java`
- `lsp/model/valobj/LspQueryType.java` / `LspLocation.java` / `LspQueryResult.java`

### ③ 与谁打交道

- **被使用**：`tool.lsp.LspTool`（`lsp_query` / `lsp_location`）。
- **依赖**：infra `ILspProviderPort`（可对接内嵌的 jdtls / tsserver / pyright）。

### ④ 注意事项

- 注意与 `tool.lsp.LspTool` 的层级：`lsp/` 是能力抽象，`tool/lsp/` 是工具壳。

---

## 8.5 `sandbox`：沙箱策略

### ① 一句话作用

每次工具执行都 `resolve` 一份 `SandboxExecutionPolicy`（`mode` + `cwd`），让 session 模式 + 不可变 cwd 一起下发到所有强制能力（fs / shell / terminal / e2b ...）。

### ② 关键源码位置

- `sandbox/service/SandboxPolicyService.java` / `ISandboxPolicyService.java`
- `sandbox/model/valobj/SandboxMode.java` / `SandboxPolicyRequest.java` / `SandboxExecutionPolicy.java`

### ③ 关键类

```java
public class SandboxPolicyService implements ISandboxPolicyService {
    public SandboxExecutionPolicy resolve(SandboxPolicyRequest request) {
        SandboxMode mode = request.modeOverride().orElse(defaultMode);
        Path       root = request.sessionCwd().orElse(fallbackWorkspaceRoot);
        return new SandboxExecutionPolicy(mode, root);
    }
}
```

### ④ 与谁打交道

- **被依赖**：`tool.fs.*` / `tool.shell.ShellExecuteTool` / `terminal` 等所有"会动文件系统"的工具族。

### ⑤ 注意事项

- **当前实现的 `mode` 只有枚举 + cwd 落点**，并没有真正在 OS 级别上做 chroot / seccomp——是"逻辑沙箱"，不是"OS 沙箱"。

---

## 8.6 `schedule`：调度任务

### ① 一句话作用

定义 + 持久化 + 调用"按时间规则触发"的任务。

### ② 关键源码位置

- `schedule/service/ScheduleService.java` / `IScheduleService.java`
- `schedule/model/entity/ScheduleRecord.java`
- `schedule/model/valobj/ScheduleKind.java` / `ScheduleState.java` / `ScheduleView.java`
- `schedule/adapter/repository/IScheduleRepository.java`

### ③ 与谁打交道

- **被依赖**：`tool.schedule.ScheduleTools`。

### ④ 注意事项

1. **`ScheduleService.drive()` 当前无调用方**（无 `@Scheduled`/Quartz）——已知隐患 #3，**形同虚设**。
2. 仓储 `InMemory`，重启即丢。
3. 计划自动化要靠外部定时器（cron）触发 `drive(...)`。

---

## 8.7 `terminal`：终端交互

### ① 一句话作用

抽象"打开终端会话 / 发送命令 / 读取输出 / 拿快照"四件事，给 `tool.terminal` 做底层支撑。

### ② 关键源码位置

- `terminal/service/TerminalService.java` / `ITerminalCommandService.java` / `ITerminalQueryService.java`
- `terminal/adapter/port/ITerminalPort.java`
- `terminal/model/valobj/TerminalSessionSnapshotVO.java` / `TerminalSendResultVO.java` / `TerminalReadResultVO.java` / `TerminalSessionStatus.java`

### ③ 与谁打交道

- **被依赖**：`tool.terminal.TerminalTools`。
- **依赖**：infra `ITerminalPort`（可用 pty / process 模拟）。

### ④ 注意事项

- 终端会话 vs `shell_execute`：终端**会话级**持续，shell 一次性。

---

## 8.8 `storage`：KV 存储抽象

### ① 一句话作用

给领域内其他模块提供"键值存取"的能力（`KvSnapshot` / `KvUnitDescriptor`），infra 可对接 Redis / RocksDB / MySQL。

### ② 关键源码位置

- `storage/service/StorageService.java` / `IStorageService.java`
- `storage/adapter/port/IStorageBackendPort.java`
- `storage/model/valobj/KvSnapshot.java` / `KvUnitDescriptor.java` / `StorageErrorCode.java` / `StorageException.java`

### ③ 与谁打交道

- **被依赖**：未来 Agent 状态 / 缓存 / 检查点。当前 domain 内还没有被重度使用，主要给上层 trigger / infra 用。

### ④ 注意事项

- 错误码统一在 `StorageErrorCode`，**别用字符串**抛错。

---

## 8.9 `typert`：类型注册协议

### ① 一句话作用

"远程类型注册"协议：把一份类型描述（`TypertContribution`）注册到中心、跨进程查找（`ITypertLookupRegistry`）。给插件系统一个"类型发现"机制。

### ② 关键源码位置

- `typert/service/TypertService.java` / `ITypertService.java`
- `typert/adapter/port/ITypertRegistryContract.java` / `ITypertLocalRegistry.java` / `ITypertRemoteRegistry.java` / `ITypertLookupRegistry.java` / `ITypertContextRegistry.java`
- `typert/model/entity/TypertContribution.java` / `InvocationDescriptor.java`
- `typert/model/valobj/TypertFace.java` / `RemoteResult.java` / `RemoteFailure.java`

### ③ 与谁打交道

- **被依赖**：理论上 `plugin` / `tool` 模块可消费；当前**未深度对接**。

### ④ 注意事项

- 多种 Registry（Local / Remote / Lookup / Context）分得细，但目前实现都较薄；未来要做"跨进程插件发现"时这套是基础。

---

## 8.10 `skill`：技能系统

### ① 一句话作用

维护"技能"（`SkillDefinition`）的定义、来源（`SkillSource`）、调用策略（`SkillInvocationPolicy`）；让 Agent 在合适时机主动调用技能。

### ② 关键源码位置

- `skill/service/SkillService.java` / `ISkillService.java`
- `skill/adapter/port/ISkillProviderPort.java`
- `skill/model/entity/SkillDefinition.java` / `SkillSummary.java` / `SkillCandidate.java`
- `skill/model/valobj/SkillSource.java` / `SkillInvocationPolicy.java`

### ③ 与谁打交道

- **被依赖**：`tool.skill.SkillTool`。
- **依赖**：infra `ISkillProviderPort`（可对接文件系统 / HTTP registry）。

### ④ 注意事项

- 当前不强制"skill 比 prompt 强"，更多是"能力命名空间"。
- 未来要让 skill 自动出现在 system prompt，需在 `agent.ScopedSystemPromptAssembler` 注入。

---

## 小结

1. **10 个平台模块**对应 10 种底层能力，**没有共同父类 / 接口**，按需取用。
2. **真生产隐患**：`e2b` Stub / `schedule` 无驱动 / `credentials` 明文 / `sandbox` 仅逻辑隔离。
3. **`sandbox.resolve(...)` 是核心枢纽**：所有动文件系统的能力（fs / shell / terminal）都要它返回 cwd + mode。

---

## 思考题

1. **【Easy】** 想给 `sandbox` 加一个 `mode=CONTAINER` ——让 `IFsPort` 真的在 Docker 里运行。改动会蔓延到几个文件？
2. **【Medium】** 给 `schedule.ScheduleService.drive()` 接上一个 `@Scheduled(cron = "0 0/5 * * * ?")` 会发生什么？仓储 InMemory 状态下重启任务会丢；想保留需要先做什么？
3. **【Hard】** 想给 `plugin` 添加"跨进程插件发现"——复用 `typert.remoteRegistry`。看 `ITypertRegistryContract` 与 `IPluginArtifactInstallerPort`，讨论方案。
