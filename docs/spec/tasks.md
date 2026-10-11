# dsh-java 核心底座对齐 — 编码任务清单（tasks.md）

> 本文档基于 `dsh-java-core-base-alignment-spec.md`（104 条 EARS 需求 / 19 域）与 `dsh-java-core-base-alignment-design.md`（39 粗粒度子任务 / 72 细粒度）生成，将设计方案的 39 个粗粒度子任务拆解为 **109 个原子级编码任务**，按 Phase 1/2/3 组织，Phase 内按依赖拓扑排序。
>
> - **目标版本**：v0.1.8 → v0.2.0（alpha → beta → GA）
> - **任务粒度**：每个任务 = 一个可 review 的 PR 单元，0.5-2 天可独立完成、独立提交
> - **技术栈**：Java 17 + Spring Boot 3.5.16 + DDD 六边形架构（types/domain/api/case/infrastructure/trigger/app 七模块）
> - **参考实现**：deepseek-harness（TS/Python，`/Users/mac/liubai/dsh/deepseek-harness`）
> - **根包**：`net.linlan.dsh`（domain → `net.linlan.dsh.domain.*`，infrastructure → `net.linlan.dsh.infrastructure.adapter.*`）

---

# 一、任务总览

## 1.1 阶段分布

| 阶段 | 优先级 | 需求数 | 粗粒度子任务 | 原子任务数 | 目标版本 | 工作量 |
|------|--------|--------|------------|-----------|---------|--------|
| Phase 1 | P0 | 25 | 15 | 36 | v0.2.0-alpha | XL |
| Phase 2 | P1 | 41 | 19 | 35 | v0.2.0-beta | XL |
| Phase 3 | P2 | 38 | 5（粗）/38（细） | 38 | v0.2.0 | XL |
| **合计** | — | **104** | **39（粗）/72（细）** | **109** | **v0.2.0** | **3×XL** |

## 1.2 域分布（A-S）

| 域 | 需求数 | 涉及 Phase | 原子任务数 |
|----|--------|-----------|-----------|
| A 核心抽象层 | 7 | 1, 2 | 14（P1:11, P2:3） |
| B Session | 14 | 1, 2 | 16（P1:10, P2:6） |
| C LLM | 8 | 1, 2 | 9（P1:4, P2:5） |
| D MCP | 3 | 1, 2 | 4（P1:2, P2:2） |
| E Context | 5 | 2 | 4 |
| F Compaction | 3 | 2 | 3 |
| G Sandbox | 5 | 2, 3 | 5（P2:1, P3:4） |
| H Subagent | 3 | 2, 3 | 3（P2:1, P3:2） |
| I Workflow/Goal | 4 | 3 | 4 |
| J Skill/Hooks/Guard | 7 | 2, 3 | 6（P2:3, P3:2） |
| K Telemetry/Identity/Credentials | 4 | 2, 3 | 4（P2:1, P3:3） |
| L Storage/FS/Shell/SSH/Subprocess | 10 | 2, 3 | 9（P2:1, P3:8） |
| M Jobs/Schedule/Webhook/Workspace | 4 | 2, 3 | 4（P2:2, P3:2） |
| N Preset/Boot/Host/Bundle | 6 | 1, 3 | 5（P1:2, P3:3） |
| O SDK/Feedback/Interaction/Spill | 6 | 1, 3 | 7（P1:2, P3:5） |
| P Document/Attachment/Deliverables | 3 | 2, 3 | 3（P2:1, P3:2） |
| Q PTC/Typert/LSP | 3 | 2 | 3 |
| R Computer/Browser/Web | 3 | 2, 3 | 3（P2:1, P3:2） |
| S Native 系统原语 | 1 | 3 | 1 |

## 1.3 任务字段说明

每个原子任务包含以下字段：

- **任务ID**：`T{phase}.{序号}`，如 `T1.001`（Phase 1 第 1 个任务）
- **任务标题**：简洁描述
- **优先级**：P0/P1/P2
- **所属域**：A-S 中的域字母
- **所属模块**：types/domain/api/case/infrastructure/trigger/app 中的若干
- **依赖任务**：前置任务ID列表（空表示无依赖）
- **描述**：要做什么（基于设计文档的接口/类/数据模型）
- **涉及文件**：需要新建/修改的关键文件路径（基于 dsh-java 现有结构）
- **验收标准**：如何验证完成（编译通过、测试通过、ArchUnit 通过、具体行为验证）
- **参考 DSH 包**：对应的 deepseek-harness packages 路径
- **预估复杂度**：S（0.5d）/ M（1d）/ L（1.5d）/ XL（2d）

---

# 二、Phase 1 — P0 核心底座补齐（36 任务，目标 v0.2.0-alpha）

> **目标**：补齐 dsh-java 缺失的核心架构特征（Scope 路由、waterfall 拦截、五阶段工具管线、Session 格式版本化与投影框架、LLM waterfall、重放感知 token、MCP 重连、模块级 HMR、runtime resolution、完整 SDK 协议），使其与 deepseek-harness 核心底座在架构层面对齐。
>
> **子阶段**：1a 架构抽象层（T1.001-T1.014）/ 1b Session 基座（T1.015-T1.024）/ 1c LLM 基座（T1.025-T1.030）/ 1d MCP 基座（T1.031-T1.032）/ 1e 插件/SDK 基座（T1.033-T1.036）

## 2.1 子阶段 1a — 架构抽象层（A 域，T1.001-T1.014）

### T1.001 ScopeKey 不透明值对象
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `domain/core/scope/model/valobj/ScopeKey.java`，用 `List<String> segments` 封装为不透明值对象。字段 `private final`，仅暴露 `equals`/`hashCode`/`parent()`/`isDescendantOf(ScopeKey)`/`root(name)`/`child(parent, name)` 工厂方法。**不暴露**任何 getter 暴露内部 segments，确保消费方无法解析内部结构。`isDescendantOf` 沿父链比较。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/main/java/net/linlan/dsh/domain/core/scope/model/valobj/ScopeKey.java`
- **验收标准**：
  - [ ] 编译通过，`ScopeKey` 为 final class，segments 字段 private final 无 getter
  - [ ] `root("a").child("b").isDescendantOf(root("a"))` == true
  - [ ] `root("a").equals(root("a"))` == true，不同结构不相等
  - [ ] ArchUnit：`domain.core.scope` 不依赖 infrastructure
  - [ ] 单元测试覆盖相等/父子/无环
- **参考 DSH 包**：`packages/core/scope`（DSH scope 路由核心）

### T1.002 ScopedEvent 与 ScopeSubscription 模型
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T1.001
- **描述**：新建 `ScopedEvent<T>` record（scopeKey/type/payload/timestamp）与 `ScopeSubscription` 值对象（scope/handler/SubscriptionToken）。`SubscriptionToken` 为不透明 token。
- **涉及文件**：
  - 新建 `domain/core/scope/model/valobj/ScopedEvent.java`
  - 新建 `domain/core/scope/model/valobj/ScopeSubscription.java`
  - 新建 `domain/core/scope/model/valobj/SubscriptionToken.java`
- **验收标准**：
  - [ ] record 不可变，编译通过
  - [ ] ArchUnit 边界通过
- **参考 DSH 包**：`packages/core/scope`

### T1.003 ScopeEventDispatcher 与订阅端口
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.001, T1.002
- **描述**：新建 `ScopeEventDispatcher` 服务（`dispatch(ScopedEvent)` scope-filtered 分发 + `subscribe(scope, handler)` 返回 token + `unsubscribe(token)`）与 `IScopeSubscriptionPort` 端口。dispatch 时遍历订阅者，按 `event.scope.isDescendantOf(sub.scope) || event.scope.equals(sub.scope)` 过滤通知。用 `ConcurrentHashMap<ScopeKey, List<ScopeSubscription>>` 维护订阅。
- **涉及文件**：
  - 新建 `domain/core/scope/service/ScopeEventDispatcher.java`
  - 新建 `domain/core/scope/adapter/port/IScopeSubscriptionPort.java`
- **验收标准**：
  - [ ] 发布带 scope key 的事件，仅父链匹配订阅者收到，不匹配者不收到
  - [ ] subscribe 返回有效 token，unsubscribe 后不再收到
  - [ ] 线程安全（并发 dispatch/subscribe 不抛异常）
  - [ ] 单元测试覆盖匹配/不匹配/后代/取消订阅
- **参考 DSH 包**：`packages/core/scope`

### T1.004 InMemoryScopeSubscriptionPort 适配器
- **优先级**：P0 ｜ **域**：A ｜ **模块**：infrastructure ｜ **复杂度**：S
- **依赖**：T1.003
- **描述**：新建 `InMemoryScopeSubscriptionPort` 实现 `IScopeSubscriptionPort`，用 `ConcurrentHashMap` 持久化订阅（可选，内存即可）。Spring `@Component`，`@ConditionalOnProperty(name="dsh.scope.enabled", havingValue="true")`。
- **涉及文件**：
  - 新建 `dsh-java-infrastructure/src/main/java/net/linlan/dsh/infrastructure/adapter/scope/InMemoryScopeSubscriptionPort.java`
- **验收标准**：
  - [ ] 实现 IScopeSubscriptionPort 全部方法
  - [ ] ArchUnit：infrastructure 依赖 domain 端口，不反向
- **参考 DSH 包**：`packages/core/scope`

### T1.005 Scope 路由集成测试与 ArchUnit
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T1.004
- **描述**：编写 Scope 路由集成测试（多层级 scope 事件分发场景）与 ArchUnit 边界测试（`domain.core.scope` 仅依赖 types + 自身）。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/core/scope/ScopeEventDispatcherTest.java`
  - 新增 ArchUnit 规则覆盖 `domain.core.**`
- **验收标准**：
  - [ ] 集成测试通过：session scope 订阅者收到 tool scope 事件（后代）
  - [ ] ArchUnit 通过：core 包不依赖 infrastructure
- **参考 DSH 包**：`packages/core/scope`

### T1.006 Waterfall 事件链模型
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `domain/core/waterfall` 包。`WaterfallEvent<T>` record（type/payload/WaterfallContext）；`WaterfallContext` record（currentExpertIndex/executedExperts/metadata）；`WaterfallOutcome<T>` sealed interface（`Continue<T>(output)` / `Terminate<T>(output)`）。
- **涉及文件**：
  - 新建 `domain/core/waterfall/model/valobj/WaterfallEvent.java`
  - 新建 `domain/core/waterfall/model/valobj/WaterfallContext.java`
  - 新建 `domain/core/waterfall/model/valobj/WaterfallOutcome.java`
- **验收标准**：
  - [ ] sealed interface 编译通过，Continue/Terminate 为唯一 permit
  - [ ] record 不可变
- **参考 DSH 包**：`packages/core/waterfall`

### T1.007 WaterfallExpert 接口与 WaterfallChain 服务
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.006
- **描述**：新建 `WaterfallExpert<T>` 接口（`priority()` 升序 / `canTerminate()` / `handle(event, current)` 返回 WaterfallOutcome）；`WaterfallChain<T>` 服务（构造时按 priority 排序专家，`execute(event, initial)` 依次执行，可终止专家返回 Terminate 则 break，不可终止专家返回 Terminate 降级为 Continue，`registerExpert` 运行时注册）。
- **涉及文件**：
  - 新建 `domain/core/waterfall/WaterfallExpert.java`
  - 新建 `domain/core/waterfall/service/WaterfallChain.java`
- **验收标准**：
  - [ ] 多专家按 priority 升序依次执行，每位可读取前序输出 current
  - [ ] canTerminate=true 专家返回 Terminate 后，后续专家不调用，最终输出为该专家输出
  - [ ] canTerminate=false 专家返回 Terminate 时降级为 Continue（不终止）
  - [ ] 单元测试覆盖终止/非终止/混合/空专家链
- **参考 DSH 包**：`packages/core/waterfall`

### T1.008 System Prompt Assembler 改造为 waterfall
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：XL
- **依赖**：T1.007
- **描述**：改造现有 `ScopedSystemPromptAssembler.assemble`，内部构造 `WaterfallChain<PromptFragment>`。每个已注册 section 包装为 `WaterfallExpert`（priority=section.order，canTerminate=section.complete）。专家 `handle` 读取前序输出并拼接/改写片段。**保留**现有 `registerSection`/`registerContext`/`registerVariable` API 作为默认非拦截专家，确保 v0.1.8 兼容。现有 `complete=true` 短路语义泛化为 waterfall Terminate。
- **涉及文件**：
  - 修改 `dsh-java-domain/src/main/java/net/linlan/dsh/domain/agent/service/ScopedSystemPromptAssembler.java`
  - 可能新建 `domain/agent/service/PromptFragment.java`
- **验收标准**：
  - [ ] 触发组装时专家按优先级依次执行，可读取前序输出并改写
  - [ ] complete=true section 终止传播，后续 section 不执行
  - [ ] 现有 registerSection API 行为不变（回归测试通过）
  - [ ] 变量插值 `{{var}}` 仍工作
- **参考 DSH 包**：`packages/core/waterfall`、`packages/agent/system-prompt`

### T1.009 Prompt Waterfall 集成测试
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T1.008
- **描述**：编写 System Prompt waterfall 集成测试：多专家链、终止传播、改写片段、变量插值、与 v0.1.8 行为回归对照。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/agent/service/ScopedSystemPromptAssemblerWaterfallTest.java`
- **验收标准**：
  - [ ] 测试通过，覆盖 REQ-A2-P0-1/2 验收条件
- **参考 DSH 包**：`packages/agent/system-prompt`

### T1.010 ToolPhase 枚举与 Guard/Around 值对象
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ToolPhase` 枚举（PRE/GUARD/AROUND/POST/RESULT，固定顺序）；`GuardDecision` sealed interface（`Allow` / `Deny(reason, errorCode)` + 静态工厂 `allow()`/`deny(reason)`）；`AroundWrapper` 接口（`wrap(Supplier<ToolExecutionResult> proceed)` + `passThrough()` 静态工厂）；`ToolPhaseOutcome` 辅助值对象。同时扩展现有 `HookPoint` 枚举新增 `GUARD_TOOL_USE`/`AROUND_TOOL_USE`/`RESULT_TOOL_USE`。
- **涉及文件**：
  - 新建 `domain/tool/model/valobj/ToolPhase.java`
  - 新建 `domain/tool/model/valobj/GuardDecision.java`
  - 新建 `domain/tool/model/valobj/AroundWrapper.java`
  - 新建 `domain/tool/model/valobj/ToolPhaseOutcome.java`
  - 修改 `domain/hooks/model/valobj/HookPoint.java`（新增枚举值）
- **验收标准**：
  - [ ] ToolPhase 顺序为 PRE→GUARD→AROUND→POST→RESULT
  - [ ] GuardDecision sealed，Allow/Deny 为唯一 permit
  - [ ] HookPoint 新增三值不破坏现有枚举（回归通过）
- **参考 DSH 包**：`packages/tool/pipeline`

### T1.011 ToolPhaseInterceptor 接口与 ToolPipeline 服务
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.010
- **描述**：新建 `ToolPhaseInterceptor` 接口（`phase()`/`priority()` + default 方法 `preIntercept`/`guardIntercept`/`aroundIntercept`/`postIntercept`/`resultIntercept`）；新建 `ToolPipeline` 服务（构造时按 phase+priority 排序拦截器，`execute(ctx, toolBody)` 按 pre→guard→around→post→result 执行）。guard 拒绝则立即返回拒绝结果不执行 toolBody；around 用洋葱模型嵌套包裹 toolBody，可控制是否调用主体；pre/post/result 不可终止。
- **涉及文件**：
  - 新建 `domain/tool/service/ToolPhaseInterceptor.java`
  - 新建 `domain/tool/service/ToolPipeline.java`
  - 新建 `domain/tool/service/ToolCallContext.java`（若不存在）
