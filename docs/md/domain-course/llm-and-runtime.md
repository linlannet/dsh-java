# 第 4 章 · LLM 与运行时

> 本章覆盖：`llm`、`runtime.model`、`runtime.setting`、`runtime.tool`、`runtime.profile`。
> 解决的核心问题：**LLM 流式返回怎么投影成 UI / 模型设置与路由怎么协作？**

---

## 学习目标

读完本章你能：

1. 画出 `ILlmRuntimePort.stream()` 的数据流向图。
2. 列出 `runtime.model` / `runtime.setting` / `runtime.tool` / `runtime.profile` 四个子域各自的边界。
3. 区分"模型路由（route）"与"模型设置（setting）"的差异。
4. 知道 `runtime.profile` 怎么决定 Agent 的可用工具 / 交互形态 / System Prompt。

---

## 章节导读

本章讲两个主题：**LLM 调用抽象**（`llm`）+ **运行时配置**（`runtime` 的 4 个子域）。两者紧密相关：`runtime.setting` 保存模型渠道并维护全局 active 渠道 → `runtime.model` 按可选 `channelCode` 解析 → `llm.ILlmRuntimePort` 真正发请求。

```
                  +---------------------+
                  |  runtime.profile    | ← headless / web
                  +---------------------+
                              |
                  +---------------------+
                  |  runtime.setting    | ← 用户保存的偏好（DB）
                  +---------------------+
                              |
                  +---------------------+
                  |  runtime.model      | ← ModelRoutingService 选模型 + baseUrl
                  +---------------------+
                              |
                  +---------------------+
                  |  runtime.tool       | ← 工具目录（内置+插件+MCP）
                  +---------------------+
                              |
                              v
                       llm.ILlmRuntimePort
                              | stream(GenerateOptions)
                              v
                        LLM 网关
```

`llm` 是核心（用"完整 7 节"模板），4 个 `runtime` 子域是小模块（用"轻量 5 节"模板）。

---

## 4.1 `llm`：流式生成与重试

### ① 解决什么问题

LLM 调用对外是异步流式（`Flow.Publisher<StreamChunk>`），对内要把 chunk 拼成完整 `ContentBlock`，并把重试 / 续写 / 错误码都封装在 port 层（不污染 adapter）。这样业务侧只用"给 options，订阅 chunks"。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `service/LlmGenerationService.java` | 领域层 LLM 服务入口（包装 port 调用） |
| `service/BlockAssembler.java` | 把流式 chunk 拼成完整块 |
| `adapter/LlmAdapter.java` | 适配器骨架（infra 提供具体实现） |
| `adapter/port/ILlmRuntimePort.java` | 核心端口：`stream()` 返回 `Flow.Publisher<StreamChunk>` |
| `adapter/ResolvedRetryPolicy.java` | 重试策略 |
| `model/valobj/StreamChunk.java` | sealed 流式块 |
| `model/valobj/FinishReason.java` / `TokenUsage.java` / `LlmErrorCodes.java` / `LlmFailure.java` | 枚举与结果对象 |
| `model/valobj/LlmCallConfig.java` / `GenerateOptions.java` | 调用配置（temperature 可空） |

### ③ 关键签名

```java
// ILlmRuntimePort.java
public interface ILlmRuntimePort {
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);
    Optional<ContentBlock> generate(GenerateOptions options);    // 同步降级
}

// StreamChunk.java（sealed）
public sealed interface StreamChunk permits TextDelta, ToolCallDelta, Finish, Failure {
    String sessionId();
    int index();        // 同 assistant message 内的 chunk 序号
}

// GenerateOptions.java（注意：temperature 可空）
public record GenerateOptions(
    String model,
    List<Message> messages,
    List<ToolSchema> tools,
    Double temperature,    // null = 不发送（适配只接受 1 的网关）
    Integer maxTokens
) {}
```

### ④ `BlockAssembler` 把 chunk 拼成完整块

```java
public ContentBlock assemble(StreamChunk... chunks) {
    StringBuilder sb = new StringBuilder();
    for (StreamChunk c : chunks) {
        if (c instanceof TextDelta td) {
            sb.append(td.text());
        } else if (c instanceof ToolCallDelta tcd) {
            // 解析 tool_calls JSON 增量
            // ...
        }
    }
    return new ContentBlock(sb.toString(), parsedToolCalls, finishReason, usage);
}
```

### ⑤ 流程图

