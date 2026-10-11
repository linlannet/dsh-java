# DeepSeek Harness Java v0.1.7 开发说明

> v0.1.7 的主线是会话运行时的写安全、读性能、可观测性与统一流式出口：Session 事件升级到 v3 并增加版本守卫，单会话写入引入租约，重建引入进程内投影缓存，新增 Token 计量域；对外新增统一 Gateway SSE 和插件库存查询；同时修正 Agent 工具结果事件的透传语义。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.7` |
| 状态 | 功能开发完成，发布号与镜像链路待发布时统一处理 |
| 基线 | `origin/main`，即 v0.1.6.4 文档提交 |
| 主线 | 会话事件 v3、写租约、投影缓存、Token 计量、Gateway SSE、插件库存 |
| 架构 | 继续保持 DDD 六边形架构，不改变依赖方向 |
| 验证 | `mvn clean test`：107 个测试，0 失败，0 错误，0 跳过 |
| 用例库 | 新增 `docs/md/test-cases.md`，当前登记 116 条用例，其中 104 条已自动化 |

## 2. 版本亮点

1. **Session v3**：JSONL 会话头统一到 `SESSION_FORMAT_VERSION = 3`，旧版本读取后自动迁移落盘，未来版本会被明确拒绝。
2. **写租约**：`createSession`、`append`、`appendBatch` 都经过单会话写租约，避免同一会话被并发写入。
3. **投影缓存**：会话重建结果按 `sessionId + projectionType + lastSeq` 缓存，事件序号不变时不再重复扫描事件流。
4. **Token 计量**：从 `AssistantMessage` 事件聚合输入、输出、缓存与推理 token，形成会话级用量视图。
5. **统一 Gateway SSE**：Agent 与 Workflow 统一走 `/api/gateway/stream`，对外输出一致的 `GatewayStreamEventDTO` 信封。
6. **插件库存**：新增插件清单查询接口，聚合安装态、入口、来源路径和作者、描述、scope 元数据。
7. **工具结果透传**：Agent 工具事件在 `phase=result` 时保留真实 `status`，并透传 `result` 与 `durationMs`。
8. **测试用例库**：建立按功能域编号的用例库和后续补测流程，作为新功能的验收入口。

## 3. 架构变化

v0.1.7 不引入新的模块，也不调整 API、Trigger、Case、Domain、Infrastructure 的依赖方向。变化集中在既有链路内部：

```text
HTTP / Web Console
    ↓
Trigger：GatewayStreamApi / HarnessPluginQueryApi / AgentApi
    ↓
Case：Agent Pipeline / Workflow Pipeline / PluginInventoryQueryFactory
    ↓
Domain：Session Event Log / Projection Cache / Token Meter / Plugin Inventory
    ↓