- **验收标准**：
  - [ ] 五阶段按 pre→guard→around→post→result 固定顺序执行
  - [ ] guard 拒绝时 toolBody 不执行，返回拒绝结果+原因
  - [ ] around 可包裹主体，可控制是否调用，可在前后注入逻辑
  - [ ] 多个 around 按优先级嵌套（洋葱模型）
  - [ ] 单元测试覆盖五阶段顺序/guard 拒绝/around 短路/around 嵌套
- **参考 DSH 包**：`packages/tool/pipeline`

### T1.012 ToolCallExecutor 改造接入五阶段管线
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：XL
- **依赖**：T1.011
- **描述**：改造现有 `ToolCallExecutor.runGroup`，将 PRE/POST Hook 逻辑迁移为 `ToolPhaseInterceptor`（PRE→pre 拦截器，POST→post 拦截器），构造 `ToolPipeline` 执行工具调用。保留现有 `setHookService`/`setApprovalGate`/`setLifecycleListener` API（approval gate 映射为 guard 拦截器）。`awaitWithTimeout` 仍用于超时控制。结果按调用顺序回填保证回放稳定。
- **涉及文件**：
  - 修改 `dsh-java-domain/src/main/java/net/linlan/dsh/domain/tool/service/ToolCallExecutor.java`
- **验收标准**：
  - [ ] 调用任意工具，五阶段按序执行（满足 REQ-A3-P0-1）
  - [ ] guard 拒绝不执行主体（满足 REQ-A3-P0-2）
  - [ ] around 可包裹并控制是否调用主体（满足 REQ-A3-P0-3）
  - [ ] 现有 PRE/POST Hook 行为不变（回归测试通过）
  - [ ] 并行/串行模式仍工作，结果回填顺序稳定
- **参考 DSH 包**：`packages/tool/pipeline`

### T1.013 五阶段管线集成测试
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.012
- **描述**：编写五阶段管线集成测试：全五阶段顺序、guard 拒绝、around 短路、around 嵌套、与 v0.1.8 PRE/POST 行为回归对照、并行模式回填顺序。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/tool/service/ToolPipelineIntegrationTest.java`
- **验收标准**：
  - [ ] 测试通过，覆盖 REQ-A3-P0-1/2/3 验收条件
  - [ ] v0.1.8 PRE/POST 回归用例全部通过
- **参考 DSH 包**：`packages/tool/pipeline`

### T1.014 ReactLoopAgent 事件发布经 ScopeEventDispatcher
- **优先级**：P0 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.003
- **描述**：改造 `ReactLoopAgent` 的事件发布，从直接发布改为经 `ScopeEventDispatcher.dispatch`（按 session/tool scope 分发）。`@ConditionalOnProperty(name="dsh.scope.enabled")` 控制，关闭时退化为原直接发布。
- **涉及文件**：
  - 修改 `dsh-java-domain/src/main/java/net/linlan/dsh/domain/agent/service/run/ReactLoopAgent.java`
- **验收标准**：
  - [ ] scope.enabled=true 时事件经 ScopeEventDispatcher 分发
  - [ ] scope.enabled=false 时退化为原行为（回归通过）
- **参考 DSH 包**：`packages/core/scope`


## 2.2 子阶段 1b — Session 基座（B 域，T1.015-T1.024）

### T1.015 SessionFormatVersion 常量与迁移接口
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：无
- **描述**：新建 `SessionFormatVersion` 值对象（`CURRENT=4` 常量，`of(version)`/`value()`/`needsMigration()`）；新建 `SessionFormatMigration` 接口（`fromVersion()`/`toVersion()`/`migrate(SessionLog)` 冻结纯函数）。`SessionHeader` 新增 `formatVersion` 字段（int，默认 0）。
- **涉及文件**：
  - 新建 `domain/session/event/model/valobj/SessionFormatVersion.java`
  - 新建 `domain/session/event/service/migration/SessionFormatMigration.java`
  - 修改 `domain/session/event/model/entity/SessionHeader.java`（新增 formatVersion 字段）
- **验收标准**：
  - [ ] `SessionFormatVersion.CURRENT == 4`
  - [ ] `needsMigration()` 对 version<4 返回 true
  - [ ] SessionHeader 新增字段不破坏现有序列化（回归通过）
- **参考 DSH 包**：`packages/session/format-version`

### T1.016 V0→V4 冻结迁移实现
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：XL
- **依赖**：T1.015
- **描述**：实现 `V0ToV1Migration`...`V3ToV4Migration` 各步迁移（均实现 `SessionFormatMigration`，为冻结纯函数：输入不可变，输出新对象，仅升级不降级，幂等）。每步迁移处理对应版本间的 schema 差异（如字段重命名、结构变更、事件类型补充）。
- **涉及文件**：
  - 新建 `domain/session/event/service/migration/V0ToV1Migration.java` ~ `V3ToV4Migration.java`
- **验收标准**：
  - [ ] 每步迁移 `fromVersion()`/`toVersion()` 正确
  - [ ] 每步为纯函数：相同输入产生相同输出，输入不被修改
  - [ ] 幂等：对已是目标版本的输入迁移无副作用
  - [ ] 单元测试覆盖每步迁移
- **参考 DSH 包**：`packages/session/migration`

### T1.017 MigrationCatalog 与 SessionRebuilderService 集成
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.016
- **描述**：新建 `MigrationCatalog`（`defaultChain()` 注册 v0→v1→v2→v3→v4，`migrateToCurrent(input)` 依次执行迁移链，`register(migration)` 运行时注册）。改造 `SessionRebuilderService` 加载事件流时调 `MigrationCatalog.migrateToCurrent`。version > CURRENT 抛 `UnsupportedFormatException`。
- **涉及文件**：
  - 新建 `domain/session/event/service/migration/MigrationCatalog.java`
  - 修改 `domain/session/event/service/SessionRebuilderService.java`
- **验收标准**：
  - [ ] 加载 V2 流依次执行 v2→v3→v4 迁移，输出 version==4
  - [ ] 加载 V4 流不迁移
  - [ ] version>4 抛 UnsupportedFormatException
  - [ ] 迁移链幂等（迁移两次等于一次）
- **参考 DSH 包**：`packages/session/migration`

### T1.018 迁移链幂等性与回归测试
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.017
- **描述**：编写迁移链幂等性测试（每步迁移两次等于一次）、全链回归测试（V0 样本经全链后为 V4）、冻结性测试（输入不被修改）。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/session/event/service/migration/MigrationCatalogTest.java`
  - 新增 V0~V3 样本事件流 fixture
- **验收标准**：
  - [ ] 全部测试通过，覆盖 REQ-B1-P0-1/2 验收条件
- **参考 DSH 包**：`packages/session/migration`

### T1.019 LegacyEventAdapter 增强（pending-free / failed-step 容忍）
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.017
- **描述**：增强现有 `LegacyEventAdapter`：检测无 pending 标记的孤立 tool/result 节点时容忍恢复（不抛格式错误）；检测 failed-step 时标记可恢复，`ReactLoopAgent` 从 failed-step 之后继续执行（不重放已成功步骤）。恢复操作幂等。
- **涉及文件**：
  - 修改 `domain/session/event/service/LegacyEventAdapter.java`
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`（failed-step 恢复分支）
- **验收标准**：
  - [ ] 含 pending-free tool/result 的事件流正常加载不报错（满足 REQ-B1-P0-3）
  - [ ] 含 failed-step 的 Session 可恢复继续执行，不重放已成功步骤（满足 REQ-B1-P0-4）
  - [ ] 恢复操作幂等
- **参考 DSH 包**：`packages/session/legacy-adapter`

### T1.020 投影框架类型表与 Provider 契约
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `domain/session/projection` 包。`ProjectionType` record（name，预定义 SURFACE/STATISTICS/TURN_OUTLINE）；`ProjectionProvider<T>` 接口（`type()`/`projectionClass()`/`initial()`/`applyIncremental(current, event)`）；`ProjectionRegistry` 服务（`register(provider)`/`resolve(type)` 路由）。
- **涉及文件**：
  - 新建 `domain/session/projection/model/valobj/ProjectionType.java`
  - 新建 `domain/session/projection/adapter/port/ProjectionProvider.java`
  - 新建 `domain/session/projection/service/ProjectionRegistry.java`
- **验收标准**：
  - [ ] 注册投影 provider 后按类型路由可解析
  - [ ] 未注册类型 resolve 返回 Optional.empty()
- **参考 DSH 包**：`packages/session/projection`

### T1.021 ProjectionCache 写后合流缓存
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.020
- **描述**：新建 `ProjectionCache`（`get`/`put`/`computeIfAbsent`/`onEventAppended`）。`onEventAppended` 触发写后合流：读取 `lastEventStepIndex`，若 `event.stepIndex > lastEventStepIndex` 则调 `ProjectionProvider.applyIncremental` 增量合并并更新缓存，否则跳过（幂等）。用 `ConcurrentHashMap` + CAS 保证并发安全。合流异步触发，不阻塞事件追加主流程。
- **涉及文件**：
  - 新建 `domain/session/projection/service/ProjectionCache.java`
  - 新建 `domain/session/projection/model/valobj/ProjectionCacheEntry.java`
- **验收标准**：
  - [ ] 写入新事件后缓存增量合并，不重算全量（满足 REQ-B2-P0-2）
  - [ ] `lastEventStepIndex` 前进，重复合流同一事件无副作用（幂等）
  - [ ] 并发安全，合流失败不阻塞主流程
- **参考 DSH 包**：`packages/session/projection-cache`

### T1.022 SurfaceProjector 改造为 ProjectionProvider
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.021
- **描述**：改造现有 `SurfaceProjector` 实现 `ProjectionProvider<SurfaceProjection>`（`type()` 返回 SURFACE，`initial()` 返回空投影，`applyIncremental` 增量合流事件）。在 `ProjectionRegistry` 注册。`SessionEventLogService.append` 后触发 `ProjectionCache.onEventAppended`。
- **涉及文件**：
  - 修改 `domain/session/event/service/SurfaceProjector.java`
  - 修改 `domain/session/log/service/SessionEventLogService.java`（append 后触发合流）
- **验收标准**：
  - [ ] SurfaceProjector 实现 ProjectionProvider 契约
  - [ ] 事件追加后投影缓存增量合流
  - [ ] 现有 Surface 投影行为不变（回归通过）
- **参考 DSH 包**：`packages/session/projection`

### T1.023 投影合流集成测试
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T1.022
- **描述**：编写投影写后合流集成测试：增量合流、幂等、并发、lastEventStepIndex 前进、Surface 投影回归。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/session/projection/ProjectionCacheTest.java`
- **验收标准**：
  - [ ] 测试通过，覆盖 REQ-B2-P0-1/2 验收条件
- **参考 DSH 包**：`packages/session/projection-cache`

### T1.024 SessionPersistenceSeam 正式化与契约测试
- **优先级**：P0 ｜ **域**：B ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `SessionPersistenceSeam` 接口（extends `ISessionEventStore`，标记接口 + 契约文档）。`JsonlSessionEventStore`/`JdbcSessionEventStore` 声明实现该 seam。`SessionEventLogService` 依赖从 `ISessionEventStore` 改为 `SessionPersistenceSeam`。新建 `SessionPersistenceContractTest` 抽象契约测试基类，两种 Store 实现均跑同一契约套件。
- **涉及文件**：
  - 新建 `domain/session/event/adapter/repository/SessionPersistenceSeam.java`
  - 修改 `infrastructure/adapter/eventstore/JsonlSessionEventStore.java`（implements seam）
  - 修改 `infrastructure/adapter/eventstore/JdbcSessionEventStore.java`（implements seam）
  - 修改 `domain/session/log/service/SessionEventLogService.java`（依赖改为 seam）
  - 新建 `dsh-java-infrastructure/src/test/java/.../SessionPersistenceContractTest.java`
- **验收标准**：
  - [ ] 两种 Store 实现同一 seam，无后端特有方法泄漏到上层（满足 REQ-B7-P0-1）
  - [ ] 切换后端仅改配置，SessionEventLogService 代码无变更（满足 REQ-B7-P0-2）
  - [ ] 契约测试基类两种实现均通过
- **参考 DSH 包**：`packages/session/persistence`

## 2.3 子阶段 1c — LLM 基座（C 域，T1.025-T1.030）

### T1.025 LlmStreamWaterfall 与拦截器
- **优先级**：P0 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：XL
- **依赖**：T1.007
- **描述**：新建 `domain/llm/waterfall` 包。`LlmStreamWaterfall` 用 `WaterfallChain<StreamChunk>` 包装 `ILlmRuntimePort.stream`。注册三个拦截器：`LlmRetryInterceptor`（可重试，不终止，重发请求）、`LlmReplayInterceptor`（可重放，不终止，注入历史 chunk）、`LlmRoutingInterceptor`（可重定向，终止当前 Provider 请求，以新 Provider 重启 waterfall，保留原始请求上下文与 trace）。
- **涉及文件**：
  - 新建 `domain/llm/waterfall/LlmStreamWaterfall.java`
  - 新建 `domain/llm/waterfall/LlmRetryInterceptor.java`
  - 新建 `domain/llm/waterfall/LlmReplayInterceptor.java`
  - 新建 `domain/llm/waterfall/LlmRoutingInterceptor.java`
- **验收标准**：
  - [ ] 流式 LLM 请求时 retry/replay/routing 拦截器按 waterfall 链依次获得介入机会（满足 REQ-C1-P0-1）
  - [ ] routing 重定向到 Provider B 后请求由 B 执行，原始请求参数与 trace 上下文保留（满足 REQ-C1-P0-2）
- **参考 DSH 包**：`packages/llm/stream-waterfall`

### T1.026 LlmGenerationService 改造接入 waterfall
- **优先级**：P0 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.025
- **描述**：改造现有 `LlmGenerationService.generate`，从直接订阅 `ILlmRuntimePort.stream` 改为经 `LlmStreamWaterfall.execute` 包装。`LlmGenerationService` 成为 waterfall 终端消费者（订阅最终 StreamChunk 流，BlockAssembler 累积，onComplete 组装 Message）。保留现有 generate 签名兼容。
- **涉及文件**：
  - 修改 `domain/llm/service/LlmGenerationService.java`
- **验收标准**：
  - [ ] 流式生成经 waterfall 包装，拦截器可介入
  - [ ] 无拦截器时行为与 v0.1.8 一致（回归通过）
- **参考 DSH 包**：`packages/llm/stream-waterfall`

### T1.027 Token 快照模型与仓储端口
- **优先级**：P0 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：无
- **描述**：新建 `TokenSnapshot` record（lastEventStepIndex/totalTokens/modelCalls）；`ReplayContext` record（isReplay/upToStepIndex + `live()`/`replay(upToStep)` 工厂）；`ITokenSnapshotRepository` 端口（`load(sessionId)`/`save(sessionId, snapshot)`）。
- **涉及文件**：
  - 新建 `domain/runtime/meter/model/valobj/TokenSnapshot.java`
  - 新建 `domain/runtime/meter/model/valobj/ReplayContext.java`
  - 新建 `domain/runtime/meter/adapter/repository/ITokenSnapshotRepository.java`
- **验收标准**：
  - [ ] record 不可变，工厂方法正确
  - [ ] ArchUnit：端口在 domain，不依赖 infrastructure
- **参考 DSH 包**：`packages/runtime/token-meter`

