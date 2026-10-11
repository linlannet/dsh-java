# DeepSeek Harness Java v0.1.9 开发说明

> v0.1.9 的主线是**核心底座对齐**：参照 deepseek-harness（TS/Python）参考实现，系统性补齐 dsh-java 缺失的核心架构特征——Scope 路由与事件分发、Waterfall 拦截链（LLM/SystemPrompt）、五阶段工具管线、Session V4 格式迁移链、投影框架与写后合流缓存、SessionPersistenceSeam 正式化、LLM Stream Waterfall 拦截器（retry/replay/routing），以及 Sonatype 中央仓库发布元数据。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.9` |
| 状态 | Phase 1 核心底座对齐进行中（T1.001-T1.025 完成，T1.026+ 待续） |
| 基线 | v0.1.8（Agent 运行期安全控制与可观测性增强） |
| 主线 | Scope 路由、Waterfall 拦截链、五阶段工具管线、Session V4 迁移、投影框架+缓存、PersistenceSeam、LLM Stream Waterfall |
| 架构 | 继续保持 DDD 六边形架构，不改变依赖方向 |
| 对齐规格 | 104 条 EARS 需求 / 19 域 / 109 原子任务（Phase 1: 36 / Phase 2: 35 / Phase 3: 38） |
| 新增类 | 28 个（domain 28） |
| 新增测试 | 75 个（domain 67 + infrastructure 8） |
| 全量测试 | domain 173 + infrastructure 45 = 218 通过，0 回归 |
| 新增依赖 | 无 |

## 2. 版本亮点

1. **Scope 路由与事件分发**：`ScopeKey`/`ScopedEvent`/`ScopeEventDispatcher` 实现会话级事件路由；`ReactLoopAgent` 在 turn/step 全生命周期关键点分发 scope 事件（turn/start、turn/end×7、step/start、step/end、user/message、assistant/message×3、request/header、assistant/chunk-batch）。
2. **Waterfall 拦截链**：`WaterfallChain<T>`/`WaterfallExpert<T>` 通用拦截链执行器，按 priority 升序执行，支持运行时注册；`WaterfallOutcome` 区分 Continue/Terminate，`canTerminate=false` 专家返回 Terminate 时降级为 Continue。
3. **System Prompt waterfall**：`ScopedSystemPromptAssembler` 重构为 `WaterfallChain<PromptAssembly>` 模式，`PromptFragment`/`SectionExpert` 按片段优先级组装系统提示词。
4. **五阶段工具管线**：`ToolCallExecutor` 引入 `extraInterceptors` 和 `buildPipeline`/`buildCallContext`/`addPhaseInterceptor` 方法，实现 pre→guard→around→post→result 五阶段洋葱模型拦截；AROUND 拦截器按 priority 大的在外层（先进入后退出）。
5. **Session V4 格式迁移链**：`SessionFormatVersion`（CURRENT=4）+ `SessionFormatMigration` 接口 + V0→V1→V2→V3→V4 四步纯函数迁移 + `MigrationCatalog` 管理注册与执行；每步幂等、仅升级、不修改输入；`SessionRebuilderService` 集成迁移链。
6. **投影框架与写后合流缓存**：`ProjectionType`/`ProjectionProvider<T>`/`ProjectionRegistry` 类型表与 Provider 契约；`ProjectionCache` 用 `ConcurrentHashMap` + CAS 实现写后合流，`onEventAppended` 异步触发增量合并，`lastEventStepIndex` 前进幂等；`SurfaceProjector` 改造为 `ProjectionProvider<List<SurfaceNode>>`。
7. **LegacyEventAdapter 增强**：`tolerateAndValidate` 容忍孤儿 ToolResult 节点；`detectFailedStep` 检测 TurnEnd Error reason；`markRecoverable` 幂等恢复标记。
8. **SessionPersistenceSeam 正式化**：标记接口 `SessionPersistenceSeam extends ISessionEventStore`，`JsonlSessionEventStore`/`JdbcSessionEventStore` 声明实现 seam；`SessionEventLogService` 依赖从 `ISessionEventStore` 改为 seam；`SessionPersistenceContractTest` 抽象契约测试基类（11 用例），两种 Store 实现跑同一套件。
9. **LLM Stream Waterfall**：`LlmStreamWaterfall` 用 `WaterfallChain<LlmStreamHandoff>` 包装 `ILlmRuntimePort.stream`；三个拦截器：`LlmRetryInterceptor`（priority=10, 可重试不终止）、`LlmReplayInterceptor`（priority=20, 注入历史 chunk 不终止）、`LlmRoutingInterceptor`（priority=30, 重定向到新 Provider 并终止链，保留原始请求参数与 trace）。
10. **Sonatype 中央仓库元数据**：6 个根 pom.xml 添加 `<url>`/`<licenses>`(Apache-2.0)/`<scm>`/`<developers>` 元数据，满足 Maven Central 发布要求。

## 3. 架构变化

v0.1.9 不引入新的 Maven 模块，也不调整依赖方向。变化集中在 domain 层内部新增包和类：

```text
domain/
  core/
    scope/          ← T1.001-T1.005: ScopeKey, ScopedEvent, ScopeEventDispatcher, IScopeSubscriptionPort
    waterfall/      ← T1.006-T1.007: WaterfallChain, WaterfallExpert, WaterfallOutcome/Event/Context
  agent/
    service/run/    ← T1.014: ReactLoopAgent Scope 事件分发
    service/        ← T1.008: ScopedSystemPromptAssembler waterfall 改造
  tool/
    service/        ← T1.009-T1.013: ToolCallExecutor 五阶段管线
  session/
    event/
      model/valobj/ ← T1.015: SessionFormatVersion
      service/
        migration/  ← T1.016-T1.018: V0ToV1~V3ToV4, MigrationCatalog, MigrationSupport
      adapter/repository/ ← T1.024: SessionPersistenceSeam
    projection/     ← T1.020-T1.023: ProjectionType, ProjectionProvider, ProjectionRegistry, ProjectionCache
  llm/
    waterfall/      ← T1.025: LlmStreamWaterfall, LlmStreamHandoff, LlmRetry/Replay/RoutingInterceptor