Infrastructure：JSONL Session Store / Plugin Manifest / JAR Manifest
```

关键分层如下：

| 层 | 新增或调整 |
| --- | --- |
| API | 新增 Gateway Stream 契约、插件库存 DTO；扩展工具结果 DTO |
| Trigger | 新增 `/api/gateway/stream`；修正 Agent 工具事件映射；暴露插件库存 |
| Case | 新增插件库存查询用例与 DTO 组装 |
| Domain | 新增写租约、投影缓存、Token 计量、插件库存服务 |
| Infrastructure | 新增插件清单/JAR Manifest 元数据端口；扩展 JSONL 会话存储兼容逻辑 |

## 4. 会话事件与状态重建

### 4.1 Session v3 格式

`SessionHeader.SESSION_FORMAT_VERSION` 从旧值升级为 `3`。当前 JSONL 会话存储的行为如下：

| 场景 | 行为 |
| --- | --- |
| 读取旧版本头 | 解析旧字段，重建成当前 v3 头 |
| 旧版本头读取成功后 | 自动调用 `saveHeader` 落盘为 v3 |
| 读取未来版本头 | 抛出 `session uses unsupported format v<x>`，不做降级读取 |
| `seedLength` 缺失 | 归一化为 `0` |
| `seedLength` 为负数 | 读取时归一化为 `0`；构造时继续拒绝负数 |
| 保存会话头 | 统一输出当前版本号，避免继续写入旧格式 |

该设计有两个目的：一是允许旧会话在升级后继续恢复；二是防止更高版本数据被当前旧代码误解析。

### 4.2 单会话写租约

`SessionWriteLeaseService` 按 `sessionId` 维护租约：

1. 写入前调用 `acquire`，生成随机 `leaseId`、`owner` 和过期时间；
2. `SessionEventLogService` 在 `createSession`、`append`、`appendBatch` 外层获取租约；
3. 动作完成后在 `finally` 中释放租约；
4. 第二个持有者未过期时，新租约获取会抛出 `SessionWriteLeaseConflictException`；
5. 租约过期后，新写者可以获取新租约，原写者不能再继续写入；
6. `renew` 使用 `ConcurrentMap.replace` 做条件更新，只有当前持有者可以续期。

`SessionEventLogService` 当前使用 30 秒 TTL。租约是 JVM 内的并发控制，不是分布式锁；多实例部署时仍需要共享锁或外部协调器。

### 4.3 会话投影缓存

`SessionRebuilderService` 重建前先读取 `lastSeq`：

1. `lastSeq` 相同：直接返回 `ISessionProjectionCache` 中的缓存投影；
2. `lastSeq` 变化：重新读取事件并重建；
3. 重建完成后把结果写回缓存；
4. 缓存键是 `sessionId + projectionType`，缓存值内部再记录 `lastSeq`。

`InMemorySessionProjectionCache` 是进程内加速层，不是持久化投影。重启后仍从事件日志冷启动；同一会话事件追加后，旧的 `lastSeq` 会让缓存自然失效。

### 4.4 Token 计量

`TokenUsage` 新增 `plus()` 与 `totalTokens()`。`SessionTokenMeterService` 扫描会话事件：

| 数据项 | 聚合规则 |
| --- | --- |
| `modelCalls` | 统计带 usage 的 `AssistantMessage` 事件数量 |
| `inputTokens` / `outputTokens` | 所有 usage 累加 |
| `cacheReadTokens` | 可选字段，空值不参与展示；有值时按 0 补齐后累加 |
| `cacheWriteTokens` | 同上 |
| `reasoningTokens` | 同上 |

当前该能力是 Domain 服务，未新增 HTTP 端点；后续控制台或运维接口需要展示用量时可直接接入。

## 5. 统一 Gateway SSE

### 5.1 请求契约

| 接口 | 方法 | 内容类型 |
| --- | --- | --- |
| `/api/gateway/stream` | `POST` | `text/event-stream` |

请求通过 `source` 选择执行通道：

```json
{
  "source": "agent",
  "agent": {
    "agentId": "agent-1",
    "message": "检查当前目录"
  }
}
```

```json
{
  "source": "workflow",
  "workflow": {
    "workflowId": "review-flow",
    "arguments": {}
  }
}
```

`source` 支持以下值：

| source | 必填载荷 | 语义 |
| --- | --- | --- |
| `agent` | `agent` | 复用 Agent 消息流 |
| `workflow` | `workflow` | 复用 Workflow 启动流 |

### 5.2 事件信封

所有事件都包装为 `GatewayStreamEventDTO`：

| 字段 | 说明 |
| --- | --- |
| `source` | `agent` 或 `workflow` |
| `event` | 业务事件名，例如 `message.delta`、`tool.result`、`done` |
| `occurredAt` | 事件发生时间 |
| `payload` | 业务事件负载；空负载会归一化为空对象 |

Agent 通道事件：

| 事件 | 说明 |
| --- | --- |
| `message.delta` | 模型输出增量 |
| `message.reasoning` | 模型推理增量 |
| `tool.call` | 工具开始调用 |
| `tool.result` | 工具返回结果 |
| `finish` | 模型流结束 |
| `done` | Agent 调用完成，携带最终响应 |
| `error` | 调用异常 |

Workflow 通道事件：

| 事件 | 说明 |
| --- | --- |
| 上游事件 | 直接保留 Workflow 事件名，并放入统一信封 |
| `done` | Workflow 结束 |
| `error` | Workflow 失败或启动异常 |

### 5.3 连接生命周期

- SSE 超时时间为 300 秒；
- Trigger 层使用固定大小线程池执行流式任务，线程数为 CPU 核数；
- `completed` 与 `closed` 状态用于避免同一个 SSE 通道被重复完成；
- `onCompletion`、`onTimeout`、`onError` 都会更新关闭状态；
- 发送失败时停止继续发送，异常路径会触发 `completeWithError`。

该接口是同步服务内的流式适配，尚未包含重连、事件位点恢复、背压控制和多实例广播能力。

## 6. Agent 工具结果语义

`AgentToolCallEventResponseDTO` 继续携带：

| 字段 | 说明 |
| --- | --- |
| `callId` | 工具调用 ID |
| `toolName` | 工具名 |
| `arguments` | 工具入参 |
| `status` | 调用状态 |
| `phase` | `call` 或 `result` |
| `result` | 工具返回内容 |
| `durationMs` | 工具执行耗时 |

修复后的映射规则：

| phase | status | result | durationMs |
| --- | --- | --- | --- |
| `call` | 统一为 `running` | 不透传 | 不透传 |
| `result` | 保留上游真实状态 | 透传 | 透传 |

这样可以避免前端把已经完成的工具结果继续显示为 `running`。

## 7. 插件库存

### 7.1 查询接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/harness/plugins/inventory` | `GET` | 查询插件库存视图 |