### T1.028 ReplayAwareTokenMeter 实现
- **优先级**：P0 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.027
- **描述**：新建 `ReplayAwareTokenMeter` 实现 `ITokenMeterService`。`summarize(sessionId, replayContext)`：若 `isReplay=true` 直接返回快照 `totalTokens`（不重算）；若 `isReplay=false` 从快照 `lastEventStepIndex` 之后增量合流（`readAfter`）并保存新快照。快照不存在时从 0 开始全量计算并保存。替换 `SessionTokenMeterService` 的使用点。
- **涉及文件**：
  - 新建 `domain/runtime/meter/service/ReplayAwareTokenMeter.java`
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`（token 统计改用 ReplayAwareTokenMeter）
- **验收标准**：
  - [ ] 重放已含 token 的事件流，总量等于快照值不翻倍（满足 REQ-C3-P0-1）
  - [ ] 重放时从快照恢复不重新估算（满足 REQ-C3-P0-2）
  - [ ] 非重放时增量合流，快照前进
- **参考 DSH 包**：`packages/runtime/token-meter`

### T1.029 JdbcTokenSnapshotRepository 适配器与表迁移
- **优先级**：P0 ｜ **域**：C ｜ **模块**：infrastructure, app ｜ **复杂度**：M
- **依赖**：T1.028
- **描述**：新建 `JdbcTokenSnapshotRepository` 实现 `ITokenSnapshotRepository`（JDBC 读写 `token_snapshot` 表）。新增表迁移脚本（`token_snapshot(session_id PK, last_event_step_index, total_tokens, model_calls)`）。在 `app` 的 `SchemaMigrationRunner` 注册。
- **涉及文件**：
  - 新建 `infrastructure/adapter/repository/JdbcTokenSnapshotRepository.java`
  - 新增 `app/src/main/resources/db/migration/V*__token_snapshot.sql`
- **验收标准**：
  - [ ] 仓储读写正确，表迁移执行成功
  - [ ] ArchUnit：infrastructure 依赖 domain 端口
- **参考 DSH 包**：`packages/runtime/token-meter`

### T1.030 重放感知 Token Meter 测试
- **优先级**：P0 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T1.029
- **描述**：编写重放感知测试：重放不翻倍、非重放增量合流、快照不存在全量计算、部分重放、与 v0.1.8 全量重算对照。
- **涉及文件**：
  - 新建 `dsh-java-domain/src/test/java/net/linlan/dsh/domain/runtime/meter/service/ReplayAwareTokenMeterTest.java`
- **验收标准**：
  - [ ] 测试通过，覆盖 REQ-C3-P0-1/2 验收条件
- **参考 DSH 包**：`packages/runtime/token-meter`

## 2.4 子阶段 1d — MCP 基座（D 域，T1.031-T1.032）

### T1.031 ReconnectPolicy 与 ReconnectingMcpClient
- **优先级**：P0 ｜ **域**：D ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `ReconnectPolicy` record（initialBackoff/maxBackoff/maxAttempts/backoffMultiplier + `default_()` 工厂 + `nextBackoff(attempt)` 指数退避）；新建 `ReconnectingMcpClient` 装饰器 implements `IMcpClient`，包裹底层 `StdioMcpClient`/`HttpMcpClient`，断连检测后按 `ReconnectPolicy` 指数退避自动重连，重连成功恢复工具可用，超过 maxAttempts 抛 `McpReconnectExhaustedException`。`McpBootstrap` 创建 client 时包裹装饰器。每个 server 独立退避时钟错峰重连。
- **涉及文件**：
  - 新建 `domain/tool/mcp/model/valobj/ReconnectPolicy.java`
  - 新建 `domain/tool/mcp/ReconnectingMcpClient.java`
  - 修改 `infrastructure/adapter/mcp/McpBootstrap.java`（创建时包裹装饰器）
- **验收标准**：
  - [ ] MCP 服务端断连后按退避策略自动重连，成功后恢复工具可用（满足 REQ-D1-P0-1）
  - [ ] 超过 maxAttempts 抛异常，不无限重试
  - [ ] 重连成功后 isConnected()=true
- **参考 DSH 包**：`packages/mcp/reconnect`

### T1.032 McpToolAdapter 强制命名规范
- **优先级**：P0 ｜ **域**：D ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：无
- **描述**：改造 `McpToolAdapter` 注册工具名时强制 `mcp__{server}__{raw}` 前缀，冲突检测（两个 server 同名工具因前缀不同无冲突）。
- **涉及文件**：
  - 修改 `domain/tool/mcp/McpToolAdapter.java`
- **验收标准**：
  - [ ] 两个 MCP server 暴露同名工具，工具名带 server 前缀无冲突（满足 REQ-D1-P0-2）
  - [ ] 现有工具名注册行为兼容（回归通过）
- **参考 DSH 包**：`packages/mcp/tool-adapter`

## 2.5 子阶段 1e — 插件/SDK 基座（N/O 域，T1.033-T1.036）

### T1.033 ModuleLevelHmrService（模块级 HMR）
- **优先级**：P0 ｜ **域**：N ｜ **模块**：domain, infrastructure ｜ **复杂度**：XL
- **依赖**：T1.034
- **描述**：将现有 `PluginHotReloader`（JAR 级）升级为 `ModuleLevelHmrService`（模块级）。监听粒度从 JAR 细化到模块源码（`watchModule(moduleId, moduleRoot)`/`unwatchModule`）。变更后 `refreshManifest(moduleId)` 刷新包清单 + `refreshResolution(moduleId)` 触发 runtime resolution 刷新。CommonJS request 失效时刷新 manifest 并以 Office resolution 重新解析。用独立 `URLClassLoader` per 模块，重载时 close 旧加载器避免泄漏。`@PreDestroy` 停止 WatchService。debounce 可配置（默认 300ms）。
- **涉及文件**：
  - 新建 `domain/plugin/runtime/service/ModuleLevelHmrService.java`
  - 修改/废弃 `infrastructure/adapter/plugin/java/PluginHotReloader.java`（升级为模块级）
- **验收标准**：
  - [ ] 插件模块文件变更后 HMR 刷新该模块，包清单和入口标识更新，不重启进程（满足 REQ-N3-P0-1）
  - [ ] CommonJS request 失效后 manifest 刷新，Office resolution 重新解析（满足 REQ-N3-P0-2）
  - [ ] 重载失败保留旧版本（回滚）
- **参考 DSH 包**：`packages/plugin/hmr`

### T1.034 IRuntimeResolutionPort 与 PluginRegistryService 刷新
- **优先级**：P0 ｜ **域**：N ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `IRuntimeResolutionPort` 端口（`resolutionMap()`/`refresh(moduleId)`/`resolve(moduleId)`）；新建 `InProcessRuntimeResolution` 适配器（维护 `Map<String, ResolvedPackage>`）。`PluginRegistryService` 新增 `refreshResolution(moduleId)` 方法，运行时新增/更新插件触发刷新。`harness_plugin` 表新增 `module_id`/`entry_point` 列。
- **涉及文件**：
  - 新建 `domain/plugin/runtime/adapter/port/IRuntimeResolutionPort.java`
  - 新建 `infrastructure/adapter/plugin/InProcessRuntimeResolution.java`
  - 修改 `domain/plugin/registry/service/PluginRegistryService.java`（增 refreshResolution）
  - 新增表迁移脚本（harness_plugin 新增列）
- **验收标准**：
  - [ ] 运行时新增/更新插件后 resolution 映射更新，新插件可被发现加载（满足 REQ-N4-P0-1）
  - [ ] ArchUnit：端口在 domain，适配器在 infrastructure
- **参考 DSH 包**：`packages/plugin/runtime-resolution`

### T1.035 ISdkServerService/ISdkClientService 接口扩展
- **优先级**：P0 ｜ **域**：O ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：对照 DSH `sdk/protocol` 方法清单，`ISdkServerService` 新增 default 方法：`listSessions(limit)`/`loadSession(sessionId)`/`cancelSession(sessionId)`/`streamSession(sessionId, sink)`/`listTools(sessionId)`/`cancelTool(sessionId, callId)`/`listMcpTools()`/`listResources(scope)` 等（均提供空默认实现）。`ISdkClientService` 对应扩展。default 实现确保旧实现无需改动。
- **涉及文件**：
  - 修改 `domain/sdk/ISdkServerService.java`
  - 修改 `domain/sdk/ISdkClientService.java`（若存在）
  - 新增对应 DTO/值对象（listSessions 返回类型等）
- **验收标准**：
  - [ ] 新方法均为 default，旧实现编译通过（v0.1.8 兼容）
  - [ ] 方法签名对齐 DSH sdk/protocol
- **参考 DSH 包**：`packages/sdk/protocol`

### T1.036 SdkServerService 实现新方法与 Stdio 路由
- **优先级**：P0 ｜ **域**：O ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：T1.035
- **描述**：`SdkServerService`（已存在实现）覆写新增 default 方法，对接 domain 服务（listSessions→SessionEventLogService.listHeaders，loadSession→readAll，等）。`StdioSdkTransportPort` 路由新 JSON-RPC 方法名到对应服务方法。
- **涉及文件**：
  - 修改 `domain/sdk/service/SdkServerService.java`（若存在）
  - 修改 `infrastructure/adapter/sdk/StdioSdkTransportPort.java`（路由新方法）
- **验收标准**：
  - [ ] 对照 DSH 方法清单全部可经 JSON-RPC stdio 调用，签名对齐（满足 REQ-O1-P0-1）
  - [ ] 旧客户端不调用新方法时行为不变（兼容）
- **参考 DSH 包**：`packages/sdk/protocol`


---

# 三、Phase 2 — P1 核心能力增强（35 任务，目标 v0.2.0-beta）

> **目标**：在 Phase 1 架构对齐基础上，补齐重要功能域并增强现有能力。**依赖 Phase 1**（P1 能力增强依赖 P0 架构基座）。
>
> **子阶段**：2a Session 增强（T2.001-T2.006）/ 2b LLM 增强（T2.007-T2.009）/ 2c Context 域（T2.010-T2.013）/ 2d Compaction 域（T2.014-T2.016）/ 2e 能力散点增强（T2.017-T2.035）

## 3.1 子阶段 2a — Session 增强（B 域，T2.001-T2.006）

### T2.001 CheckpointStrategy 与仓储
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain, infrastructure, app ｜ **复杂度**：L
- **依赖**：T1.017
- **描述**：新建 `domain/session/checkpoint` 包。`CheckpointStrategy`（`beforeModelRequest(SessionLog)`/`beforeToolSideEffect(SessionLog, ToolCall)` 返回 `CheckpointSnapshot`）；`ICheckpointRepository` 端口；`JdbcCheckpointRepository` 适配器；新增 `checkpoint(session_id, step_index, reason, context_snapshot_json)` 表。
- **涉及文件**：
  - 新建 `domain/session/checkpoint/service/CheckpointStrategy.java`
  - 新建 `domain/session/checkpoint/model/valobj/CheckpointSnapshot.java`
  - 新建 `domain/session/checkpoint/adapter/repository/ICheckpointRepository.java`
  - 新建 `infrastructure/adapter/repository/JdbcCheckpointRepository.java`
  - 新增 `app/src/main/resources/db/migration/V*__checkpoint.sql`
- **验收标准**：
  - [ ] LLM 请求前写入检查点含上下文快照（满足 REQ-B3-P1-1）
  - [ ] 工具副作用前写入可回滚至副作用前状态（满足 REQ-B3-P1-2）
- **参考 DSH 包**：`packages/session/checkpoint`

### T2.002 ReactLoopAgent 集成检查点
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T2.001
- **描述**：`ReactLoopAgent` 在发起 LLM 请求前调 `CheckpointStrategy.beforeModelRequest`，在工具产生副作用前（如写文件）调 `beforeToolSideEffect`。
- **涉及文件**：
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`
- **验收标准**：
  - [ ] 请求前/副作用前检查点被写入
  - [ ] 不破坏现有执行流程（回归通过）
- **参考 DSH 包**：`packages/session/checkpoint`

### T2.003 SessionTelemetryExporter 与字节有界 OTLP 缓冲
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.021
- **描述**：新建 `domain/session/telemetry` 包。`SessionTelemetryExporter`（`export(SessionEvent)`）；`OtlpBuffer` 字节有界缓冲区（`byteLimit` 默认 4MB，`currentBytes`，`dropPolicy=drop-oldest`，超界丢弃最旧事件，`droppedCount` 计数）。`@ConditionalOnProperty(name="dsh.session.telemetry.enabled")`。
- **涉及文件**：
  - 新建 `domain/session/telemetry/service/SessionTelemetryExporter.java`
  - 新建 `domain/session/telemetry/model/valobj/OtlpBuffer.java`
- **验收标准**：
  - [ ] 启用 session-telemetry 后 session log events 经 OTLP 上报，缓冲区受字节上界约束（满足 REQ-B4-P1-1）
  - [ ] 缓冲区超界时丢弃最旧事件，新事件继续入队，记录丢弃计数（满足 REQ-B4-P1-2）
- **参考 DSH 包**：`packages/session/telemetry`

### T2.004 SessionTitleGenerator
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.022
- **描述**：新建 `SessionTitleGenerator`（`generate(SessionLog)` 首轮对话完成触发，`update(old, SessionLog)` 更新）。在 Session 创建或首轮对话完成时生成/更新标题，可在 list 中展示。
- **涉及文件**：
  - 新建 `domain/session/projection/service/SessionTitleGenerator.java`
- **验收标准**：
  - [ ] 首轮对话完成后 Session 标题被生成或更新（满足 REQ-B5-P1-1）
- **参考 DSH 包**：`packages/session/title`

### T2.005 SessionStatistics 与 TurnOutline 投影
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.022
- **描述**：新建 `SessionStatistics` 投影（实现 `ProjectionProvider`，统计消息数/工具调用数/token 用量）与 `TurnOutline` 投影（每轮 goal/action/result 摘要）。在 `ProjectionRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/session/projection/service/SessionStatistics.java`
  - 新建 `domain/session/projection/service/TurnOutline.java`
- **验收标准**：
  - [ ] 查询 Session 统计返回消息数、工具调用数、token 用量（满足 REQ-B5-P1-2）
  - [ ] 查询轮次大纲返回每轮 goal/action/result 摘要列表（满足 REQ-B5-P1-3）
- **参考 DSH 包**：`packages/session/statistics`、`packages/session/turn-outline`

### T2.006 SessionFullTextIndex 与搜索 Provider
- **优先级**：P1 ｜ **域**：B ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：T1.021
- **描述**：新建 `domain/session/search` 包。`SessionFullTextIndex`（`index(SessionEvent)` 索引事件流内容）；`SessionSearchProvider`（`search(query, limit)` 返回匹配片段 + 高亮定位）；`LuceneSessionSearchIndex` 适配器（Apache Lucene 嵌入式索引）。`ISessionSearchIndexPort` 端口。
- **涉及文件**：
  - 新建 `domain/session/search/service/SessionFullTextIndex.java`
  - 新建 `domain/session/search/service/SessionSearchProvider.java`
  - 新建 `domain/session/search/adapter/port/ISessionSearchIndexPort.java`
  - 新建 `infrastructure/adapter/search/LuceneSessionSearchIndex.java`
  - 新增 Maven 依赖 `lucene-core`/`lucene-highlighter`
- **验收标准**：
  - [ ] 提交搜索关键词返回匹配的 Session 片段及定位信息，支持高亮（满足 REQ-B6-P1-1）
- **参考 DSH 包**：`packages/session/search`

## 3.2 子阶段 2b — LLM 增强（C 域，T2.007-T2.009）

### T2.007 LlmRetryPolicy 与重试拦截器
- **优先级**：P1 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T1.025
- **描述**：新建 `domain/llm/retry` 包。`LlmRetryPolicy`（`shouldRetry(Throwable, attempt)`/`fallbackProvider(Throwable)`/`maxAttempts`）；`RetryableErrorClassifier`（429/5xx 可重试分类）。增强 Phase 1 的 `LlmRetryInterceptor`：按策略重试，可路由到备用 Provider，超上限返回最终错误+重试摘要。
- **涉及文件**：
  - 新建 `domain/llm/retry/model/valobj/LlmRetryPolicy.java`
  - 新建 `domain/llm/retry/service/RetryableErrorClassifier.java`
  - 修改 `domain/llm/waterfall/LlmRetryInterceptor.java`（接入策略）
- **验收标准**：
  - [ ] Provider 返回 429/5xx 可重试错误时按策略重试，可路由到备用 Provider（满足 REQ-C2-P1-1）
  - [ ] 重试次数达上限停止重试，返回最后一次错误及重试摘要（满足 REQ-C2-P1-2）