```
ReactLoopAgent.turn()
   ↓
ILlmRuntimePort.stream(options)
   ↓
   Flow.Publisher<StreamChunk> ─────────┐
   ↓                                    │
   ┌──chunk 1──┐──chunk 2──┐──...──┐   │
   ↓            ↓            ↓         │
   TextDelta    ToolCall     Finish    │
   → SessionEvent.AssistantChunk         │
   → SessionEvent.ToolCall               │
   → SessionEvent.AssistantMessage        │
                                          │
BlockAssembler.assemble(chunks) ←─────────┘
   ↓
完整 ContentBlock → ContentBlock → Messages
```

### ⑥ 设计意图

> **重试在 port 层，不在 adapter 层** —— infra 实现 `ILlmRuntimePort` 时统一处理 401/429/5xx 重试；adapter 只负责"翻译 HTTP → StreamChunk"。这样业务侧不需要给每个 LLM 厂商写重试。

### ⑦ 与谁打交道

| 方向 | 谁 | 干什么 |
| --- | --- | --- |
| 消费 | `agent.service.ReactLoopAgent.turn()` | 调用 stream |
| 消费 | `agent.compaction.BasicCompactionEngine` | summarize |
| 被实现 | `infra.llm.InMemoryLlmRuntimePort`（含重试） + `infra.llm.DeepSeekAdapter`（HTTP SSE 手解） | 提供真实能力 |
| 配置来源 | `runtime.setting.ModelSettingService` + `runtime.model.ModelRoutingService` | 决定 options |

### ⑧ 注意事项

1. **`temperature` 传 null = 不发送 temperature 字段**（适配只接受 `1` 的特殊网关）。硬编码非空会触发 400。
2. HTTP 总超时 **120 秒**（来自 `DeepSeekAdapter` 配置）；如超时，retry policy 接管。
3. 续写（max_tokens 截断后接上）最多 **4 次**，超过视为失败。
4. 重试在 `InMemoryLlmRuntimePort` 而非 `DeepSeekAdapter`，是层级清晰的体现。

---

## 4.2 `runtime.model`：模型路由

### ① 一句话作用

按请求指定的 `channelCode` 或全局 active 渠道，挑出“用哪个模型 + 用哪个 baseUrl + API key 怎么来”，封装成 `ModelProviderProfileVO` 给 LLM 调用使用。`headless` / `web` 只决定执行工具和审批策略，不参与模型渠道路由。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `runtime/model/service/ModelRoutingService.java` | 路由实现 |
| `runtime/model/adapter/port/IModelProviderPort.java` | 上游模型发现端口（infra 实现） |
| `runtime/model/model/valobj/ModelProviderProfileVO.java` | 提供方画像（含 baseUrl / apiKeyRef / modelName） |

### ③ 关键签名（伪代码）

```java
public ModelProviderProfileVO route(AgentOptions opts) {
    ModelSettingEntity setting = settingService.findActive(opts.sessionId());
    return providerPort.discover(setting);    // 拿上游 baseUrl + key
}
```

### ④ 与谁打交道

- **依赖**：`runtime.setting.ModelSettingService`（查用户设置）。
- **被依赖**：`llm.ILlmRuntimePort.stream(options)` 拿 ModelProviderProfileVO 拼 GenerateOptions。

### ⑤ 注意事项

- 模型发现不命中（agent 不认识这个 model）→ 走兜底（默认 `Deepseek-V4.0-Pro`）。

---

## 4.3 `runtime.setting`：模型设置

### ① 一句话作用

用户通过 `/api/harness/settings/models` 保存的"会话级模型偏好"：会话 → ModelSettingEntity 行（model 字段、apiKeyRef 字段）。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `runtime/setting/service/ModelSettingService.java` | 服务实现（CQRS） |
| `runtime/setting/IModelSettingCommandService.java` / `IModelSettingQueryService.java` | CQRS 接口 |
| `runtime/setting/model/entity/ModelSettingEntity.java` | 实体 |
| `runtime/setting/model/valobj/ModelSettingVO.java` | VO |
| `runtime/setting/adapter/repository/IModelSettingRepository.java` | 仓储端口 |

### ③ 与谁打交道

- **依赖**：infra `IModelSettingRepository` (MyBatis) 持久化到 `harness_model_setting` 表。
- **被依赖**：`runtime.model.ModelRoutingService`。
- **被消费**：trigger `/api/harness/settings/models/*`。