响应仍是统一 `Response` 包装。核心业务字段包括：

| 字段 | 说明 |
| --- | --- |
| `generatedAt` | 库存生成时间 |
| `count` | 插件数量 |
| `plugins[].pluginId` | 插件 ID |
| `plugins[].displayName` | 展示名 |
| `plugins[].pluginVersion` | 插件版本 |
| `plugins[].runtimeType` | 运行时类型 |
| `plugins[].installMode` | 安装方式 |
| `plugins[].status` | 注册或运行状态 |
| `plugins[].sourcePath` | 来源路径 |
| `plugins[].entrypoint` | 入口描述 |
| `plugins[].manifestPath` | 元数据来源 |
| `plugins[].author` | 作者 |
| `plugins[].description` | 描述 |
| `plugins[].scope` | `project`、`artifact` 或 `unknown` |

### 7.2 元数据识别

| 来源 | 识别方式 | scope 兜底 |
| --- | --- | --- |
| 目录插件 | 优先读取 `.codex-plugin/plugin.json` | `project` |
| Node 风格目录插件 | Codex 清单不存在时读取 `package.json` | `project` |
| JAR 插件 | 读取 `META-INF/MANIFEST.MF` | `artifact` |
| JAR 无 manifest | 使用 JAR 路径兜底 | `artifact` |

清单解析按安全降级处理：

- `plugin.json` 支持 `author` 为字符串或对象；
- `author` 可以回退到 `author.name`；
- JAR manifest 优先读取 `Plugin-Author` / `Plugin-Description` / `Plugin-Scope`；
- JAR manifest 缺少专用字段时回退 `Implementation-Vendor` / `Implementation-Title`；
- 损坏 JSON 或损坏 JAR 不会阻断整个库存查询，只降低该插件的元数据丰富度。

## 8. 测试与验证

### 8.1 测试用例库

新增 `docs/md/test-cases.md`。用例按功能域编号，后续新功能必须先补充或更新用例，再进行功能验证。当前覆盖：

| 功能域 | 覆盖方向 |
| --- | --- |
| Session 事件存储 | v3 迁移、未来版本拒绝、usage 往返 |
| Session 投影与重建 | 缓存命中、`lastSeq` 失效、类型隔离 |
| Session 写租约 | 单写者、续期、冲突、过期抢占 |
| Token 计量 | usage 聚合 |
| Gateway 与 Agent 流 | 工具结果语义、SSE 契约 |
| 插件库存 | 排序映射、JSON/JAR 元数据、损坏清单降级 |
| 架构与契约 | API/Trigger/Case/Domain 依赖边界和 DTO 契约 |

### 8.2 自动化验证

| 验证项 | 结果 |
| --- | --- |
| 命令 | `mvn clean test` |
| 模块数 | 10 |
| 测试总数 | 107 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| `git diff --check` | 通过 |

代表性定向测试：

| 测试 | 覆盖点 |
| --- | --- |
| `JsonlSessionEventStoreTest#shouldMigrateLegacyHeaderToCurrentFormat` | 旧会话头迁移为 v3 |
| `JsonlSessionEventStoreTest#shouldRejectNewerSessionFormat` | 拒绝未来版本 |
| `SessionWriteLeaseServiceTest#shouldRejectSecondActiveWriter` | 第二写者冲突 |
| `SessionEventLogServiceTest#shouldRejectAppendWhenLeaseIsHeld` | 外部租约阻断追加 |
| `SessionRebuilderServiceTest#shouldReuseProjectionUntilLastSeqChanges` | 投影缓存命中与失效 |
| `SessionTokenMeterServiceTest#shouldAggregateModelUsage` | Token 聚合 |
| `AgentApiTest#shouldPreserveToolResultPhase` | 工具结果字段透传 |
| `PluginInventoryMetadataPortTest#shouldReadJarManifestMetadata` | JAR 元数据解析 |

### 8.3 待补验证