- **参考 DSH 包**：`packages/llm/retry`

### T2.008 LlmRequestExtensionRegistry
- **优先级**：P1 ｜ **域**：C ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `domain/llm/extension` 包。`LlmRequestExtensionRegistry`（`register(field, provider)`/`resolve(request)` 返回额外字段 Map）。插件注册加法请求字段，不影响已有字段。`@ConditionalOnProperty(name="dsh.llm.api-extensions.enabled")`。
- **涉及文件**：
  - 新建 `domain/llm/extension/service/LlmRequestExtensionRegistry.java`
- **验收标准**：
  - [ ] 插件注册扩展字段 X 后后续 LLM 请求包含字段 X，不影响已有字段（满足 REQ-C4-P1-1）
- **参考 DSH 包**：`packages/llm/request-extensions`

### T2.009 PiAiAdapter 与 LlmAdapterFactory 分支
- **优先级**：P1 ｜ **域**：C ｜ **模块**：infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `PiAiAdapter`（继承 `OpenAiCompatibleAdapter` 或与 `DeepSeekAdapter` 共享 `BaseChatAdapter` 核心协议）；`LlmAdapterFactory` 新增 pi-ai 分支。`@ConditionalOnProperty` 按配置激活。
- **涉及文件**：
  - 新建 `infrastructure/adapter/llm/piai/PiAiAdapter.java`
  - 修改 `infrastructure/adapter/llm/LlmAdapterFactory.java`（新增 pi-ai 分支）
- **验收标准**：
  - [ ] 配置 pi-ai Provider 后请求经 pi-ai 适配器处理，核心协议逻辑与 DeepSeek 共享（满足 REQ-C5-P1-1）
- **参考 DSH 包**：`packages/llm/pi-ai`

## 3.3 子阶段 2c — Context 域新建（E 域，T2.010-T2.013）

### T2.010 AgentsMdLoader 层级加载与合并
- **优先级**：P1 ｜ **域**：E ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `domain/agent/context` 包。`AgentsMdLoader`（`load(workspaceRoot)` 从工作区层级加载 AGENTS.md，`merge(layered)` 子级覆盖父级同键指令）。Session 初始化时调 `load(cwd)`，结果经 `ScopedSystemPromptAssembler.registerContext` 注册为 `agents-md` context。
- **涉及文件**：
  - 新建 `domain/agent/context/service/AgentsMdLoader.java`
  - 新建 `domain/agent/context/model/valobj/Instructions.java`
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`（Session 初始化时加载）
- **验收标准**：
  - [ ] 工作区含多层级 AGENTS.md 时按层级合并，子级覆盖父级同键指令（满足 REQ-E1-P1-1）
- **参考 DSH 包**：`packages/agent/context/agents-md`

### T2.011 FileReferenceExpander @file 内联展开
- **优先级**：P1 ｜ **域**：E ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `FileReferenceExpander`（`expand(Message)` 消息预处理，`expandAtFile(text, cwd)` 将 @path/to/file 引用文件内容内联展开）。`ReactLoopAgent` 消息预处理阶段调用。复用 `IFsPort` 读文件。
- **涉及文件**：
  - 新建 `domain/agent/context/service/FileReferenceExpander.java`
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`（消息预处理）
- **验收标准**：
  - [ ] 消息含 @path/to/file 时文件内容内联展开，模型可见文件内容（满足 REQ-E2-P1-1）
- **参考 DSH 包**：`packages/agent/context/file-reference`

### T2.012 SessionSnapshotReference 只读快照
- **优先级**：P1 ｜ **域**：E ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.024
- **描述**：新建 `SessionSnapshotReference`（`loadReadOnly(sessionId)` 解析跨 session 引用并加载目标 Session 只读快照，不可修改）。消息含 session 引用时注入上下文。
- **涉及文件**：
  - 新建 `domain/agent/context/service/SessionSnapshotReference.java`
- **验收标准**：
  - [ ] 消息含 session 引用时目标 Session 以只读快照注入上下文，不可修改（满足 REQ-E3-P1-1）
- **参考 DSH 包**：`packages/agent/context/session-snapshot`

### T2.013 TimeContextInjector 与 TmuxContextInjector
- **优先级**：P1 ｜ **域**：E ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `TimeContextInjector`（每步注入当前时间戳 + 自 Session 起始 elapsed 时间）与 `TmuxContextInjector`（每步注入当前 pane/window 位置信息，`@ConditionalOnProperty(name="dsh.tmux.enabled")`）。经 `ScopedSystemPromptAssembler.registerContext` 注册 `time`/`tmux` context。
- **涉及文件**：
  - 新建 `domain/agent/context/service/TimeContextInjector.java`
  - 新建 `domain/agent/context/service/TmuxContextInjector.java`
- **验收标准**：
  - [ ] 任意步骤执行中上下文含当前时间戳和 elapsed 时间（满足 REQ-E4-P1-1）
  - [ ] 启用 tmux 集成后每步上下文含 pane/window 标识（满足 REQ-E5-P1-1）
- **参考 DSH 包**：`packages/agent/context/time`、`packages/agent/context/tmux`

## 3.4 子阶段 2d — Compaction 域新建（F 域，T2.014-T2.016）

### T2.014 ImageOffloader 图像卸载
- **优先级**：P1 ｜ **域**：F ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `domain/agent/service/compaction` 包（已存在骨架，增强）。`ImageOffloader`（`offload(contentBlocks)` 将图像内容替换为占位符并记录可恢复引用，`restore(offloadResult)` 恢复）。复用 `IStorageBackendPort` 存溢出图像。集成到 `CompactionEngine`。
- **涉及文件**：
  - 新建 `domain/agent/service/compaction/ImageOffloader.java`
  - 修改 `domain/agent/service/compaction/CompactionEngine.java`（若存在）
- **验收标准**：
  - [ ] 压缩触发且上下文含图像时图像替换为占位符，记录可恢复引用（满足 REQ-F1-P1-1）
- **参考 DSH 包**：`packages/agent/compaction/image-offload`

### T2.015 ToolResultPruner head/middle/tail 剪枝
- **优先级**：P1 ｜ **域**：F ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ToolResultPruner`（`prune(result, threshold)` 按阈值剪枝，`headMiddleTail(result, threshold)` 保留首尾片段 + 摘要中间部分，总长度低于阈值）。集成到 `CompactionEngine`。
- **涉及文件**：
  - 新建 `domain/agent/service/compaction/ToolResultPruner.java`
- **验收标准**：
  - [ ] 工具结果超阈值时被剪枝为首部+摘要+尾部，总长度低于阈值（满足 REQ-F2-P1-1）
- **参考 DSH 包**：`packages/agent/compaction/prune`

### T2.016 CompactCommand slash 命令
- **优先级**：P1 ｜ **域**：F ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T2.014, T2.015
- **描述**：新建 `CompactCommand`（`/compact` slash 命令，立即触发上下文压缩，返回压缩后摘要）。在 `SlashCommandRegistry` 注册（若已存在则复用）。
- **涉及文件**：
  - 新建 `domain/agent/service/compaction/CompactCommand.java`
- **验收标准**：
  - [ ] 用户输入 /compact 时立即执行压缩，返回压缩后上下文摘要（满足 REQ-F3-P1-1）
- **参考 DSH 包**：`packages/agent/compaction/command`

## 3.5 子阶段 2e — 能力散点增强（T2.017-T2.035）

### T2.017 SandboxPolicyInjector 策略注入
- **优先级**：P1 ｜ **域**：G ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.012
- **描述**：新建 `SandboxPolicyInjector`（每次工具调用发起时解析沙箱策略，序列化策略摘要，注入 `AssembleContext`）。`ToolCallExecutor` 调用前解析策略并注入；`ScopedSystemPromptAssembler.registerContext` 注册 `sandbox-policy` context。
- **涉及文件**：
  - 新建 `domain/sandbox/service/SandboxPolicyInjector.java`
  - 修改 `domain/tool/service/ToolCallExecutor.java`（调用前注入）
- **验收标准**：
  - [ ] 发起工具调用时策略被解析，模型上下文含该调用的策略摘要（满足 REQ-G4-P1-1）
- **参考 DSH 包**：`packages/sandbox/policy-injector`

### T2.018 InProcessSubagentDriver 对齐 DSH
- **优先级**：P1 ｜ **域**：H ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：对照 DSH `subagent-in-process-driver` 行为契约补齐现有 `agent/service/subagent` 骨架的 `InProcessSubagentDriver`：生命周期管理（spawn/send/interrupt/shutdown）、行为契约对齐。
- **涉及文件**：
  - 修改/新建 `domain/agent/service/subagent/InProcessSubagentDriver.java`
- **验收标准**：
  - [ ] 启动进程内子代理行为与 DSH in-process-driver 一致，含生命周期管理（满足 REQ-H2-P1-1）
- **参考 DSH 包**：`packages/subagent/in-process-driver`

### T2.019 CodexHookBridge 与 HookDialect.CODEX
- **优先级**：P1 ｜ **域**：J ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：`HookDialect` 枚举新增 `CODEX`；新建 `CodexHookBridge`（`bridge(CodexHookInput)` 实现 Codex hook 协议收发）。`@ConditionalOnProperty` 按配置激活。
- **涉及文件**：
  - 修改 `domain/hooks/model/valobj/HookDialect.java`（新增 CODEX）
  - 新建 `domain/hooks/codex/CodexHookBridge.java`
- **验收标准**：
  - [ ] 配置 CC/Codex hook 后 hook 按对应协议格式收发（满足 REQ-J3-P1-1）
- **参考 DSH 包**：`packages/hooks/codex`

### T2.020 DuplicateToolGuard 增强
- **优先级**：P1 ｜ **域**：J ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.012
- **描述**：增强现有 `domain/guard/reminder` 骨架的 `DuplicateToolGuard`：连续两次同工具同参数调用时触发重复提醒，提示用户/模型。注册为 `ToolPhaseInterceptor`（PRE 阶段）。
- **涉及文件**：
  - 修改 `domain/guard/reminder/DuplicateToolGuard.java`（若存在）
- **验收标准**：
  - [ ] 连续两次同工具同参数调用触发重复提醒（满足 REQ-J4-P1-1）
- **参考 DSH 包**：`packages/guard/duplicate-tool`

### T2.021 PerToolDeadlinePolicy per-tool 超时
- **优先级**：P1 ｜ **域**：J ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T1.012
- **描述**：新建 `PerToolDeadlinePolicy`（`Map<String, Duration> perToolDeadlines`，`deadline(toolName)` 查表）；`ToolCallExecutor.awaitWithTimeout` 改为按工具名查表超时（替代全局 `toolTimeoutMs`）。超时中断执行返回超时错误码+已耗时。
- **涉及文件**：
  - 新建 `domain/guard/timeout/PerToolDeadlinePolicy.java`
  - 修改 `domain/tool/service/ToolCallExecutor.java`（awaitWithTimeout 查表）
- **验收标准**：
  - [ ] 配置工具 deadline 后该工具按独立 deadline 执行超时控制（满足 REQ-J5-P1-1）
  - [ ] 工具执行超 deadline 时中断执行，返回超时错误码及已耗时（满足 REQ-J5-P1-2）
- **参考 DSH 包**：`packages/guard/timeout`

### T2.022 OtelService 与 OTLP 导出
- **优先级**：P1 ｜ **域**：K ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：T2.003
- **描述**：新建 `domain/telemetry` 包。`OtelService`（`start()`/`startSpan(name)`/`exportOtlp(signals)`）；`IOtelExporterPort` 端口；`OtlpHttpExporter` 适配器（`opentelemetry-java` + `opentelemetry-exporter-otlp`）。span/log/metric 字节有界上报。`@ConditionalOnProperty(name="dsh.otel.enabled")`。`app` 启动时装配。
- **涉及文件**：
  - 新建 `domain/telemetry/service/OtelService.java`
  - 新建 `domain/telemetry/adapter/port/IOtelExporterPort.java`
  - 新建 `infrastructure/adapter/otel/OtlpHttpExporter.java`
  - 新增 Maven 依赖 `opentelemetry-*`
- **验收标准**：
  - [ ] 启用 otel 后 OTel 服务启动，span/log/metric 经 OTLP 字节有界上报（满足 REQ-K1-P1-1）
- **参考 DSH 包**：`packages/telemetry/otel`

### T2.023 GlobTool 与 GrepTool
- **优先级**：P1 ｜ **域**：L ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `GlobTool`（glob 模式匹配返回文件路径）与 `GrepTool`（正则内容检索返回匹配行），对齐 DSH `tool-fs-search` 行为。在 `ToolRegistry` 注册。复用 `IFsPort`。
- **涉及文件**：
  - 新建 `domain/tool/fs/GlobTool.java`
  - 新建 `domain/tool/fs/GrepTool.java`
- **验收标准**：
  - [ ] 调用 glob/grep 工具返回匹配文件路径/匹配行，行为与 DSH tool-fs-search 对齐（满足 REQ-L3-P1-1）
- **参考 DSH 包**：`packages/tool/fs-search`

### T2.024 ScheduleTool 模型面向工具
- **优先级**：P1 ｜ **域**：M ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ScheduleTool`（模型面向工具，创建/修改/取消提醒任务）。在 `ToolRegistry` 注册。复用现有 `schedule` 域服务。
- **涉及文件**：
  - 新建 `domain/tool/schedule/ScheduleTool.java`
- **验收标准**：
  - [ ] 模型调用 schedule tool 可创建/修改/取消提醒任务，提醒按计划触发（满足 REQ-M2-P1-1）
- **参考 DSH 包**：`packages/tool/schedule`

### T2.025 WorkspaceEntity 与附着校验
- **优先级**：P1 ｜ **域**：M ｜ **模块**：domain, infrastructure, app ｜ **复杂度**：L
- **依赖**：T1.015
- **描述**：新建 `domain/session/workspace` 包。`WorkspaceEntity`（workspaceId/rootPath/attachedSessionIds）；`IWorkspaceRepository` 端口；`JdbcWorkspaceRepository` 适配器。`SessionHeader` 新增 `workspaceId` 字段。`SessionEventLogService.createSession` 校验 workspace 附着关系，识别孤立 Session。新增 `workspace` 表 + `session_workspace` 关联表。
- **涉及文件**：
  - 新建 `domain/session/workspace/model/entity/WorkspaceEntity.java`
  - 新建 `domain/session/workspace/adapter/repository/IWorkspaceRepository.java`
  - 新建 `infrastructure/adapter/repository/JdbcWorkspaceRepository.java`
  - 修改 `domain/session/event/model/entity/SessionHeader.java`（新增 workspaceId）
  - 修改 `domain/session/log/service/SessionEventLogService.java`（附着校验）
  - 新增 `app/src/main/resources/db/migration/V*__workspace.sql`
- **验收标准**：
  - [ ] 创建/查询 Workspace 记录持久化，Session 附着关系经校验，孤立 Session 被识别（满足 REQ-M4-P1-1）
- **参考 DSH 包**：`packages/session/workspace`

### T2.026 BundleDefinition 与 BundleAssembler
- **优先级**：P1 ｜ **域**：N ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `domain/runtime/profile/bundle` 包。`BundleDefinition`（base/headless/sdk-app/sdk-minimal/acp-app/web-app bundle 定义）；`BundleAssembler`（按 bundle 定义装配对应插件集与配置）。现有 `ExecutionProfile` 降级为 `BundleDefinition` 的一个属性。
- **涉及文件**：
  - 新建 `domain/runtime/profile/bundle/BundleDefinition.java`
  - 新建 `domain/runtime/profile/bundle/BundleAssembler.java`
  - 修改 `domain/runtime/profile/ExecutionProfile.java`（降级为 bundle 属性）
- **验收标准**：
  - [ ] 选择 bundle 组合后按 bundle 定义装配对应插件集与配置（满足 REQ-N2-P1-1）
- **参考 DSH 包**：`packages/runtime/bundle`