### ④ 注意事项

1. **模型配置以数据库为准**——`AgentModelOptionsResolver` 先读取 `harness_model_setting`，application.yml 只作为数据库缺省时的兜底和首次种子。
2. **`ModelSyncRunner` 只做按需初始化**——数据库已有 deepseek 默认行时不会覆盖控制台保存的设置。
3. `api_key_ref` 当前由模型设置链路透传给 LLM 适配器；字段名沿用历史命名，实际保存的是运行时使用的连接信息。

---

## 4.4 `runtime.tool`：工具目录

### ① 一句话作用

把"内置 + 插件 + MCP"三类工具统一暴露成 `ToolCatalogEntryEntity`，按会话请求的工具白名单过滤返回可用子集。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `runtime/tool/service/ToolResolutionService.java` | 解析入口（`IToolResolutionService`） |
| `runtime/tool/adapter/port/IToolCatalogPort.java` | 工具目录端口（infra `ToolCatalogPort`） |
| `runtime/tool/model/entity/ToolCatalogEntryEntity.java` | 目录实体 |
| `runtime/tool/model/valobj/ToolCatalogEntryVO.java` / `ToolSelectionVO.java` | VO |

### ③ 与谁打交道

- **依赖**：`tool.adapter.ToolRegistry`（实际工具定义）+ infra `ToolCatalogPort`（持久化）。
- **被依赖**：任务提交链（决定 enabledTools 子集）+ Agent 每次 turn 给 LLM 的 tools schema。

### ④ 注意事项

- `tool.plugin.PluginToolBridgeService` 把插件工具注册进 `ToolRegistry` 时也要同步进 `ToolCatalogPort`，否则 `enabledTools` 引用 `plugin__<id>__<tool>` 但目录里没有 → 提交被 `ToolSelectionFilter` 拒。

---

## 4.5 `runtime.profile`：执行画像

### ① 一句话作用

按用户/系统选的执行 profile（headless / web）决定 Agent 的可用工具、System Prompt 模板和审批/取消策略；模型统一从全局 active 渠道解析，也可以在请求中显式指定 `channelCode`。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `runtime/profile/service/ExecutionProfileService.java` | 解析 + 选择（顶层接口 `IExecutionProfileService`） |
| `runtime/profile/service/router/ExecutionProfileStrategy.java` | 策略接口 |
| `runtime/profile/service/router/HeadlessExecutionProfileStrategy.java` | headless 模式策略 |
| `runtime/profile/service/router/WebExecutionProfileStrategy.java` | web 模式策略 |
| `runtime/profile/model/valobj/ExecutionProfileVO.java` | 画像 VO |

### ③ 与谁打交道

- **依赖**：`shared.HarnessStatusEnumVO`（不是状态机，但是 UI 颜色编码共享）+ 多模块（`agent.ScopedSystemPromptAssembler` / `tool.MatrixRuntimeApprovalGate` / `runtime.model.ModelRoutingService`）。
- **被依赖**：`task.submission.ProfileCodeFilter`（校验 profile 合法）。

### ④ 注意事项

- 切换 profile 会触发 system prompt 重建 + 工具目录重建；但**不会**清空 `SessionLog`。

---

## 小结

1. **LLM 流式输出 → `BlockAssembler` 拼装 → ContentBlock → `SessionEvent` 落盘 → SSE 推送**。
2. **重试**在 port 层（`InMemoryLlmRuntimePort`），**翻译**在 adapter 层（`DeepSeekAdapter`）——分层清晰。
3. **`runtime` 四子域分工**：model（路由）/ setting（持久化）/ tool（目录）/ profile（策略）。四者协作给 LLM 调用提供完整的"环境"信息。
4. **`temperature` 字段可空 = 不发送**——是适配只接受 `1` 的网关的特殊设计。

---

## 思考题

1. **【Easy】** 给 `ToolRegistry` 加一个新工具 `echo`（参数 `{message}` 直接回显）。需要改哪些文件？注册时机是 Spring 启动时还是 Agent 创建时？
2. **【Medium】** 看 `BlockAssembler.assemble`，假设 chunk #3 网络丢了，前端会怎样？应该在哪里加"前向纠错"？
3. **【Hard】** 假如要让 `ToolCallExecutor` 支持"工具结果中间件"（每个工具结果再过一层 transform，比如脱敏），你打算塞在哪一层？参看 `IHookRunnerPort` 的 hook 机制。
