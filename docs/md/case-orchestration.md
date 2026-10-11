# Case Orchestration

## 1. 分类标准

Case 层只保留两种形态：

| 形态 | 适用场景 | 结构 |
| --- | --- | --- |
| 薄用例 | 单次查询、单次命令、简单映射 | `I*Case` + `*CaseImpl` |
| 流程用例 | 多步骤、跨领域服务、分支决策、状态流转 | `I*Case` + `*Factory` + `*Node` + `DynamicContext` |

不要为了“统一”把简单查询塞进策略树；也不要让复杂流程在 `CaseImpl` 里手写多步骤业务。

## 2. 通用组件

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `StrategyHandler` | `cases/orchestration` | 单个节点的统一执行契约 |
| `AbstractStrategyRouter` | `cases/orchestration` | 执行当前节点并路由下一节点 |
| `CasePipeline` | `cases/orchestration` | 统一创建上下文、执行根节点、转换异常、清理上下文 |
| `CasePipelineFactory` | `cases/orchestration` | 复杂流程 Factory 的统一契约 |
| `CaseGuard` | `cases/support` | 统一 Case 层入参校验 |

## 3. 当前代表链路

### Agent 消息

```
AgentUseCase
  → AgentMessageFactory
  → AgentResolveNode
  → AgentIntentNode
  → AgentDispatchNode
  → AgentCollectNode
```

同步和流式都经过同一棵策略树；流式的 delta/tool-call sink 由 `DynamicContext` 挂载和清理。

### 任务提交

```
SubmitHarnessTaskCaseImpl
  → SubmitHarnessTaskFactory
  → SubmissionRootNode
  → ProfileResolutionNode
  → ToolResolutionNode
  → PermissionCheckNode
  → EnqueueNode
```

### 插件激活 / 运行

```
ActivateHarnessPluginCaseImpl / RunHarnessPluginCaseImpl
  → PluginRuntimePipelineFactory
  → ResolvePluginNode
  → InspectBridgeNode
  → BindBridgeNode
  → ActivatePluginNode / RunPluginNode
```

激活和运行共享同一套插件解析、桥接检查与绑定步骤，只有终态节点不同。

## 4. 架构约束

- `factory` 包中的 `*Factory` 必须实现 `CasePipelineFactory`。
- `node` 包中的 `*Node` 必须继承 `AbstractStrategyRouter`。
- `CaseImpl` 不允许绕过 `Factory` 手写复杂流程。
- 薄用例不需要 `Factory`，避免无意义的单节点树。