### T2.027 TimedWaitAskUser 与 late reply
- **优先级**：P1 ｜ **域**：O ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：改造现有 `AskUserTool` 为 `TimedWaitAskUser`：提问带 timeout 时超时后以默认值/超时结果继续，不阻塞流程；超时后用户提交 late reply 时接受并处理。
- **涉及文件**：
  - 新建 `domain/agent/service/ask/timed/TimedWaitAskUser.java`
  - 修改/废弃现有 `AskUserTool.java`
- **验收标准**：
  - [ ] 提问带 timeout 超时后以默认值/超时结果继续，不阻塞流程（满足 REQ-O5-P1-1）
  - [ ] 提问已超时后用户提交回复被接受并处理（满足 REQ-O5-P1-2）
- **参考 DSH 包**：`packages/sdk/ask-user`

### T2.028 ContentAddressableAttachmentStore
- **优先级**：P1 ｜ **域**：P ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ContentAddressableAttachmentStore` 装饰 `IStorageBackendPort`，以 `SHA-256(content)` 为存储键，相同内容去重。`attachment` 表以 `content_hash` 为键。
- **涉及文件**：
  - 新建 `domain/storage/attachment/ContentAddressableAttachmentStore.java`
  - 新建 `infrastructure/adapter/storage/Sha256ContentAddressableStore.java`
- **验收标准**：
  - [ ] 上传相同内容附件存储键为内容哈希，相同内容去重不重复存储（满足 REQ-P2-P1-1）
- **参考 DSH 包**：`packages/storage/attachment`

### T2.029 PtcSandboxNodeProcess
- **优先级**：P1 ｜ **域**：Q ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `PtcSandboxNodeProcess`（独立 Node 进程隔离，PTC 在沙箱 Node 进程中执行）。`IPtcRuntimePort` 端口；`SandboxedNodePtcProcess` 适配器。`@ConditionalOnProperty(name="dsh.ptc.runtime-node.enabled")`。
- **涉及文件**：
  - 新建 `domain/typert/PtcSandboxNodeProcess.java`（或 `domain/typert/ptc/`）
  - 新建 `domain/typert/adapter/port/IPtcRuntimePort.java`
  - 新建 `infrastructure/adapter/typert/SandboxedNodePtcProcess.java`
- **验收标准**：
  - [ ] 启用 ptc-runtime-node 后 PTC 在沙箱 Node 进程中执行，与主进程隔离（满足 REQ-Q1-P1-1）
- **参考 DSH 包**：`packages/ptc/runtime-node`

### T2.030 TypertGenerator
- **优先级**：P1 ｜ **域**：Q ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `TypertGenerator`（分析 TS 项目生成类型协议定义，可被 `TypertProtocolLoader` 加载）。`ITypertGeneratorPort` 端口；`TsMorphTypertGenerator` 适配器（ts-morph Node 子进程）。
- **涉及文件**：
  - 新建 `domain/typert/generator/TypertGenerator.java`
  - 新建 `domain/typert/generator/adapter/port/ITypertGeneratorPort.java`
  - 新建 `infrastructure/adapter/typert/TsMorphTypertGenerator.java`
- **验收标准**：
  - [ ] 对 TS 项目运行 generator 输出类型协议定义，可被 TypertProtocolLoader 加载（满足 REQ-Q2-P1-1）
- **参考 DSH 包**：`packages/typert/generator`

### T2.031 LspStdioProvider 与 LspTool
- **优先级**：P1 ｜ **域**：Q ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `LspStdioProvider`（LSP 经 stdio JSON-RPC 通信）与 `LspTool`（面向模型的 LSP 工具，查询定义/引用/诊断）。`ILspProviderPort` 端口（已存在则复用）；`StdioLspProvider` 适配器。在 `ToolRegistry` 注册 `LspTool`。
- **涉及文件**：
  - 新建 `domain/lsp/stdio/LspStdioProvider.java`
  - 新建 `domain/tool/lsp/LspTool.java`
  - 新建 `infrastructure/adapter/lsp/StdioLspProvider.java`
- **验收标准**：
  - [ ] 启用 LSP 后 LSP 经 stdio provider 通信，模型可经 LSP tool 查询定义/引用/诊断（满足 REQ-Q3-P1-1）
- **参考 DSH 包**：`packages/lsp/stdio`

### T2.032 ExaSearchProvider 与 PerplexitySearchProvider
- **优先级**：P1 ｜ **域**：R ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ExaSearchProvider` 与 `PerplexitySearchProvider`（实现 `IWebSearchPort`）；现有 `web_search` 工具路由多 Provider 后端（DeepSeek/Exa/Perplexity）。
- **涉及文件**：
  - 新建 `domain/tool/web/search/ExaSearchProvider.java`
  - 新建 `domain/tool/web/search/PerplexitySearchProvider.java`
  - 新建 `infrastructure/adapter/web/ExaHttpSearch.java`
  - 新建 `infrastructure/adapter/web/PerplexityHttpSearch.java`
- **验收标准**：
  - [ ] 配置 Exa/Perplexity Provider 后 web_search 可路由到对应 Provider 并返回结果（满足 REQ-R3-P1-1）
- **参考 DSH 包**：`packages/tool/web-search`

### T2.033 IMcpResourcePort 与 McpResourceDiscovery
- **优先级**：P1 ｜ **域**：D ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：T1.031
- **描述**：新建 `domain/tool/mcp/resource` 包。`IMcpResourcePort`（`listResources(scope)`/`readResource(uri)`）；`McpResourceDiscovery`（scoped 资源发现与读取，按 scope 过滤可见资源）；`StdioMcpResourcePort`/`HttpMcpResourcePort` 适配器。`@ConditionalOnProperty(name="dsh.mcp.resources.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/mcp/resource/adapter/port/IMcpResourcePort.java`
  - 新建 `domain/tool/mcp/resource/service/McpResourceDiscovery.java`
  - 新建 `infrastructure/adapter/mcp/StdioMcpResourcePort.java`
- **验收标准**：
  - [ ] 启用 mcp-resources 并按 scope 查询时仅返回 scope 匹配的资源列表及内容（满足 REQ-D2-P1-1）
- **参考 DSH 包**：`packages/mcp/resources`