```

| 层 | 新增或调整 |
| --- | --- |
| Domain | 新增 28 个类（core/scope 4、core/waterfall 4、session/event/migration 7、session/projection 5、llm/waterfall 5、session/event/adapter 1、其他 2）；增强 8 个类（ReactLoopAgent、ScopedSystemPromptAssembler、ToolCallExecutor、SessionHeader、SessionEventLogService、SurfaceProjector、LegacyEventAdapter、SessionRebuilderService） |
| Infrastructure | 修改 2 个类（JsonlSessionEventStore/JdbcSessionEventStore implements SessionPersistenceSeam）；新增契约测试基类 + Jsonl 契约测试 |
| POM | 6 个根 pom.xml 添加 Sonatype 发布元数据 |

## 4. Scope 路由与事件分发

### 4.1 核心抽象

| 类 | 说明 |
| --- | --- |
| **ScopeKey** | scope 路由键（agentId + scopePath），record 值对象 |
| **ScopedEvent** | scope 事件（scopeKey + type + payload），record 值对象 |
| **ScopeSubscription** | scope 订阅（scopeKey + token + filter），record 值对象 |
| **IScopeSubscriptionPort** | scope 订阅端口接口 |
| **ScopeEventDispatcher** | scope 事件分发服务，按 ScopeKey 路由事件到订阅者 |
| **InMemoryScopeSubscriptionPort** | 内存适配器实现 |

### 4.2 ReactLoopAgent 集成

`ReactLoopAgent` 新增 `scopeEventDispatcher` 可空字段和 `setScopeEventDispatcher` setter。在 turn() 和 step() 的所有关键事件点添加 dispatch 调用：

| 事件点 | 事件类型 |
| --- | --- |
| turn 进入 | turn/start |
| turn 结束 | turn/end（completed/max_steps/aborted/blocked/error/natural/exception 共 7 种） |
| step 进入 | step/start |
| step 结束 | step/end |
| 用户消息 | user/message |
| 助手消息 | assistant/message（error/partial/normal 共 3 种） |
| 请求头 | request/header |
| chunk 批次 | assistant/chunk-batch |

dispatcher 为空时退化为原有行为（向后兼容）。

## 5. Waterfall 拦截链

### 5.1 核心抽象

| 类 | 说明 |
| --- | --- |
| **WaterfallChain\<T\>** | 拦截链执行器，按 priority 升序执行专家，`CopyOnWriteArrayList` 支持运行时注册 |
| **WaterfallExpert\<T\>** | 专家接口：`priority()`/`canTerminate()`/`handle(event, current)` |
| **WaterfallOutcome** | sealed interface：`Continue<T>`（继续传递）/ `Terminate<T>`（终止链） |
| **WaterfallEvent\<T\>** | 链中传递的事件（type + payload + context） |
| **WaterfallContext** | 执行上下文（currentExpertIndex + executedExperts + metadata） |

### 5.2 终止语义

- `canTerminate=true` 专家返回 `Terminate` → 链终止
- `canTerminate=false` 专家返回 `Terminate` → 降级为 `Continue`（输出仍传递）

### 5.3 System Prompt waterfall

`ScopedSystemPromptAssembler` 重构为 `WaterfallChain<PromptAssembly>` 模式：
- `PromptFragment`：提示词片段（section + content + priority）
- `SectionExpert`：按片段优先级组装的 waterfall 专家

## 6. 五阶段工具管线

### 6.1 设计

`ToolCallExecutor` 引入五阶段洋葱模型拦截：

| 阶段 | 说明 | canTerminate |
| --- | --- | --- |
| PRE | 请求前置处理 | false |
| GUARD | 准入校验，可拒绝 | true |
| AROUND | 包裹执行，可短路 | true |
| POST | 响应后置处理 | false |
| RESULT | 结果改写 | false |

### 6.2 实现

| 方法 | 说明 |
| --- | --- |
| `addPhaseInterceptor` | 运行时注册阶段拦截器 |
| `buildPipeline` | 构建五阶段管线 |
| `buildCallContext` | 构建调用上下文 |

AROUND 拦截器嵌套方向：从前往后嵌套，priority 大的在外层（先进入、后退出）。

## 7. Session V4 格式迁移链

### 7.1 核心抽象

| 类 | 说明 |
| --- | --- |
| **SessionFormatVersion** | record，CURRENT=4，`of()`/`value()`/`needsMigration()`/`isCurrent()` |
| **SessionFormatMigration** | 接口，`fromVersion()`/`toVersion()`/`migrate(SessionLog)` |
| **MigrationSupport** | 包级私有辅助工具 |
| **V0ToV1Migration** ~ **V3ToV4Migration** | 四步迁移实现，每步纯函数、仅升级、幂等 |
| **MigrationCatalog** | 迁移注册与执行，`defaultChain()`/`register()`/`migrateToCurrent()` |
| **UnsupportedFormatException** | 版本不支持异常 |

### 7.2 SessionHeader 变更

`SessionHeader` 新增 `formatVersion` 字段（int，默认 0），添加向后兼容构造函数。`JsonlSessionEventStore` 读写 formatVersion。

### 7.3 集成

`SessionRebuilderService` 新增 `MigrationCatalog` 字段和 setter，`rebuildAndCache` 中执行迁移链。

## 8. 投影框架与写后合流缓存

### 8.1 类型表与 Provider 契约

| 类 | 说明 |
| --- | --- |
| **ProjectionType** | record 值对象，预定义 SURFACE/STATISTICS/TURN_OUTLINE |
| **ProjectionProvider\<T\>** | 投影提供者端口：`type()`/`projectionClass()`/`initial()`/`applyIncremental(current, event)` |
| **ProjectionRegistry** | 投影注册与路由服务，`register()`/`resolve()`/`registeredTypes()`/`size()` |

### 8.2 ProjectionCache

| 方法 | 说明 |
| --- | --- |
| `get(type)` | 读取缓存条目 |
| `put(type, entry)` | 直接写入 |
| `computeIfAbsent(type, supplier)` | 不存在则计算 |
| `onEventAppended(type, event)` | 异步触发写后合流（单类型） |
| `onEventAppended(event)` | 异步触发写后合流（所有已注册类型） |
| `onEventAppendedSync(type, event)` | 同步合流（测试用） |

合流逻辑：读取 `lastEventStepIndex`，若 `event.seq() > lastEventStepIndex` 则调 `ProjectionProvider.applyIncremental` 增量合并并 CAS 更新缓存，否则跳过（幂等）。用 `ConcurrentHashMap` + `AtomicReference` CAS 保证并发安全。合流异步触发，不阻塞事件追加主流程；合流失败不阻塞主流程。

### 8.3 SurfaceProjector 改造

`SurfaceProjector` 实现 `ProjectionProvider<List<SurfaceNode>>`：
- `type()` → `ProjectionType.SURFACE`
- `initial()` → 空列表
- `applyIncremental(current, event)` → 增量合流单事件
- 保留 `project()`/`extractChatMessages()` 向后兼容

### 8.4 SessionEventLogService 集成

`SessionEventLogService` 新增 `ProjectionCache` 可空字段和 setter。`append()`/`appendBatch()` 后触发 `projectionCache.onEventAppended(event)`。

## 9. LegacyEventAdapter 增强

| 方法 | 说明 |
| --- | --- |
| `tolerateAndValidate` | 验证并容忍孤儿 ToolResult 节点 |
| `detectFailedStep` | 扫描 TurnEnd Error reason，返回 FailedStepDetection |
| `markRecoverable` | 幂等恢复标记 |

`ReactLoopAgent` 新增 `detectFailedStep()` 方法和 `FailedStepRecovery` record。

## 10. SessionPersistenceSeam 正式化

### 10.1 标记接口

`SessionPersistenceSeam extends ISessionEventStore` — 标记接口，区分正式持久化适配器与临时 stub。`JsonlSessionEventStore`/`JdbcSessionEventStore` 声明实现该 seam。`SessionEventLogService` 依赖从 `ISessionEventStore` 改为 `SessionPersistenceSeam`。

### 10.2 契约测试

`SessionPersistenceContractTest` 抽象基类，11 个契约用例：

| 用例 | 说明 |
| --- | --- |
| headerRoundTrip | saveHeader + getHeader 往返一致 |
| getHeaderNonExistentReturnsEmpty | 不存在返回 empty |
| appendAndReadAll | 追加事件可读 |
| appendBatchAndReadAll | 批量追加可读 |
| readAllNonExistentReturnsEmpty | 不存在返回空列表 |
| readAfter | 返回指定 seq 之后的事件 |
| getLastSeq | 返回最后事件序号 |
| getLastSeqNonExistent | 不存在返回 -1 |
| count | 返回事件数 |
| listHeaders | 返回已保存的 header |
| saveHeaderIdempotent | 重复保存不报错 |

`JsonlSessionPersistenceContractTest` 继承基类，对 `JsonlSessionEventStore` 跑全部契约用例。

## 11. LLM Stream Waterfall

### 11.1 核心抽象

| 类 | 说明 |
| --- | --- |
| **LlmStreamHandoff** | waterfall 链中传递的交接上下文（options/streamSupplier/prefixChunks/retryCount/metadata） |
| **LlmStreamWaterfall** | 用 `WaterfallChain<LlmStreamHandoff>` 包装 `ILlmRuntimePort.stream`，含 `ConcatPublisher`（前置 chunk + 底层流拼接） |

### 11.2 三个拦截器

| 拦截器 | priority | canTerminate | 行为 |
| --- | --- | --- | --- |
| **LlmRetryInterceptor** | 10 | false | 包装流为重试逻辑，错误时自动重发，最多 maxRetries 次 |
| **LlmReplayInterceptor** | 20 | false | 从 replaySource 获取历史 chunk，注入为 prefixChunks |
| **LlmRoutingInterceptor** | 30 | true | 按 router 函数重定向到新 Provider，更新 streamSupplier，保留原始参数与 trace，终止链 |

### 11.3 执行流程

```
LlmStreamWaterfall.execute(options)
  → 初始 handoff（options + streamSupplier=() -> runtime.stream(options)）
  → WaterfallChain.execute（retry → replay → routing 依次介入）
  → 最终 handoff 的 prefixChunks + streamSupplier.get()
  → ConcatPublisher（先发射 prefixChunks，再转发底层流）