| 事项 | 当前状态 |
| --- | --- |
| JDBC SessionEventStore 等价迁移测试 | 待补 |
| Gateway Agent / Workflow 真实 HTTP 端到端 | 待补 |
| SSE 断连、超时和错误注入 | 待补 |
| 租约多线程竞争压测 | 待补 |
| 插件库存 HTTP 端到端 | 待补 |

## 9. 接口与配置速查

### 9.1 新增接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/gateway/stream` | `POST` | Agent / Workflow 统一 SSE |
| `/api/harness/plugins/inventory` | `GET` | 插件库存 |

### 9.2 数据契约变化

| 对象 | 变化 |
| --- | --- |
| `GatewayStreamRequestDTO` | 新增，包含 `source`、`agent`、`workflow` |
| `GatewayStreamEventDTO` | 新增，统一 SSE 事件信封 |
| `PluginInventoryResponseDTO` | 新增，插件库存响应 |
| `PluginInventoryItemDTO` | 新增，单个插件库存项 |
| `AgentToolCallEventResponseDTO` | 新增 `phase`、`result`、`durationMs` 字段 |
| `TokenUsage` | 新增 `totalTokens()` 与 `plus()` |

### 9.3 配置变化

本版本没有新增配置键。以下参数当前是代码常量：

| 参数 | 当前值 | 位置 |
| --- | --- | --- |
| Session 写租约 TTL | 30 秒 | `SessionEventLogService` |
| Gateway SSE 超时 | 300 秒 | `GatewayStreamApi` |
| Gateway 流线程池 | CPU 核数 | `GatewayStreamApi` |

## 10. 兼容性变化

| 变化 | 兼容性 |
| --- | --- |
| JSONL 旧会话头 | 读取后自动迁移为 v3 |
| JSONL 未来会话头 | 明确拒绝，不做降级解析 |
| 缺失或负数 `seedLength` | JSONL 读取时归一化为 `0` |
| Agent 工具结果 DTO | 新增字段，原有字段语义不变 |
| Gateway / 插件库存接口 | 新增接口，不影响既有端点 |
| 既有 Agent / Workflow API | 未调整调用方式 |

## 11. 升级注意

1. 升级前备份 JSONL 会话目录和数据库中的会话数据；
2. 首次读取旧 JSONL 会话时会发生头文件重写，请确认运行账号对该目录有写权限；
3. 如果数据中已出现超过 `3` 的会话版本，会被拒绝读取，需要先确认版本演进方向；
4. JDBC 会话存储没有在本版本实现与 JSONL 等价的 v3 自动迁移；
5. 写租约和投影缓存都是 JVM 内状态，水平扩容后不能依赖它们做全局互斥或跨实例缓存；
6. Gateway SSE 当前没有重连位点、鉴权和多实例广播；公网部署需在网络边界控制访问；
7. Maven POM 仍是 `0.1.6`，正式发布前需要统一升到 `0.1.7` 并同步镜像与部署脚本。

## 12. 已知限制

| 事项 | 说明 |
| --- | --- |
| 写租约单机有效 | 只适合单实例运行时，尚不是分布式锁 |
| 投影缓存无容量上限 | 当前没有 TTL、最大条数和淘汰策略 |
| JSONL 迁移优先 | JDBC 存储的会话版本迁移能力未补齐 |
| Token 计量未出 HTTP 接口 | 当前只提供 Domain 服务 |
| Gateway 不支持断点续传 | 客户端断开后不能从事件位点恢复 |
| SSE 线程池固定 | 当前固定为 CPU 核数，未按负载动态伸缩 |
| 插件清单安全降级 | 损坏元数据不会阻断查询，但也不会主动校验签名或完整性 |

## 13. 后续建议

1. 为 JDBC SessionEventStore 补齐 v3 迁移、追加和读取等价测试；
2. 增加 Gateway SSE 端到端、断连、超时、错误注入和多事件并发用例；
3. 为租约增加并发竞争测试，并预留分布式锁端口；
4. 为投影缓存增加容量、TTL 或显式淘汰策略；
5. 将 Token 用量接入控制台或运维查询接口；
6. 发布前完成版本号、Docker 镜像、部署脚本和 release tag 的统一升级。

## 14. 结论

v0.1.7 把会话链路从“能恢复”推进到“写安全、读更快、可计量”，也把 Agent 与 Workflow 的流式出口收敛到统一 Gateway。插件库存让运行环境可以更完整地描述插件来源和元数据，Agent 工具结果事件则补齐了前端判断执行状态所需的关键字段。

下一阶段的重点是补齐 JDBC 会话迁移、SSE 端到端和并发验证，并在进入正式发布前统一版本与镜像链路。