### T2.034 ToolPresentation（Agent Tool Presentation）
- **优先级**：P1 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ToolPresentation` 值对象（PTC/native/selector 三种呈现形式）。工具注册时声明 presentation，模型可见声明形式之一。`@ConditionalOnProperty(name="dsh.agent-tool-presentation.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/model/valobj/ToolPresentation.java`
  - 修改工具注册接口（增 presentation 声明）
- **验收标准**：
  - [ ] 工具注册时声明 presentation 后模型可见 PTC/native/选择器三种呈现形式之一（满足 REQ-A4-P1-1）
- **参考 DSH 包**：`packages/tool/presentation`

### T2.035 AgentDefaultModelProvider
- **优先级**：P1 ｜ **域**：A ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：无
- **描述**：新建 `AgentDefaultModelProvider`（共享默认模型选择服务）。入口未显式指定模型时使用该 provider 配置的默认模型。
- **涉及文件**：
  - 新建 `domain/agent/service/AgentDefaultModelProvider.java`
  - 修改入口调用处（未传 model 参数时回退到默认）
- **验收标准**：
  - [ ] 入口调用未传 model 参数时系统使用 agent-default-model 配置的默认模型（满足 REQ-A5-P1-1）
- **参考 DSH 包**：`packages/agent/default-model`


---

# 四、Phase 3 — P2 生态扩展（38 任务，目标 v0.2.0）

> **目标**：扩展平台覆盖（Linux/macOS/Windows 沙箱）、生态能力（Shell/SSH/子代理/工作流/存储/技能/凭证/Webhook/Computer-use/Browser-use 等）。本阶段为可选扩展，按需启用，不影响 Phase 1/2 核心底座稳定性。**依赖 Phase 1**（P2 生态扩展依赖 P0 架构基座），部分可与 Phase 2 并行。
>
> **子阶段**：3a 跨平台沙箱（T3.001-T3.004）/ 3b Shell/SSH/进程（T3.005-T3.011）/ 3c 工作流/子代理（T3.012-T3.017）/ 3d 存储/技能/凭证（T3.018-T3.024）/ 3e 其他扩展（T3.025-T3.038）

## 4.1 子阶段 3a — 跨平台沙箱（G 域，T3.001-T3.004）

### T3.001 LandlockSandbox（Linux）
- **优先级**：P2 ｜ **域**：G ｜ **模块**：infrastructure ｜ **复杂度**：XL
- **依赖**：T2.017
- **描述**：新建 `LandlockSandbox` 实现 `ISandboxEnforcerPort`（Linux Landlock 文件系统隔离，bwrap fallback）。`@ConditionalOnOs(name="linux")` + `@ConditionalOnProperty(name="dsh.sandbox.local.enabled")`。Landlock 失败回退 bwrap，bwrap 失败回退无沙箱+警告。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/linux/LandlockSandbox.java`
- **验收标准**：
  - [ ] Linux + 启用 sandbox-local 时工具执行被限制在允许的文件系统路径内（满足 REQ-G1-P2-1）
  - [ ] Landlock 失败时回退 bwrap
- **参考 DSH 包**：`packages/sandbox/landlock`

### T3.002 SeatbeltSandbox（macOS）
- **优先级**：P2 ｜ **域**：G ｜ **模块**：infrastructure ｜ **复杂度**：L
- **依赖**：T2.017
- **描述**：新建 `SeatbeltSandbox` 实现 `ISandboxEnforcerPort`（macOS sandbox-exec Seatbelt 策略隔离）。`@ConditionalOnOs(name="mac")` + `@ConditionalOnProperty`。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/macos/SeatbeltSandbox.java`
- **验收标准**：
  - [ ] macOS + 启用 sandbox-local 时工具执行被 Seatbelt 策略约束（满足 REQ-G2-P2-1）
- **参考 DSH 包**：`packages/sandbox/seatbelt`

### T3.003 AclSandbox（Windows 受限 token）
- **优先级**：P2 ｜ **域**：G ｜ **模块**：infrastructure ｜ **复杂度**：XL
- **依赖**：T2.017
- **描述**：新建 `AclSandbox` 实现 `ISandboxEnforcerPort`（Windows 受限 token + capability-SID 写白名单 ACL 沙箱）。`@ConditionalOnOs(name="windows")` + `@ConditionalOnProperty`。JNA 调用 Win32 API。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/windows/AclSandbox.java`
- **验收标准**：
  - [ ] Windows + 启用 ACL 沙箱时进程以受限 token 运行，仅白名单路径可写（满足 REQ-G3-P2-1）
- **参考 DSH 包**：`packages/sandbox/acl`

### T3.004 AclDiagnostic 拒绝诊断
- **优先级**：P2 ｜ **域**：G ｜ **模块**：infrastructure ｜ **复杂度**：M
- **依赖**：T3.003
- **描述**：新建 `AclDiagnostic`：ACL 沙箱拒绝写入时返回诊断信息（被拒绝路径、拒绝原因、建议修复）。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/windows/AclDiagnostic.java`
- **验收标准**：
  - [ ] 写入被 ACL 拒绝时返回诊断信息含路径、拒绝原因、建议修复（满足 REQ-G3-P2-2）
- **参考 DSH 包**：`packages/sandbox/acl-diagnostic`

## 4.2 子阶段 3b — Shell/SSH/进程（L 域，T3.005-T3.011）

### T3.005 StrReplaceEditorTool
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `StrReplaceEditorTool`（view/create/replace/insert 四种字符串替换编辑操作，含精确匹配与冲突检测）。`@ConditionalOnProperty(name="dsh.tool.str-replace-editor.enabled")`。在 `ToolRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/tool/fs/StrReplaceEditorTool.java`
- **验收标准**：
  - [ ] 启用 str-replace-editor 后四种操作可用，含精确匹配与冲突检测（满足 REQ-L4-P2-1）
- **参考 DSH 包**：`packages/tool/str-replace-editor`

### T3.006 FsObservationPolicy
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `FsObservationPolicy`（文件系统观察策略，文件变更按策略观察并通知）。`@ConditionalOnProperty(name="dsh.fs.observation-policy.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/fs/FsObservationPolicy.java`
- **验收标准**：
  - [ ] 启用 fs-observation-policy 后文件变更按策略观察并通知（满足 REQ-L5-P2-1）
- **参考 DSH 包**：`packages/tool/fs-observation`

### T3.007 PowerShellExecutor
- **优先级**：P2 ｜ **域**：L ｜ **模块**：infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `PowerShellExecutor`（PowerShell 本地和沙箱执行，`ProcessBuilder` + `pwsh` 命令）。`@ConditionalOnOs(name="windows")` + `@ConditionalOnProperty(name="dsh.pwsh.enabled")`。
- **涉及文件**：
  - 新建 `infrastructure/adapter/shell/PowerShellExecutor.java`
- **验收标准**：
  - [ ] Windows + 启用 pwsh 时 PowerShell 命令可在本地和沙箱中执行（满足 REQ-L6-P2-1）
- **参考 DSH 包**：`packages/shell/pwsh`

### T3.008 PersistentShellTool
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `PersistentShellTool`（持久化 Shell 工具，跨调用保持 Shell 状态：环境变量/工作目录保持）。`ProcessBuilder` + PTY（`pty4j`）。`@ConditionalOnProperty(name="dsh.tool.bash-persistent.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/shell/persistent/PersistentShellTool.java`
  - 新增 Maven 依赖 `pty4j`
- **验收标准**：
  - [ ] 启用持久 Shell 后多次工具调用复用同一 Shell 进程，环境变量/工作目录保持（满足 REQ-L7-P2-1）
- **参考 DSH 包**：`packages/shell/persistent`

### T3.009 ManagedEnvRegistry（DSH_* 受管环境变量）
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `ManagedEnvRegistry`（`DSH_*` 前缀受管环境变量注册表，可枚举/校验/注入子进程）。
- **涉及文件**：
  - 新建 `domain/runtime/setting/ManagedEnvRegistry.java`
- **验收标准**：
  - [ ] 注册 DSH_ 前缀变量后变量经注册表管理，可枚举/校验/注入子进程（满足 REQ-L8-P2-1）
- **参考 DSH 包**：`packages/runtime/managed-env`

### T3.010 SSH 全套（连接/FS over SSH/沙箱/子进程）
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain, infrastructure ｜ **复杂度**：XL
- **依赖**：无
- **描述**：新建 `SshConnection`/`FsOverSsh`/`SandboxedSsh`/`SubprocessSsh`（SSH 连接、FS over SSH、沙箱 SSH、子进程 SSH）。`ISshPort` 端口；`ApacheSshdConnection` 适配器（Apache MINA SSHD）。`@ConditionalOnProperty(name="dsh.ssh.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/ssh/SshConnection.java`、`FsOverSsh.java`、`SandboxedSsh.java`、`SubprocessSsh.java`
  - 新建 `domain/tool/ssh/adapter/port/ISshPort.java`
  - 新建 `infrastructure/adapter/ssh/ApacheSshdConnection.java`
  - 新增 Maven 依赖 `sshd-core`/`sshd-sftp`
- **验收标准**：
  - [ ] 启用 ssh 后可经 SSH 执行远程命令/文件操作/沙箱隔离/子进程（满足 REQ-L9-P2-1）
- **参考 DSH 包**：`packages/ssh`

### T3.011 JobObject（Win32 进程组控制）
- **优先级**：P2 ｜ **域**：L ｜ **模块**：infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `JobObject`（Win32 Job Object 进程组控制原语，JNA + kernel32 API）。`IWin32ProcessPort` 端口；`JnaJobObject` 适配器。`@ConditionalOnOs(name="windows")` + `@ConditionalOnProperty(name="dsh.win32-process.enabled")`。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/win32/JobObject.java`
  - 新建 `domain/tool/ssh/adapter/port/IWin32ProcessPort.java`（或合适位置）
  - 新增 Maven 依赖 `jna`
- **验收标准**：
  - [ ] Windows + 启用 win32-process 时可创建 Job Object 管理子进程组生命周期（满足 REQ-L10-P2-1）
- **参考 DSH 包**：`packages/win32/job-object`

## 4.3 子阶段 3c — 工作流/子代理（H/I 域，T3.012-T3.017）

### T3.012 SdkSubagentDriver（进程外 SDK 子代理）
- **优先级**：P2 ｜ **域**：H ｜ **模块**：domain, infrastructure ｜ **复杂度**：XL
- **依赖**：T1.036
- **描述**：新建 `SdkSubagentDriver`（进程外 SDK 子代理，stdio JSON-RPC 通信）。`ISubagentDriverPort` 端口；`StdioSdkSubagentDriver` 适配器。`@ConditionalOnProperty(name="dsh.subagent.dsh-sdk.enabled")`。
- **涉及文件**：
  - 新建 `domain/agent/service/subagent/SdkSubagentDriver.java`
  - 新建 `domain/agent/service/subagent/adapter/port/ISubagentDriverPort.java`
  - 新建 `infrastructure/adapter/subagent/StdioSdkSubagentDriver.java`
- **验收标准**：
  - [ ] 启用 SDK 子代理后子代理以独立进程运行，经 JSON-RPC stdio 通信（满足 REQ-H1-P2-1）
- **参考 DSH 包**：`packages/subagent/sdk-driver`

### T3.013 子代理控制工具（send_message/interrupt/list_agents）
- **优先级**：P2 ｜ **域**：H ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：T3.012
- **描述**：新建 `SendMessageTool`/`InterruptTool`/`ListAgentsTool`（向子代理发消息/中断/列举）。在 `ToolRegistry` 注册。`@ConditionalOnProperty(name="dsh.tool.subagent-control.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/subagent/SendMessageTool.java`、`InterruptTool.java`、`ListAgentsTool.java`
- **验收标准**：
  - [ ] 启用子代理控制后三个工具可用，可向子代理发消息/中断/列举（满足 REQ-H3-P2-1）
- **参考 DSH 包**：`packages/tool/subagent-control`

### T3.014 PtcRuntimeOrchestrator
- **优先级**：P2 ｜ **域**：I ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T2.029
- **描述**：新建 `PtcRuntimeOrchestrator`（沙箱 Node PTC 运行时编排）。`@ConditionalOnProperty(name="dsh.workflow.ptc.enabled")`。
- **涉及文件**：
  - 新建 `domain/workflow/ptc/PtcRuntimeOrchestrator.java`
- **验收标准**：
  - [ ] 启用 workflow-ptc 后可在沙箱 Node 中编排 PTC 工作流（满足 REQ-I1-P2-1）
- **参考 DSH 包**：`packages/workflow/ptc`

### T3.015 RalphLoopTool
- **优先级**：P2 ｜ **域**：I ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `RalphLoopTool`（fresh-agent Ralph loop 工具）。`@ConditionalOnProperty(name="dsh.tool.ralph.enabled")`。在 `ToolRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/tool/goal/RalphLoopTool.java`
- **验收标准**：
  - [ ] 启用 tool-ralph 后可启动 fresh-agent Ralph 循环（满足 REQ-I2-P2-1）
- **参考 DSH 包**：`packages/tool/ralph`

### T3.016 GoalRoundDriver
- **优先级**：P2 ｜ **域**：I ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `GoalRoundDriver`（竞争隔离的 goal-round 驱动，多 goal 竞争时按隔离策略调度）。`IGoalRoundPort` 端口；`InProcessGoalRound` 适配器。`@ConditionalOnProperty(name="dsh.goal-round-driver.enabled")`。`goal` 域新增 `round_state` 字段。
- **涉及文件**：
  - 新建 `domain/goal/round/GoalRoundDriver.java`
  - 新建 `domain/goal/round/adapter/port/IGoalRoundPort.java`
  - 新建 `infrastructure/adapter/goal/InProcessGoalRound.java`
- **验收标准**：
  - [ ] 启用 goal-round-driver 后多 goal 竞争时按隔离策略调度（满足 REQ-I3-P2-1）
- **参考 DSH 包**：`packages/goal/round-driver`

### T3.017 GoalCommand（/goal slash 命令）
- **优先级**：P2 ｜ **域**：I ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：T3.016
- **描述**：新建 `GoalCommand`（`/goal` slash 命令，启动 goal 驱动流程，进入 goal 模式）。在 `SlashCommandRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/goal/GoalCommand.java`
- **验收标准**：
  - [ ] 用户输入 /goal 时启动 goal 驱动流程，进入 goal 模式（满足 REQ-I4-P2-1）
- **参考 DSH 包**：`packages/goal/command`

## 4.4 子阶段 3d — 存储/技能/凭证（L/J/K 域，T3.018-T3.024）

### T3.018 StorageDomain（KV schema + 事件发射）
- **优先级**：P2 ｜ **域**：L ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `StorageDomain`（KV 域数据形式，schema 校验 + 事件发射持久化）。`@ConditionalOnProperty(name="dsh.storage-domain.enabled")`。
- **涉及文件**：
  - 新建 `domain/storage/domain/StorageDomain.java`
- **验收标准**：
  - [ ] 启用 storage-domain 后 KV 数据经 schema 校验后以事件发射持久化（满足 REQ-L1-P2-1）
- **参考 DSH 包**：`packages/storage/domain`

### T3.019 SqliteSessionEventStore
- **优先级**：P2 ｜ **域**：L ｜ **模块**：infrastructure ｜ **复杂度**：L
- **依赖**：T1.024
- **描述**：新建 `SqliteSessionEventStore` 实现 `SessionPersistenceSeam`（SQLite 存储后端，事件流/投影/附件可经 SQLite 持久化）。`sqlite-jdbc`（Xerial），WAL 模式。`@ConditionalOnProperty(name="dsh.storage.sqlite.enabled")`。
- **涉及文件**：
  - 新建 `infrastructure/adapter/eventstore/SqliteSessionEventStore.java`
  - 新增 Maven 依赖 `sqlite-jdbc`
  - 新增 SQLite 建表脚本
- **验收标准**：
  - [ ] 配置 SQLite 后端后事件流/投影/附件可经 SQLite 持久化（满足 REQ-L2-P2-1）
  - [ ] 通过 `SessionPersistenceContractTest` 契约测试
- **参考 DSH 包**：`packages/storage/sqlite`

### T3.020 OfficeSkill
- **优先级**：P2 ｜ **域**：J ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `OfficeSkill`（Office 工作流技能，实现 `ISkillProviderPort`）。`@ConditionalOnProperty(name="dsh.skill.office.enabled")`。
- **涉及文件**：
  - 新建 `domain/skill/office/OfficeSkill.java`
- **验收标准**：
  - [ ] 启用 skill-office 后 Office 文档工作流技能可用（满足 REQ-J1-P2-1）
- **参考 DSH 包**：`packages/skill/office`

### T3.021 BadgeSkill
- **优先级**：P2 ｜ **域**：J ｜ **模块**：domain ｜ **复杂度**：S
- **依赖**：无
- **描述**：新建 `BadgeSkill`（badge 技能，实现 `ISkillProviderPort`）。`@ConditionalOnProperty(name="dsh.skill.badge.enabled")`。
- **涉及文件**：
  - 新建 `domain/skill/badge/BadgeSkill.java`
- **验收标准**：
  - [ ] 启用 skill-badge 后 badge 技能可用（满足 REQ-J2-P2-1）
- **参考 DSH 包**：`packages/skill/badge`

### T3.022 AnonymousUserIdProvider
- **优先级**：P2 ｜ **域**：K ｜ **模块**：domain, infrastructure, app ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `AnonymousUserIdProvider`（未认证用户生成稳定匿名身份标识，后续访问复用同一 ID）。`IAnonymousIdStorePort` 端口；`JdbcAnonymousIdStore` 适配器；新增 `anonymous_user_id` 表。`@ConditionalOnProperty(name="dsh.anonymous-user-id.enabled")`。
- **涉及文件**：
  - 新建 `domain/credentials/anonymous/AnonymousUserIdProvider.java`
  - 新建 `domain/credentials/anonymous/adapter/port/IAnonymousIdStorePort.java`
  - 新建 `infrastructure/adapter/credentials/JdbcAnonymousIdStore.java`
  - 新增 `app/src/main/resources/db/migration/V*__anonymous_user_id.sql`
- **验收标准**：
  - [ ] 未认证用户首次访问生成稳定匿名 ID，后续访问复用同一 ID（满足 REQ-K2-P2-1）
- **参考 DSH 包**：`packages/credentials/anonymous`

### T3.023 AuthorizationSeam（对话式凭证获取）
- **优先级**：P2 ｜ **域**：K ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `AuthorizationSeam`（对话式凭证获取流 seam，凭证缺失时以对话流获取，用户显式确认）。`ICredentialDialogPort` 端口；`HttpCredentialDialog` 适配器。`@ConditionalOnProperty(name="dsh.authorization.enabled")`。
- **涉及文件**：
  - 新建 `domain/credentials/authorization/AuthorizationSeam.java`
  - 新建 `domain/credentials/authorization/adapter/port/ICredentialDialogPort.java`
  - 新建 `infrastructure/adapter/credentials/HttpCredentialDialog.java`
- **验收标准**：
  - [ ] 启用 authorization seam 后凭证缺失时以对话流获取，用户显式确认（满足 REQ-K3-P2-1）
- **参考 DSH 包**：`packages/credentials/authorization`

### T3.024 PkceAuthorizer（PKCE 浏览器授权）
- **优先级**：P2 ｜ **域**：K ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `PkceAuthorizer`（PKCE 浏览器授权流程，经浏览器 PKCE 流获取 token，凭证不落明文）。`java.awt.Desktop.browse` 唤起浏览器 + 本地回调 server。`@ConditionalOnProperty(name="dsh.deepseek-account.enabled")`。
- **涉及文件**：
  - 新建 `domain/credentials/pkce/PkceAuthorizer.java`
  - 新建 `infrastructure/adapter/credentials/BrowserPkceFlow.java`
- **验收标准**：
  - [ ] 启用 PKCE 授权后经浏览器 PKCE 流获取 token，凭证不落明文（满足 REQ-K4-P2-1）
- **参考 DSH 包**：`packages/credentials/pkce`

## 4.5 子阶段 3e — 其他扩展（T3.025-T3.038）

### T3.025 NativeSystemPrimitive（Landlock + flock JNA）
- **优先级**：P2 ｜ **域**：S ｜ **模块**：infrastructure ｜ **复杂度**：L
- **依赖**：T3.001
- **描述**：新建 `NativeSystemPrimitive`（JNA 封装入口）；`LandlockPrimitive`（`landlock_create_ruleset` 等 syscall）；`FlockPrimitive`（flock 文件锁）。`INativePrimitivePort` 端口；`JnaLandlock`/`JnaFlock` 适配器。`@ConditionalOnProperty(name="dsh.native-system.enabled")`。
- **涉及文件**：
  - 新建 `infrastructure/adapter/sandbox/native/NativeSystemPrimitive.java`
  - 新建 `infrastructure/adapter/sandbox/native/LandlockPrimitive.java`、`FlockPrimitive.java`
  - 新增 Maven 依赖 `jna`
- **验收标准**：
  - [ ] 启用 native-system 后可调用 Landlock 设置文件访问限制，可调用 flock 加文件锁（满足 REQ-S1-P2-1）
- **参考 DSH 包**：`packages/native/system-primitive`

### T3.026 JobControlTool
- **优先级**：P2 ｜ **域**：M ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `JobControlTool`（Job CRUD 工具，创建/查询/取消/列举 Job）。在 `ToolRegistry` 注册。`@ConditionalOnProperty(name="dsh.tool.jobs.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/jobs/JobControlTool.java`
- **验收标准**：
  - [ ] 启用 tool-jobs 后可创建/查询/取消/列举 Job（满足 REQ-M1-P2-1）
- **参考 DSH 包**：`packages/tool/jobs`

### T3.027 WebhookReceiver 与 GitHubWebhookHandler
- **优先级**：P2 ｜ **域**：M ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `WebhookReceiver`（Webhook 接收）与 `GitHubWebhookHandler`（GitHub 事件经 HMAC-SHA256 签名校验后处理）。`IWebhookPort` 端口；`HttpWebhookReceiver` 适配器。`@ConditionalOnProperty(name="dsh.webhook.enabled")`。
- **涉及文件**：
  - 新建 `domain/webhook/WebhookReceiver.java`、`GitHubWebhookHandler.java`
  - 新建 `domain/webhook/adapter/port/IWebhookPort.java`
  - 新建 `infrastructure/adapter/webhook/HttpWebhookReceiver.java`
- **验收标准**：
  - [ ] 启用 webhook 后可接收 Webhook 事件，GitHub 事件经签名校验后处理（满足 REQ-M3-P2-1）
- **参考 DSH 包**：`packages/webhook`

### T3.028 YamlPresetLoader 与 PersonaDefinition
- **优先级**：P2 ｜ **域**：N ｜ **模块**：domain ｜ **复杂度**：L
- **依赖**：T2.026
- **描述**：新建 `YamlPresetLoader`（YAML 声明式 Preset 加载，SnakeYAML）与 `PersonaDefinition`（persona 定义）。系统按声明装配插件/配置/persona。`@ConditionalOnProperty(name="dsh.agent-preset.enabled")`。
- **涉及文件**：
  - 新建 `domain/runtime/profile/preset/YamlPresetLoader.java`、`PersonaDefinition.java`
- **验收标准**：
  - [ ] 提供 YAML Preset 后系统按声明装配插件/配置/persona（满足 REQ-N1-P2-1）
- **参考 DSH 包**：`packages/runtime/preset`

### T3.029 RemotePluginInventory
- **优先级**：P2 ｜ **域**：N ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：T1.034
- **描述**：新建 `RemotePluginInventory`（远程插件清单发现）。`IRemoteInventoryPort` 端口；`HttpRemoteInventory` 适配器。`@ConditionalOnProperty(name="dsh.plugin-inventory.enabled")`。
- **涉及文件**：
  - 新建 `domain/plugin/registry/RemotePluginInventory.java`
  - 新建 `domain/plugin/registry/adapter/port/IRemoteInventoryPort.java`
  - 新建 `infrastructure/adapter/plugin/HttpRemoteInventory.java`
- **验收标准**：
  - [ ] 启用 plugin-inventory 后可从远程清单发现并列举可用插件（满足 REQ-N5-P2-1）
- **参考 DSH 包**：`packages/plugin/inventory-remote`

### T3.030 DirectoryPicker
- **优先级**：P2 ｜ **域**：N ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `DirectoryPicker`（交互式目录选择器，返回路径）。`IDirectoryPickerPort` 端口；`NativeDirectoryPicker` 适配器。`@ConditionalOnProperty(name="dsh.directory-picker.enabled")`。
- **涉及文件**：
  - 新建 `domain/tool/DirectoryPicker.java`（或合适位置）
  - 新建 `infrastructure/adapter/picker/NativeDirectoryPicker.java`
- **验收标准**：
  - [ ] 启用 directory-picker 后可交互式选择目录并返回路径（满足 REQ-N6-P2-1）
- **参考 DSH 包**：`packages/tool/directory-picker`

### T3.031 MessageFeedbackService 与 FeedbackCommand
- **优先级**：P2 ｜ **域**：O ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `MessageFeedbackService`（消息评分和笔记反馈）与 `FeedbackCommand`（`/feedback` slash 命令打开反馈对话框）。`@ConditionalOnProperty(name="dsh.message-feedback.enabled")`。新增 `message_feedback` 表。
- **涉及文件**：
  - 新建 `domain/sdk/feedback/MessageFeedbackService.java`、`FeedbackCommand.java`
  - 新增 `app/src/main/resources/db/migration/V*__message_feedback.sql`
- **验收标准**：
  - [ ] 启用 message-feedback 后可对消息评分/记笔记，/feedback 命令打开反馈对话框（满足 REQ-O2-P2-1）
- **参考 DSH 包**：`packages/sdk/feedback`

### T3.032 SlashCommandRegistry
- **优先级**：P2 ｜ **域**：O ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `SlashCommandRegistry`（人类命令注册表，统一管理 slash 命令的注册与分发，可列举/帮助/禁用）。`ISlashCommandPort` 端口；`InMemorySlashCommandRegistry` 适配器。`ReactLoopAgent` 消息预处理经注册表分发。新增 `slash_command` 表。
- **涉及文件**：
  - 新建 `domain/sdk/command/SlashCommandRegistry.java`
  - 新建 `infrastructure/adapter/sdk/InMemorySlashCommandRegistry.java`
  - 修改 `domain/agent/service/run/ReactLoopAgent.java`（消息预处理分发）
- **验收标准**：
  - [ ] 注册 slash 命令后命令经注册表统一分发，可列举/帮助/禁用（满足 REQ-O3-P2-1）
- **参考 DSH 包**：`packages/sdk/command-registry`

### T3.033 PermissionPreset
- **优先级**：P2 ｜ **域**：O ｜ **模块**：domain ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `PermissionPreset`（权限预设，按预设批量配置工具/资源权限）。`@ConditionalOnProperty(name="dsh.permission-presets.enabled")`。
- **涉及文件**：
  - 新建 `domain/task/permission/PermissionPreset.java`
- **验收标准**：
  - [ ] 启用 permission-presets 后可按预设批量配置工具/资源权限（满足 REQ-O4-P2-1）
- **参考 DSH 包**：`packages/sdk/permission-preset`

### T3.034 SpillStore 与 SpillPolicy
- **优先级**：P2 ｜ **域**：O ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `SpillStore`（溢出存储）与 `SpillPolicy`（溢出策略，超界内容按策略转存外部存储以引用替代）。`ISpillStorePort` 端口；`JdbcSpillStore` 适配器。`@ConditionalOnProperty(name="dsh.spill.enabled")`。新增 `spill_ref` 表。
- **涉及文件**：
  - 新建 `domain/sdk/spill/SpillStore.java`、`SpillPolicy.java`
  - 新建 `infrastructure/adapter/storage/JdbcSpillStore.java`
  - 新增 `app/src/main/resources/db/migration/V*__spill_ref.sql`
- **验收标准**：
  - [ ] 启用 spill 后超界内容按策略转存外部存储，以引用替代（满足 REQ-O6-P2-1）
- **参考 DSH 包**：`packages/sdk/spill`

### T3.035 OfficeToPdfConverter
- **优先级**：P2 ｜ **域**：P ｜ **模块**：domain, infrastructure ｜ **复杂度**：M
- **依赖**：无
- **描述**：新建 `OfficeToPdfConverter`（Office 文档转 PDF，LibreOffice headless `soffice --convert-to pdf`）。`IOfficeConverterPort` 端口；`LibreOfficeConverter` 适配器。`@ConditionalOnProperty(name="dsh.office-to-pdf.enabled")`。
- **涉及文件**：
  - 新建 `domain/storage/OfficeToPdfConverter.java`（或合适位置）
  - 新建 `infrastructure/adapter/storage/LibreOfficeConverter.java`
- **验收标准**：
  - [ ] 启用 office-to-pdf 后 Office 文档可转为 PDF 并返回（满足 REQ-P1-P2-1）
- **参考 DSH 包**：`packages/document/office-to-pdf`

### T3.036 DeliverableTool 与 GitWorktreeSnapshot
- **优先级**：P2 ｜ **域**：P ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `DeliverableTool`（交付声明工具）与 `GitWorktreeSnapshot`（git 工作树快照，JGit）。`IGitPort` 端口；`JGitWorktree` 适配器。`@ConditionalOnProperty(name="dsh.tool-present.enabled")`。在 `ToolRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/storage/deliverable/DeliverableTool.java`、`GitWorktreeSnapshot.java`
  - 新建 `infrastructure/adapter/git/JGitWorktree.java`
  - 新增 Maven 依赖 `jgit`
- **验收标准**：
  - [ ] 启用 tool-present 后可声明交付物并生成 git 工作树快照（满足 REQ-P3-P2-1）
- **参考 DSH 包**：`packages/document/deliverable`

### T3.037 ComputerUseTool
- **优先级**：P2 ｜ **域**：R ｜ **模块**：domain, infrastructure ｜ **复杂度**：L
- **依赖**：无
- **描述**：新建 `ComputerUseTool`（计算机使用能力，截图/鼠标/键盘，`java.awt.Robot`）。`IComputerUsePort` 端口；`RobotComputerUse` 适配器。`@ConditionalOnProperty(name="dsh.computer-use.enabled")`。在 `ToolRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/tool/web/computer/ComputerUseTool.java`
  - 新建 `infrastructure/adapter/web/RobotComputerUse.java`
- **验收标准**：
  - [ ] 启用 computer-use 后可执行屏幕截图/鼠标/键盘等计算机操作（满足 REQ-R1-P2-1）
- **参考 DSH 包**：`packages/tool/computer-use`

### T3.038 BrowserUseTool
- **优先级**：P2 ｜ **域**：R ｜ **模块**：domain, infrastructure ｜ **复杂度**：XL
- **依赖**：无
- **描述**：新建 `BrowserUseTool`（浏览器使用能力，导航/点击/输入/截图，Playwright Java）。`IBrowserUsePort` 端口；`PlaywrightBrowserUse` 适配器。`@ConditionalOnProperty(name="dsh.browser-use.enabled")`。在 `ToolRegistry` 注册。
- **涉及文件**：
  - 新建 `domain/tool/web/browser/BrowserUseTool.java`
  - 新建 `infrastructure/adapter/web/PlaywrightBrowserUse.java`
  - 新增 Maven 依赖 `playwright`
- **验收标准**：
  - [ ] 启用 browser-use 后可执行导航/点击/输入/截图等浏览器操作（满足 REQ-R2-P2-1）
- **参考 DSH 包**：`packages/tool/browser-use`


---

# 五、任务依赖图

## 5.1 Phase 1 内部依赖（拓扑序）

```
1a 架构抽象层:
  T1.001 (ScopeKey) ──┬──> T1.002 (ScopedEvent) ──> T1.003 (Dispatcher) ──> T1.004 (InMemory适配器) ──> T1.005 (测试)
                      └──> T1.014 (ReactLoopAgent接入)
  T1.006 (Waterfall模型) ──> T1.007 (WaterfallChain) ──┬──> T1.008 (Prompt waterfall改造) ──> T1.009 (测试)
                                                       └──> T1.025 (LLM waterfall)
  T1.010 (ToolPhase枚举) ──> T1.011 (ToolPipeline) ──> T1.012 (ToolCallExecutor改造) ──> T1.013 (测试)

1b Session 基座:
  T1.015 (FormatVersion) ──> T1.016 (V0-V4迁移) ──> T1.017 (MigrationCatalog) ──> T1.018 (幂等测试)
                                                          └──> T1.019 (pending-free/failed-step)
  T1.020 (投影框架) ──> T1.021 (ProjectionCache) ──> T1.022 (SurfaceProjector改造) ──> T1.023 (测试)
  T1.024 (PersistenceSeam) ── 独立

1c LLM 基座:
  T1.007 (WaterfallChain) ──> T1.025 (LLM waterfall) ──> T1.026 (LlmGenerationService改造)
  T1.027 (Token快照模型) ──> T1.028 (ReplayAwareTokenMeter) ──> T1.029 (Jdbc适配器) ──> T1.030 (测试)

1d MCP 基座:
  T1.031 (重连策略) ── 独立
  T1.032 (命名规范) ── 独立

1e 插件/SDK 基座:
  T1.034 (RuntimeResolution) ──> T1.033 (模块级HMR)
  T1.035 (SDK接口扩展) ──> T1.036 (SDK实现+Stdio路由)
```

## 5.2 Phase 2 内部依赖（依赖 Phase 1）

```
2a Session 增强:
  T1.017 ──> T2.001 (Checkpoint) ──> T2.002 (ReactLoopAgent集成)
  T1.021 ──> T2.003 (SessionTelemetry OTel)
  T1.022 ──> T2.004 (TitleGenerator)
  T1.022 ──> T2.005 (Statistics+TurnOutline)
  T1.021 ──> T2.006 (全文搜索)

2b LLM 增强:
  T1.025 ──> T2.007 (RetryPolicy)
  T2.008 (RequestExtension) ── 独立
  T2.009 (PiAiAdapter) ── 独立

2c Context 域: T2.010-T2.013 多数独立，T2.012 依赖 T1.024
2d Compaction 域: T2.014/T2.015 独立 ──> T2.016 (CompactCommand)

2e 散点增强:
  T1.012 ──> T2.017 (SandboxPolicyInjector)
  T1.012 ──> T2.020 (DuplicateToolGuard)
  T1.012 ──> T2.021 (PerToolDeadline)
  T2.003 ──> T2.022 (OtelService)
  T1.015 ──> T2.025 (Workspace)
  T1.031 ──> T2.033 (MCP资源)
  其余 T2.018/T2.019/T2.023/T2.024/T2.026-T2.032/T2.034/T2.035 独立或弱依赖
```

## 5.3 Phase 3 内部依赖（依赖 Phase 1，部分并行 Phase 2）

```
3a 跨平台沙箱: T2.017 ──> T3.001/T3.002/T3.003 ──> T3.004 (AclDiagnostic)
3b Shell/SSH/进程: T3.005-T3.011 多数独立
3c 工作流/子代理: T1.036 ──> T3.012 ──> T3.013; T2.029 ──> T3.014; T3.016 ──> T3.017
3d 存储/技能/凭证: T1.024 ──> T3.019 (SQLite); 其余独立
3e 其他扩展: T3.001 ──> T3.025 (Native); T1.034 ──> T3.029; T2.026 ──> T3.028; 其余独立

可并行（与 Phase 2 无直接依赖）: T3.037 (ComputerUse), T3.038 (BrowserUse), T3.015 (RalphLoop), T3.020/T3.021 (Skills) 等
```

## 5.4 关键路径（最长依赖链）

**Phase 1 关键路径**（长度 7）：
```
T1.006 (Waterfall模型) → T1.007 (WaterfallChain) → T1.025 (LLM waterfall) → T1.026 (LlmGenerationService改造)
```
另一条关键路径（长度 5）：
```
T1.010 (ToolPhase) → T1.011 (ToolPipeline) → T1.012 (ToolCallExecutor改造) → T1.013 (测试)
```

**跨 Phase 关键路径**（长度 9，全局最长）：
```
T1.006 → T1.007 → T1.025 (LLM waterfall) → T2.007 (RetryPolicy) → [P1完成] → T3.014 (PtcOrchestrator，依赖T2.029)
```
或：
```
T1.010 → T1.011 → T1.012 → T2.017 (SandboxPolicyInjector) → T3.001 (Landlock) → T3.025 (NativePrimitive)
```

**关键路径长度**：9（跨 Phase 最长依赖链）

---

# 六、里程碑

## 6.1 Phase 1 → v0.2.0-alpha

**里程碑节点**（按完成顺序）：

| 里程碑 | 完成任务 | 含义 |
|--------|---------|------|
| M1.1 架构抽象层就绪 | T1.001-T1.014 | Scope 路由 + Waterfall + 五阶段管线可用 |
| M1.2 Session 基座就绪 | T1.015-T1.024 | 格式版本化 + 投影框架 + 持久化 seam |
| M1.3 LLM 基座就绪 | T1.025-T1.030 | LLM waterfall + 重放感知 token |
| M1.4 MCP 基座就绪 | T1.031-T1.032 | 重连策略 + 命名规范 |
| M1.5 插件/SDK 基座就绪 | T1.033-T1.036 | 模块级 HMR + runtime resolution + SDK 协议 |
| **v0.2.0-alpha 发布** | **T1.001-T1.036 全部** | 25 条 P0 需求验收通过，SESSION_FORMAT_VERSION=4 冻结 |

**alpha 准入条件**：
- 全部 36 个 Phase 1 任务完成
- 25 条 P0 需求验收条件通过
- `mvn clean package` + 全部测试 + ArchUnit 通过
- 现有 v0.1.8 API 不破坏（新方法均为 default 或新增接口）
- 性能基准对照 v0.1.8 不退化（核心接口 P99 ≤ 3s）

## 6.2 Phase 2 → v0.2.0-beta

**里程碑节点**：

| 里程碑 | 完成任务 | 含义 |
|--------|---------|------|
| M2.1 Session 增强就绪 | T2.001-T2.006 | 检查点 + OTel + 标题/统计/大纲 + 全文搜索 |
| M2.2 LLM 增强就绪 | T2.007-T2.009 | 重试 + 请求扩展 + pi-ai |
| M2.3 Context 域就绪 | T2.010-T2.013 | AGENTS.md + @file + session 引用 + 时间/tmux |
| M2.4 Compaction 域就绪 | T2.014-T2.016 | 图像卸载 + 剪枝 + /compact |
| M2.5 散点增强就绪 | T2.017-T2.035 | 沙箱注入/子代理/Hook/超时/OTel/FS/Schedule/Workspace/Bundle/timed/附件/PTC/Typert/LSP/Web/MCP资源/Presentation/DefaultModel |
| **v0.2.0-beta 发布** | **T2.001-T2.035 全部** | 41 条 P1 需求验收通过 |

**beta 准入条件**：
- Phase 1（alpha）已发布
- 全部 35 个 Phase 2 任务完成
- 41 条 P1 需求验收条件通过
- 每个子阶段对照 DSH 对应包行为契约编写回归测试

## 6.3 Phase 3 → v0.2.0

**里程碑节点**：

| 里程碑 | 完成任务 | 含义 |
|--------|---------|------|
| M3.1 跨平台沙箱就绪 | T3.001-T3.004 | Linux/macOS/Windows 沙箱 |
| M3.2 Shell/SSH/进程就绪 | T3.005-T3.011 | str-replace/FS观察/PowerShell/持久Shell/受管env/SSH/JobObject |
| M3.3 工作流/子代理就绪 | T3.012-T3.017 | SDK子代理/控制工具/PTC编排/Ralph/goal-round/goal命令 |
| M3.4 存储/技能/凭证就绪 | T3.018-T3.024 | Storage域/SQLite/Office/Badge skill/匿名ID/授权/PKCE |
| M3.5 其他扩展就绪 | T3.025-T3.038 | Native/Job/Webhook/Preset/Inventory/Picker/Feedback/命令注册/权限/Spill/Office-PDF/交付/Computer/Browser |
| **v0.2.0 GA 发布** | **T3.001-T3.038 全部** | 38 条 P2 需求验收通过 |

**GA 准入条件**：
- Phase 2（beta）已发布
- 全部 38 个 Phase 3 任务完成
- 38 条 P2 需求验收条件通过
- 跨平台 CI 矩阵测试（Linux/macOS/Windows）
- 性能基准对照 v0.1.8 不退化

---

# 七、执行指引

## 7.1 通用执行原则

1. **不破坏 v0.1.8 现有 API**：现有接口（`ISessionEventStore`/`IMcpClient`/`IHookService`/`ISdkServerService`/`ISdkClientService`/`ITokenMeterService` 等）仅加方法不减方法；新增方法用 Java 8 default method 提供空默认，旧实现无需改动。破坏性变更走 semver major（v0.2.0 内不破坏）。

2. **保持 ArchUnit 边界**：每新增包/类须满足：
   - `types` 模块零业务依赖
   - `domain` 模块不依赖 infrastructure（端口用 Java 接口表达）
   - `infrastructure` 依赖 domain 端口，不反向
   - `case` 组合 domain 服务，定义事务边界
   - `trigger` 将外部协议转内部调用
   - `api` 不含业务逻辑
   - 每个任务完成后跑 ArchUnit 测试确保边界不破坏

3. **测试先行**：每个任务须包含单元测试 + 必要的集成测试。改造现有类时先编写回归测试锁定 v0.1.8 行为，再实施改造。验收标准中的"回归通过"指 v0.1.8 行为不变。

4. **端口适配器优先**：所有新增能力按端口适配器模式落位。domain 层声明端口（`I*Port`/`I*Repository`），infrastructure 层实现适配器。Spring DI 用 `@Autowired List<T>` 注入多实现 + `@Order` 排序；feature flag 用 `@ConditionalOnProperty`。

5. **类型安全**：无 `any`、无 unchecked cast。用 sealed interface 表达代数和类型（如 `WaterfallOutcome`/`GuardDecision`），用 record 表达不可变值对象，用 enum 表达固定枚举（如 `ToolPhase`）。

6. **配置兼容**：新增配置项均有默认值，不配置则保持 v0.1.8 行为（如 `dsh.scope.enabled=false` 时退化为无 scope 路由）。配置项命名 `dsh.{域}.{特性}.{参数}`。

7. **每个任务独立可提交**：任务粒度 0.5-2 天，一个任务 = 一个可 review 的 PR 单元。任务完成后 `mvn clean package` + 测试 + ArchUnit 全绿方可提交。

## 7.2 与 DSH 参考实现对照方法

每个任务的"参考 DSH 包"字段标注了对应的 deepseek-harness packages 路径（相对 `/Users/mac/liubai/dsh/deepseek-harness`）。对照方法：

1. **行为契约对照**：阅读 DSH 对应包的入口接口与行为契约，理解 WHAT；dsh-java 用 Java 强类型 + 端口适配器表达 HOW。
2. **架构差异适配**（DSH → dsh-java）：
   - Cordis `@service` 声明合并 → Spring `@Service` + 接口 + `@Autowired List<T>` + `@Order`
   - Cordis `@hook` 装饰器 → `IHookService` + `HookPoint` 枚举 + `WaterfallChain`
   - Cordis feature flag → Spring `@ConditionalOnProperty(name="dsh.feature.xxx")`
   - Cordis scope 声明 → `ScopeKey` 值对象 + `ScopeEventDispatcher`（显式创建，非装饰器自动）
   - TS/Python 动态类型 → Java 强类型 + record + sealed interface
   - TS `async/await` → `CompletableFuture` + `Flow.Publisher`（Reactive Streams）
   - Node.js HMR（webpack/turbopack）→ JVM `WatchService` + `URLClassLoader` 重新加载
   - pnpm monorepo → Maven 多模块（7 模块 + plugins 子目录）
   - TS `import` runtime resolution → `IRuntimeResolutionPort` + `Map<String, ResolvedPackage>`
3. **回归测试对照**：每个子阶段完成后，对照 DSH 对应包的行为契约编写回归测试，确保 dsh-java 行为与 DSH 一致。

## 7.3 分阶段执行注意事项

### Phase 1 注意事项

- **Scope 路由**（T1.001-T1.005）：`ScopeKey` 必须为不透明值对象，内部 segments 字段 private final 无 getter，确保消费方无法解析内部结构。`ScopeEventDispatcher` 用 `ConcurrentHashMap` 保证线程安全。
- **Waterfall**（T1.006-T1.009）：`canTerminate=false` 专家返回 `Terminate` 时框架须降级为 `Continue`（不终止），避免误用。单元测试覆盖终止/非终止/混合场景。
- **五阶段管线**（T1.010-T1.013）：around 拦截器用洋葱模型嵌套，嵌套深度上限可配置（默认 8）。现有 PRE/POST Hook 须映射为 pre/post 拦截器保持兼容。`ToolCallExecutor` 改造时保留 `awaitWithTimeout` 与并行/串行模式。
- **Session 迁移**（T1.015-T1.018）：每步迁移必须为冻结纯函数（输入不可变，输出新对象，仅升级不降级，幂等）。迁移过程不写存储，仅内存转换。`MigrationCatalog` 单元测试验证幂等（迁移两次等于一次）。
- **重放感知**（T1.027-T1.030）：`ReplayContext` 须显式传入，不依赖隐式推断。快照 `lastEventStepIndex` 严格校验。单元测试覆盖重放/非重放/部分重放。
- **MCP 重连**（T1.031）：每个 server 独立退避时钟错峰重连，全局重连并发上限可配置，重连失败超 `maxAttempts` 后退化为不可用并告警，不无限重试。
- **模块级 HMR**（T1.033）：JVM 类加载器重新加载可能导致内存泄漏，用独立 `URLClassLoader` per 插件模块，重载时 close 旧加载器；插件模块设计为无状态或可重置；提供回滚机制（重载失败保留旧版本）。限定 HMR 范围为插件模块（非核心底座）。
- **SDK 协议**（T1.035-T1.036）：新方法用 default 实现提供空默认，旧实现无需改动；方法按域分组（session/tools/resources/mcp/...）；考虑未来拆分为多个细粒度接口。

### Phase 2 注意事项

- **投影缓存一致性**（T2.005）：写后合流异步，可能与读取竞态。用 `ConcurrentHashMap` + `lastEventStepIndex` CAS；读取时若缓存落后于事件流触发同步合流；提供强一致读选项（`computeIfAbsent` 同步加载）。
- **OTel 字节有界**（T2.003/T2.022）：drop-oldest 策略可能丢失关键 span。关键 span（错误/慢请求）标记不可丢弃；`droppedCount` 接入监控告警；`byteLimit` 可配置，默认 4MB。
- **timed waits**（T2.027）：超时后以默认值/超时结果继续，不阻塞流程；超时后用户提交 late reply 须被接受并处理（不丢弃）。

### Phase 3 注意事项

- **跨平台沙箱**（T3.001-T3.004）：Landlock/Seatbelt/ACL 为内核级原语，JNA 绑定可能因内核版本/系统策略失败。每平台独立适配器，`@ConditionalOnOs` 激活；提供 fallback（Landlock 失败回退 bwrap，bwrap 失败回退无沙箱+警告）；CI 按平台矩阵测试。
- **SQLite 并发**（T3.019）：SQLite 单写入者限制，启用 WAL 模式（多读单写）；写入经 `SessionWriteLeaseService`（已存在）串行化；大规模场景推荐 JDBC 后端。
- **可并行开发**：T3.037（Computer-use）/T3.038（Browser-use）/T3.015（RalphLoop）/T3.020-T3.021（Skills）等与 Phase 2 无直接依赖，可与 Phase 2 并行开发。

## 7.4 验收策略

1. **每阶段交付可运行版本**：Phase 1 → v0.2.0-alpha，Phase 2 → v0.2.0-beta，Phase 3 → v0.2.0。
2. **对照验证**：每个子阶段完成后，对照 DSH 对应包的行为契约编写回归测试。
3. **格式版本冻结**：Phase 1 完成后 `SESSION_FORMAT_VERSION` 冻结为 4，后续阶段不再变更迁移链。
4. **性能基准**：每阶段进行性能基准对照 v0.1.8，确保核心接口 P99 ≤ 3s 不退化、Session list P99 ≤ 500ms 不退化。
5. **ArchUnit 持续守护**：每个 PR 须通过 ArchUnit 边界测试，确保 DDD 六边形架构不破坏。

---

# 八、任务清单速查表

> 全部 109 个原子任务的 ID/标题/优先级/域/复杂度速查。

## Phase 1（36 任务）

| ID | 标题 | 域 | 复杂度 |
|----|------|----|--------|
| T1.001 | ScopeKey 不透明值对象 | A | M |
| T1.002 | ScopedEvent 与 ScopeSubscription 模型 | A | S |
| T1.003 | ScopeEventDispatcher 与订阅端口 | A | M |
| T1.004 | InMemoryScopeSubscriptionPort 适配器 | A | S |
| T1.005 | Scope 路由集成测试与 ArchUnit | A | S |
| T1.006 | Waterfall 事件链模型 | A | M |
| T1.007 | WaterfallExpert 接口与 WaterfallChain 服务 | A | L |
| T1.008 | System Prompt Assembler 改造为 waterfall | A | XL |
| T1.009 | Prompt Waterfall 集成测试 | A | S |
| T1.010 | ToolPhase 枚举与 Guard/Around 值对象 | A | M |
| T1.011 | ToolPhaseInterceptor 接口与 ToolPipeline 服务 | A | L |
| T1.012 | ToolCallExecutor 改造接入五阶段管线 | A | XL |
| T1.013 | 五阶段管线集成测试 | A | M |
| T1.014 | ReactLoopAgent 事件发布经 ScopeEventDispatcher | A | M |
| T1.015 | SessionFormatVersion 常量与迁移接口 | B | S |
| T1.016 | V0→V4 冻结迁移实现 | B | XL |
| T1.017 | MigrationCatalog 与 SessionRebuilderService 集成 | B | M |
| T1.018 | 迁移链幂等性与回归测试 | B | M |
| T1.019 | LegacyEventAdapter 增强（pending-free/failed-step） | B | L |
| T1.020 | 投影框架类型表与 Provider 契约 | B | M |
| T1.021 | ProjectionCache 写后合流缓存 | B | L |
| T1.022 | SurfaceProjector 改造为 ProjectionProvider | B | M |
| T1.023 | 投影合流集成测试 | B | S |
| T1.024 | SessionPersistenceSeam 正式化与契约测试 | B | M |
| T1.025 | LlmStreamWaterfall 与拦截器 | C | XL |
| T1.026 | LlmGenerationService 改造接入 waterfall | C | L |
| T1.027 | Token 快照模型与仓储端口 | C | S |
| T1.028 | ReplayAwareTokenMeter 实现 | C | L |
| T1.029 | JdbcTokenSnapshotRepository 适配器与表迁移 | C | M |
| T1.030 | 重放感知 Token Meter 测试 | C | S |
| T1.031 | ReconnectPolicy 与 ReconnectingMcpClient | D | L |
| T1.032 | McpToolAdapter 强制命名规范 | D | S |
| T1.033 | ModuleLevelHmrService（模块级 HMR） | N | XL |
| T1.034 | IRuntimeResolutionPort 与 PluginRegistryService 刷新 | N | L |
| T1.035 | ISdkServerService/ISdkClientService 接口扩展 | O | L |
| T1.036 | SdkServerService 实现新方法与 Stdio 路由 | O | L |

## Phase 2（35 任务）

| ID | 标题 | 域 | 复杂度 |
|----|------|----|--------|
| T2.001 | CheckpointStrategy 与仓储 | B | L |
| T2.002 | ReactLoopAgent 集成检查点 | B | M |
| T2.003 | SessionTelemetryExporter 与字节有界 OTLP 缓冲 | B | L |
| T2.004 | SessionTitleGenerator | B | M |
| T2.005 | SessionStatistics 与 TurnOutline 投影 | B | L |
| T2.006 | SessionFullTextIndex 与搜索 Provider | B | L |
| T2.007 | LlmRetryPolicy 与重试拦截器 | C | L |
| T2.008 | LlmRequestExtensionRegistry | C | M |
| T2.009 | PiAiAdapter 与 LlmAdapterFactory 分支 | C | M |
| T2.010 | AgentsMdLoader 层级加载与合并 | E | L |
| T2.011 | FileReferenceExpander @file 内联展开 | E | M |
| T2.012 | SessionSnapshotReference 只读快照 | E | M |
| T2.013 | TimeContextInjector 与 TmuxContextInjector | E | M |
| T2.014 | ImageOffloader 图像卸载 | F | M |
| T2.015 | ToolResultPruner head/middle/tail 剪枝 | F | M |
| T2.016 | CompactCommand slash 命令 | F | S |
| T2.017 | SandboxPolicyInjector 策略注入 | G | M |
| T2.018 | InProcessSubagentDriver 对齐 DSH | H | L |
| T2.019 | CodexHookBridge 与 HookDialect.CODEX | J | M |
| T2.020 | DuplicateToolGuard 增强 | J | M |
| T2.021 | PerToolDeadlinePolicy per-tool 超时 | J | M |
| T2.022 | OtelService 与 OTLP 导出 | K | L |
| T2.023 | GlobTool 与 GrepTool | L | M |
| T2.024 | ScheduleTool 模型面向工具 | M | M |
| T2.025 | WorkspaceEntity 与附着校验 | M | L |
| T2.026 | BundleDefinition 与 BundleAssembler | N | L |
| T2.027 | TimedWaitAskUser 与 late reply | O | M |
| T2.028 | ContentAddressableAttachmentStore | P | M |
| T2.029 | PtcSandboxNodeProcess | Q | L |
| T2.030 | TypertGenerator | Q | L |
| T2.031 | LspStdioProvider 与 LspTool | Q | L |
| T2.032 | ExaSearchProvider 与 PerplexitySearchProvider | R | M |
| T2.033 | IMcpResourcePort 与 McpResourceDiscovery | D | M |
| T2.034 | ToolPresentation（Agent Tool Presentation） | A | M |
| T2.035 | AgentDefaultModelProvider | A | S |

## Phase 3（38 任务）

| ID | 标题 | 域 | 复杂度 |
|----|------|----|--------|
| T3.001 | LandlockSandbox（Linux） | G | XL |
| T3.002 | SeatbeltSandbox（macOS） | G | L |
| T3.003 | AclSandbox（Windows 受限 token） | G | XL |
| T3.004 | AclDiagnostic 拒绝诊断 | G | M |
| T3.005 | StrReplaceEditorTool | L | L |
| T3.006 | FsObservationPolicy | L | M |
| T3.007 | PowerShellExecutor | L | M |
| T3.008 | PersistentShellTool | L | L |
| T3.009 | ManagedEnvRegistry（DSH_* 受管环境变量） | L | M |
| T3.010 | SSH 全套（连接/FS over SSH/沙箱/子进程） | L | XL |
| T3.011 | JobObject（Win32 进程组控制） | L | L |
| T3.012 | SdkSubagentDriver（进程外 SDK 子代理） | H | XL |
| T3.013 | 子代理控制工具（send_message/interrupt/list_agents） | H | M |
| T3.014 | PtcRuntimeOrchestrator | I | L |
| T3.015 | RalphLoopTool | I | L |
| T3.016 | GoalRoundDriver | I | L |
| T3.017 | GoalCommand（/goal slash 命令） | I | S |
| T3.018 | StorageDomain（KV schema + 事件发射） | L | L |
| T3.019 | SqliteSessionEventStore | L | L |
| T3.020 | OfficeSkill | J | M |
| T3.021 | BadgeSkill | J | S |
| T3.022 | AnonymousUserIdProvider | K | M |
| T3.023 | AuthorizationSeam（对话式凭证获取） | K | L |
| T3.024 | PkceAuthorizer（PKCE 浏览器授权） | K | L |
| T3.025 | NativeSystemPrimitive（Landlock + flock JNA） | S | L |
| T3.026 | JobControlTool | M | M |
| T3.027 | WebhookReceiver 与 GitHubWebhookHandler | M | L |
| T3.028 | YamlPresetLoader 与 PersonaDefinition | N | L |
| T3.029 | RemotePluginInventory | N | M |
| T3.030 | DirectoryPicker | N | M |
| T3.031 | MessageFeedbackService 与 FeedbackCommand | O | M |
| T3.032 | SlashCommandRegistry | O | M |
| T3.033 | PermissionPreset | O | M |
| T3.034 | SpillStore 与 SpillPolicy | O | L |
| T3.035 | OfficeToPdfConverter | P | M |
| T3.036 | DeliverableTool 与 GitWorktreeSnapshot | P | L |
| T3.037 | ComputerUseTool | R | L |
| T3.038 | BrowserUseTool | R | XL |

---

> 本编码任务清单覆盖 19 个域、104 条 EARS 需求、109 个原子级编码任务，分 Phase 1/2/3 三阶段实施，目标版本 v0.2.0（alpha → beta → GA）。所有任务遵循 DDD 六边形架构与端口适配器模式，落位到 types/domain/api/case/infrastructure/trigger/app 七模块，不破坏 v0.1.8 现有 API。