```

## 12. Sonatype 中央仓库元数据

6 个根 pom.xml（无 `<parent>` 标签）添加以下元数据：

| 元素 | 内容 |
| --- | --- |
| `<url>` | `https://github.com/linlannet/dsh-java` |
| `<licenses>` | Apache-2.0 |
| `<scm>` | gitee URL + connection |
| `<developers>` | linlan（linlannet@163.com / Linlan Corporation） |

涉及文件：
1. `dsh-java/pom.xml`
2. `dsh-java-ontology/pom.xml`
3. `dsh-java-plugins/pom.xml`
4. `dsh-java/plugins/dsh-plugin-archetype/pom.xml`
5. `dsh-java/plugins/dsh-plugin-archetype/src/main/resources/archetype-resources/pom.xml`
6. `dsh-java/plugins/example-java-plugin/pom.xml`

## 13. 测试统计

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| ScopeEventDispatcherTest | 16 | Scope 事件分发 |
| ScopeArchitectureTest | 2 | Scope 架构边界 |
| WaterfallChainTest | 7 | Waterfall 链执行 |
| ScopedSystemPromptAssemblerWaterfallTest | 7 | System Prompt waterfall |
| ToolPipelineTest | 7 | 五阶段工具管线 |
| ToolPipelineIntegrationTest | 8 | 工具管线集成 |
| ReactLoopAgentScopeDispatchTest | 3 | ReactLoopAgent Scope 分发 |
| SessionFormatVersionTest | 8 | 格式版本 |
| SessionFormatMigrationTest | 16 | 迁移步骤 |
| MigrationCatalogTest | 21 | 迁移链幂等性与回归 |
| LegacyEventAdapterTest | 5 | 旧版事件适配 |
| ProjectionRegistryTest | 6 | 投影注册 |
| ProjectionCacheTest | 11 | 投影缓存 |
| ProjectionMergeIntegrationTest | 7 | 投影合流集成 |
| LlmStreamWaterfallTest | 7 | LLM Stream Waterfall |
| JsonlSessionPersistenceContractTest | 11 | 持久化契约 |
| **合计** | **218** | domain 173 + infrastructure 45 |

