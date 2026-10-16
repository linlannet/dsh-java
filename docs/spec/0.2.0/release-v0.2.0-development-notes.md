# DeepSeek Harness Java v0.2.0 开发说明

> v0.2.0 的主线是**核心底座对齐 Phase 1 收尾**：完成 T1.026-T1.036 共 11 个原子任务，将 LLM 生成服务接入 waterfall、引入重放感知 Token 快照与 JDBC 仓储、MCP 自动重连装饰器、模块级 HMR、Runtime Resolution 端口、完整 SDK 协议扩展与 Stdio 路由；同时完成包路径从 `net.linlan.dsh` 到 `net.linlan.dsh` 的全局迁移。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.2.0` |
| 状态 | Phase 1 全部完成（T1.001-T1.036），Phase 2/3 待续 |
| 基线 | v0.1.9（核心底座对齐 Phase 1 前 25 个任务） |
| 主线 | LLM waterfall 接入、重放感知 Token、MCP 重连、模块级 HMR、Runtime Resolution、SDK 协议扩展、包路径迁移 |
| 架构 | 继续保持 DDD 六边形架构，不改变依赖方向 |
| 新增类 | 22 个（domain 18、infrastructure 2、app 2） |
| 变更类 | 12 个（domain 9、infrastructure 2、app 1） |
| 新增测试 | 46 个（domain 46） |
| 全量测试 | domain 219 + infrastructure 45 = 264 通过，0 回归 |
| 新增依赖 | 无 |

## 2. 版本亮点

1. **LLM 生成服务接入 waterfall**：`LlmGenerationService` 从直接订阅 `ILlmRuntimePort.stream` 改为经 `LlmStreamWaterfall.execute` 包装，支持动态注册拦截器；保留向后兼容构造函数。
2. **重放感知 Token 快照**：`TokenSnapshot` record + `ReplayContext` record + `ITokenSnapshotRepository` 端口 + `ReplayAwareTokenMeter` 实现——重放返回快照不重算，实时增量合流并保存；`JdbcTokenSnapshotRepository` 适配器 + `token_snapshot` 表 + Schema 迁移。
3. **MCP 自动重连**：`ReconnectPolicy` record（指数退避）+ `ReconnectingMcpClient` 装饰器（断连检测 + 自动重连 + 超限抛异常）+ `McpBootstrap` 集成包裹。
4. **MCP 工具命名规范**：`mcp__{server}__{tool}` 强制命名格式，多 server 同名工具无冲突。
5. **模块级 HMR**：`ModuleLevelHmrService` 独立 `URLClassLoader` per 模块，debounce 可配置，重载失败保留旧版本（回滚），`@PreDestroy` stop。
6. **Runtime Resolution**：`ResolvedPackage` record + `IRuntimeResolutionPort` 端口 + `InProcessRuntimeResolution` 适配器 + `PluginRegistryService.refreshResolution`。
7. **完整 SDK 协议扩展**：5 个 DTO record + `ISdkServerService` 新增 8 个 default 方法 + `ISdkClientService` 对应扩展 7 个 default 方法 + `SdkServerService` 实现 + `StdioSdkTransportPort` requestHandler 路由。
8. **包路径全局迁移**：`net.linlan.dsh` → `net.linlan.dsh`，所有模块（api/types/domain/case/infrastructure/trigger/app）源码、测试、配置、SPI 服务文件统一迁移。

## 3. 架构变化

v0.2.0 不引入新的 Maven 模块，也不调整依赖方向。变化集中在既有链路内部：

```text
domain/
  llm/
    service/          ← T1.026: LlmGenerationService waterfall 接入
  runtime/
    meter/
      model/valobj/   ← T1.027: TokenSnapshot, ReplayContext
      adapter/repository/ ← T1.027: ITokenSnapshotRepository
      service/        ← T1.028: ReplayAwareTokenMeter
  tool/
    mcp/              ← T1.031: ReconnectPolicy, ReconnectingMcpClient, McpReconnectExhaustedException
  plugin/
    runtime/
      service/        ← T1.033: ModuleLevelHmrService
      model/valobj/   ← T1.034: ResolvedPackage
      adapter/port/   ← T1.034: IRuntimeResolutionPort
    registry/service/ ← T1.034: PluginRegistryService refreshResolution
  sdk/
    model/valobj/     ← T1.035: SessionSummary, SessionDetail, ToolInfo, McpToolInfo, ResourceInfo
    service/          ← T1.036: SdkServerService 新方法实现