## 14. 兼容性变化

| 变化 | 兼容性 |
| --- | --- |
| `SessionHeader` 新增 `formatVersion` 字段 | 旧 8 参构造保留，默认 formatVersion=0 |
| `SurfaceProjector` 改为 `final` 并实现 `ProjectionProvider` | `project()`/`extractChatMessages()` 签名不变 |
| `SessionEventLogService` 依赖从 `ISessionEventStore` 改为 `SessionPersistenceSeam` | `SessionPersistenceSeam extends ISessionEventStore`，所有正式实现自动兼容 |
| `SessionEventLogService` 新增 `setProjectionCache` | 可空字段，默认 null 时不触发投影 |
| `ProjectionCache.onEventAppendedSync` | public 方法，供测试同步调用 |
| `ProjectionRegistry` 新增 `registeredTypes()` | 新方法，不影响既有调用 |
| pom.xml 新增元数据 | 不影响构建，仅满足发布要求 |

## 15. 对齐进度

### 已完成（Phase 1 前 25 个任务）

| 任务 | 域 | 说明 |
| --- | --- | --- |
| T1.001-T1.005 | A | Scope 路由核心抽象 + 集成测试 |
| T1.006-T1.007 | A | Waterfall 事件链 |
| T1.008 | A | System Prompt waterfall 改造 |
| T1.009-T1.013 | A | 五阶段工具管线 + 集成测试 |
| T1.014 | B | ReactLoopAgent Scope 事件分发 |
| T1.015-T1.018 | B | Session V4 格式迁移链 |
| T1.019 | B | LegacyEventAdapter 增强 |
| T1.020-T1.023 | B | 投影框架 + 缓存 + 合流集成测试 |
| T1.024 | B | SessionPersistenceSeam 正式化与契约测试 |
| T1.025 | C | LlmStreamWaterfall + 拦截器链 |

### 已移至 v0.2.0

| 任务 | 域 | 说明 |
| --- | --- | --- |
| T1.026 | C | LlmGenerationService 改造接入 waterfall |
| T1.027-T1.030 | C/D | 重放感知 Token 快照 + JDBC 仓储 + 测试 |
| T1.031-T1.032 | D | MCP 自动重连 + 工具命名规范 |
| T1.033 | N | 模块级 HMR |
| T1.034 | N | Runtime Resolution 端口 + 插件刷新 |
| T1.035-T1.036 | O | SDK 协议扩展 + Stdio 路由 |

> 以上 11 个任务已在 v0.2.0 中完成，详见 `release-v0.2.0-development-notes.md`。

### 待完成

| 任务 | 域 | 说明 |
| --- | --- | --- |
| Phase 2 (35 任务) | — | v0.2.0-beta 核心能力增强 |
| Phase 3 (38 任务) | — | v0.2.0 GA 生态扩展 |

## 16. 已知限制

| 事项 | 说明 |
| --- | --- |
| LLM routing 绕过 retry | `LlmRoutingInterceptor` 替换 streamSupplier 后不保留 retry 包装，重定向后的流不自动重试 |
| 投影合流异步单线程 | `ProjectionCache` 默认用单线程 executor，高并发下可能成为瓶颈 |
| 契约测试仅覆盖 Jsonl | `JdbcSessionPersistenceContractTest` 待 JDBC 测试环境就绪后补齐 |
| Phase 1 后 11 任务已移至 v0.2.0 | T1.026-T1.036 在 v0.2.0 中完成 |

## 17. 后续建议

1. ~~完成 T1.026 `LlmGenerationService` 改造接入 waterfall~~（已在 v0.2.0 完成）；
2. 为 `LlmRoutingInterceptor` 增加 retry 包装保留机制；
3. 为 `ProjectionCache` 提供可配置的 executor 线程池大小；
4. 补齐 `JdbcSessionPersistenceContractTest`；
5. ~~继续 Phase 1 剩余任务（T1.027-T1.036）~~（已在 v0.2.0 完成）；
6. 进入 Phase 2 核心能力增强（35 任务）。

## 18. 结论

v0.1.9 把 dsh-java 的核心底座从"功能具备但架构缺失"推进到"核心架构特征对齐参考实现"：Scope 路由、Waterfall 拦截链、五阶段工具管线、Session V4 迁移、投影框架+缓存、LLM Stream Waterfall 等关键底座已补齐并通过测试验证。全量 218 测试通过，0 回归。

Phase 1 剩余 11 个任务（T1.026-T1.036）已在 v0.2.0 中完成，详见 `release-v0.2.0-development-notes.md`。下一阶段的重点是进入 Phase 2 核心能力增强（35 任务，目标 v0.2.0-beta）。