infrastructure/
  adapter/
    repository/       ← T1.029: JdbcTokenSnapshotRepository
    plugin/           ← T1.034: InProcessRuntimeResolution
    sdk/              ← T1.036: StdioSdkTransportPort requestHandler 路由

app/
  resources/
    schema.sql        ← T1.029/T1.034: token_snapshot 表 + module_id 列
    db/migration/     ← T1.029: V20261010__token_snapshot.sql
  SchemaMigrationRunner ← T1.029/T1.034: ensureTokenSnapshotTable + module_id 迁移
```

| 层 | 新增或调整 |
| --- | --- |
| Domain | 新增 18 个类（runtime/meter 3、tool/mcp 3、plugin/runtime 3、sdk 5、其他 4）；增强 9 个类（LlmGenerationService、McpBootstrap、McpToolAdapter、PluginRegistryService、SdkServerService、ISdkServerService、ISdkClientService 等） |
| Infrastructure | 新增 2 个类（JdbcTokenSnapshotRepository、InProcessRuntimeResolution）；增强 2 个类（StdioSdkTransportPort、SchemaMigrationRunner） |
| App | 新增 1 个迁移文件；修改 schema.sql + SchemaMigrationRunner |
| 包路径 | 全模块 `net.linlan.dsh` → `net.linlan.dsh` |

## 4. LLM 生成服务接入 waterfall（T1.026）

### 4.1 设计目标

将 `LlmGenerationService` 从直接订阅 `ILlmRuntimePort.stream` 改为经 `LlmStreamWaterfall.execute` 包装，使 retry/replay/routing 拦截器对上层透明生效。

### 4.2 实现

| 类 | 变更 |
| --- | --- |
| **LlmGenerationService** | `generate` 方法改为调 `waterfall.execute(options)` 获取流；新增 `waterfall()` 访问器支持动态注册拦截器；新增 `(runtime, waterfall)` 双参构造；保留 `(runtime)` 单参向后兼容构造（内部创建默认 waterfall） |

### 4.3 测试

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| LlmGenerationServiceTest | 5 | waterfall 接入、拦截器透传、向后兼容 |

## 5. 重放感知 Token 快照（T1.027-T1.030）

### 5.1 设计目标

重放会话时直接返回已保存的 Token 快照（不重算），实时运行时增量合流并保存。避免重放场景下的重复 Token 计算。

### 5.2 核心抽象

| 类 | 说明 |
| --- | --- |
| **TokenSnapshot** | record 值对象（lastEventStepIndex/totalTokens/modelCalls），`initial()` 工厂 + `advanceTo(event)` 增量推进 |
| **ReplayContext** | record 值对象（isReplay/upToStepIndex），`live()` 工厂 + `replay(upToStepIndex)` 工厂 |
| **ITokenSnapshotRepository** | 端口接口：`load(sessionId)`/`save(sessionId, snapshot)` + `register()` 注册 |
| **ReplayAwareTokenMeter** | 实现 `ITokenMeterService`：`summarize(sessionId, replayContext)` 重放返回快照不重算，实时增量合流并保存；`summarize(sessionId)` 委托到 `summarize(sessionId, ReplayContext.live())` |

### 5.3 JDBC 适配器与表迁移

| 类/文件 | 说明 |
| --- | --- |
| **JdbcTokenSnapshotRepository** | raw JDBC + DataSource 实现 `ITokenSnapshotRepository`，含 `ensureTable()` 幂等建表 |
| **schema.sql** | 新增 `token_snapshot` 表（session_id PK + last_event_step_index + total_tokens + model_calls + updated_at） |
| **V20261010__token_snapshot.sql** | Flyway 风格迁移文件 |
| **SchemaMigrationRunner** | 新增 `ensureTokenSnapshotTable()` 幂等补列 |

### 5.4 测试

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| TokenSnapshotTest | 7 | initial/advanceTo/幂等 |
| ReplayAwareTokenMeterTest | 9 | 重放返回快照、实时增量合流、部分重放、与 v0.1.8 全量重算对照 |

## 6. MCP 自动重连（T1.031-T1.032）

### 6.1 设计目标

MCP Server 断连时自动按指数退避策略重连，超限时抛出异常供上层处理。工具命名强制 `mcp__{server}__{tool}` 格式，避免多 server 同名工具冲突。

### 6.2 重连策略

| 类 | 说明 |
| --- | --- |
| **ReconnectPolicy** | record（initialBackoff/maxBackoff/maxAttempts/backoffMultiplier），`default_()` 工厂 + `nextBackoff(current)` 指数退避计算 |
| **McpReconnectExhaustedException** | 重连耗尽异常 |
| **ReconnectingMcpClient** | 装饰器：包装 `IMcpClient`，断连检测 + 指数退避重连 + 超限抛异常；每次调用前检查连接状态 |
| **McpBootstrap** | 创建 client 时包裹 `ReconnectingMcpClient`，新增 `ReconnectPolicy` 构造参数 |

### 6.3 工具命名规范

`McpToolAdapter` 验证 `mcp__{server}__{tool}` 命名格式已就位，多 server 同名工具无冲突。

### 6.4 测试

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| ReconnectingMcpClientTest | 8 | 正常调用、断连重连、退避递增、超限抛异常 |
| McpToolAdapterTest | 5 | 命名格式验证、多 server 同名工具无冲突 |

## 7. 模块级 HMR（T1.033）

### 7.1 设计目标

支持模块级热替换（Hot Module Replacement），每个模块独立 `URLClassLoader`，重载时 close 旧加载器，debounce 可配置，重载失败保留旧版本。

### 7.2 实现

| 方法 | 说明 |
| --- | --- |
| `watchModule(moduleId, sourcePath)` | 注册模块监听 |
| `unwatchModule(moduleId)` | 取消监听 |
| `refreshManifest(moduleId)` | 刷新模块清单 |
| `refreshResolution(moduleId)` | 刷新模块解析 |

| 特性 | 说明 |
| --- | --- |
| 独立 ClassLoader | 每个 `URLClassLoader` per 模块，重载时 close 旧加载器 |
| debounce | 可配置（默认 300ms），避免频繁重载 |
| 回滚 | 重载失败保留旧版本 |
| 生命周期 | `@PreDestroy` stop，清理 watcher 和 executor |

### 7.3 测试

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| ModuleLevelHmrServiceTest | 9 | watch/unwatch/refresh/debounce/回滚/多模块 |

## 8. Runtime Resolution（T1.034）

### 8.1 设计目标

引入运行时解析端口，支持插件注册后动态刷新解析结果。

### 8.2 核心抽象

| 类 | 说明 |
| --- | --- |
| **ResolvedPackage** | record（moduleId/entryPoint/sourcePath/packageType/resolvedAt） |
| **IRuntimeResolutionPort** | 端口接口：`resolutionMap()`/`refresh(moduleId)`/`resolve(moduleId)`/`register(package)` |
| **InProcessRuntimeResolution** | 适配器，`ConcurrentHashMap` 维护解析结果 |

### 8.3 PluginRegistryService 集成

| 类 | 变更 |
| --- | --- |
| **PluginRegistryService** | 新增 `refreshResolution(moduleId)` 方法和可选 `IRuntimeResolutionPort` 依赖 |
| **schema.sql** | 新增 `module_id` 列 |
| **SchemaMigrationRunner** | 新增 `module_id` 列幂等迁移 |

### 8.4 测试

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| ResolvedPackageTest | 3 | record 值对象基本行为 |

## 9. 完整 SDK 协议扩展（T1.035-T1.036）

### 9.1 设计目标

扩展 `ISdkServerService`/`ISdkClientService` 接口，补齐会话查询、工具查询、MCP 工具查询、资源查询等 SDK 协议方法；`StdioSdkTransportPort` 支持 requestHandler 路由。

### 9.2 新增 DTO

| DTO | 字段 |
| --- | --- |
| **SessionSummary** | sessionId, agentId, status, messageCount, createdAt, updatedAt |
| **SessionDetail** | sessionId, agentId, status, messages, toolCalls, tokenUsage, createdAt, updatedAt |
| **ToolInfo** | name, description, parameters, category |
| **McpToolInfo** | serverName, toolName, fullName, description, parameters |
| **ResourceInfo** | uri, name, description, mimeType |

### 9.3 接口扩展

| 接口 | 新增方法 | 说明 |
| --- | --- | --- |
| **ISdkServerService** | 8 个 default 方法 | listSessions/loadSession/cancelSession/streamSession/listTools/cancelTool/listMcpTools/listResources |
| **ISdkClientService** | 7 个 default 方法 | 对应客户端调用 |

所有新方法均为 default，旧实现无需改动。

### 9.4 SdkServerService 实现

`SdkServerService` 覆写所有新增 default 方法，对接 `ISessionEventLogService`。新增可选 `ISessionEventLogService` 依赖（向后兼容构造函数保留）。

### 9.5 Stdio 路由

| 类 | 变更 |
| --- | --- |
| **StdioSdkTransportPort** | 新增 `registerRequestHandler(method, handler)` 机制；`handleLine` incoming request 分发到注册的 handler，返回 JSON-RPC response |

## 10. 包路径全局迁移

### 10.1 设计目标

将所有模块的 Java 包路径从 `net.linlan.dsh` 统一迁移到 `net.linlan.dsh`，与 pom.xml groupId 和开源协议保持一致。

### 10.2 影响范围

| 模块 | 变更 |
| --- | --- |
| dsh-java-api | 源码 + 测试 `net.linlan.dsh.api` → `net.linlan.dsh.api` |
| dsh-java-types | 源码 + 测试 `net.linlan.dsh.types` → `net.linlan.dsh.types` |
| dsh-java-domain | 源码 + 测试 `net.linlan.dsh.domain` → `net.linlan.dsh.domain` |
| dsh-java-case | 源码 + 测试 `net.linlan.dsh.case` → `net.linlan.dsh.case` |
| dsh-java-infrastructure | 源码 + 测试 `net.linlan.dsh.infrastructure` → `net.linlan.dsh.infrastructure` |
| dsh-java-trigger | 源码 + 测试 `net.linlan.dsh.trigger` → `net.linlan.dsh.trigger` |
| dsh-java-app | 源码 + 测试 `net.linlan.dsh.app` → `net.linlan.dsh.app` |
| plugins | SPI 服务文件路径迁移 |

### 10.3 兼容性

旧 `net.linlan.dsh` 包下的类全部删除，新 `net.linlan.dsh` 包下的类为唯一实现。外部依赖旧包路径的代码需更新 import。

## 11. 测试统计

| 测试类 | 测试数 | 说明 |
| --- | --- | --- |
| LlmGenerationServiceTest | 5 | LLM 生成服务 waterfall 接入 |
| TokenSnapshotTest | 7 | Token 快照值对象 |
| ReplayAwareTokenMeterTest | 9 | 重放感知 Token Meter |
| ReconnectingMcpClientTest | 8 | MCP 自动重连 |
| McpToolAdapterTest | 5 | MCP 工具命名规范 |
| ModuleLevelHmrServiceTest | 9 | 模块级 HMR |
| ResolvedPackageTest | 3 | ResolvedPackage 值对象 |
| **合计** | **46** | domain 46 |

全量测试：domain 219 + infrastructure 45 = **264 通过**，0 失败，0 回归。

## 12. 对齐进度

### 已完成（Phase 1 全部 36 个任务）

| 任务 | 域 | 说明 | 版本 |
| --- | --- | --- | --- |
| T1.001-T1.025 | A/B/C | Scope 路由 → LLM Stream Waterfall | v0.1.9 |
| T1.026 | C | LlmGenerationService 改造接入 waterfall | v0.2.0 |
| T1.027-T1.030 | C/D | 重放感知 Token 快照 + JDBC 仓储 + 测试 | v0.2.0 |
| T1.031-T1.032 | D | MCP 自动重连 + 工具命名规范 | v0.2.0 |
| T1.033 | N | 模块级 HMR | v0.2.0 |
| T1.034 | N | Runtime Resolution 端口 + 插件刷新 | v0.2.0 |
| T1.035-T1.036 | O | SDK 协议扩展 + Stdio 路由 | v0.2.0 |

### 待完成

| 任务 | 域 | 说明 |
| --- | --- | --- |
| Phase 2（35 任务） | — | v0.2.0-beta 核心能力增强 |
| Phase 3（38 任务） | — | v0.2.0 GA 生态扩展 |

## 13. 兼容性变化

| 变化 | 兼容性 |
| --- | --- |
| `LlmGenerationService` 新增 `(runtime, waterfall)` 构造 | 旧 `(runtime)` 构造保留，内部创建默认 waterfall |
| `McpBootstrap` 新增 `ReconnectPolicy` 构造参数 | 旧构造保留，使用默认 `ReconnectPolicy.default_()` |
| `PluginRegistryService` 新增 `IRuntimeResolutionPort` 可选依赖 | 可空字段，默认 null 时不触发刷新 |
| `SdkServerService` 新增 `ISessionEventLogService` 可选依赖 | 旧构造保留，新方法在无依赖时返回空结果 |
| `ISdkServerService`/`ISdkClientService` 新增 default 方法 | 旧实现无需改动 |
| `StdioSdkTransportPort` 新增 requestHandler 机制 | 无注册 handler 时走原有逻辑 |
| 包路径 `net.linlan.dsh` → `net.linlan.dsh` | **破坏性变更**，外部需更新 import |
| `schema.sql` 新增 `token_snapshot` 表 + `module_id` 列 | `SchemaMigrationRunner` 幂等补列 |

## 14. 升级注意

1. **包路径迁移为破坏性变更**：所有 `import net.linlan.dsh.*` 需改为 `import net.linlan.dsh.*`；
2. `LlmGenerationService` 推荐使用 `(runtime, waterfall)` 构造以启用拦截器链；
3. `McpBootstrap` 推荐传入自定义 `ReconnectPolicy` 以控制重连行为；
4. `SdkServerService` 推荐注入 `ISessionEventLogService` 以启用会话查询功能；
5. `StdioSdkTransportPort` 使用 `registerRequestHandler` 注册自定义请求处理器；
6. 数据库需执行 `V20261010__token_snapshot.sql` 迁移或依赖 `SchemaMigrationRunner` 自动补列。

## 15. 已知限制

| 事项 | 说明 |
| --- | --- |
| Token 快照仅 JDBC 实现 | `ITokenSnapshotRepository` 目前仅 `JdbcTokenSnapshotRepository`，无内存实现 |
| MCP 重连单机有效 | `ReconnectingMcpClient` 是 JVM 内装饰器，多实例部署时各自独立重连 |
| 模块级 HMR 单机有效 | `ModuleLevelHmrService` 依赖 JVM 内 `URLClassLoader`，多实例部署时不共享 |
| SDK 协议方法为初始版本 | 新增的 8 个方法功能完整度和稳定性待验证 |
| 包路径迁移不可回退 | 旧 `net.linlan.dsh` 包已删除，无法回退到 v0.1.9 |

## 16. 后续建议

1. 为 `ITokenSnapshotRepository` 提供内存实现，支持测试和开发环境；
2. 为 `ReconnectPolicy` 增加可配置的 `maxBackoff` 和 `maxAttempts`，通过配置文件注入；
3. 为 `ModuleLevelHmrService` 增加多实例同步机制（如基于 Redis 的模块版本广播）；
4. 为 SDK 协议新方法补齐端到端测试和文档；
5. 进入 Phase 2 核心能力增强（35 任务，目标 v0.2.0-beta）；
6. 进入 Phase 3 生态扩展（38 任务，目标 v0.2.0 GA）。

## 17. 结论

v0.2.0 完成了核心底座对齐 Phase 1 的全部 36 个任务：LLM 生成服务接入 waterfall 拦截链、重放感知 Token 快照避免重算、MCP 自动重连提升鲁棒性、模块级 HMR 支持开发期热替换、Runtime Resolution 端口支持插件动态刷新、完整 SDK 协议扩展补齐会话/工具/资源查询。同时完成包路径从 `net.linlan.dsh` 到 `net.linlan.dsh` 的全局迁移。全量 264 测试通过，0 回归。

下一阶段的重点是进入 Phase 2 核心能力增强（35 任务，目标 v0.2.0-beta），然后 Phase 3 生态扩展（38 任务，目标 v0.2.0 GA）。
