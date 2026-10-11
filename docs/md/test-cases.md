# DeepSeek Harness Java 测试用例库

> 本文档是功能验收与回归验证的持续维护入口。新功能进入开发完成态前，必须先补充或更新对应测试用例；修复缺陷时，必须新增或调整可复现该缺陷的用例。

## 1. 使用方式

### 1.1 基本约定

| 项 | 约定 |
| --- | --- |
| 用例编号 | `TC-域-序号`，新增用例必须在对应域末尾递增，不复用废弃编号 |
| 优先级 | `P0` 阻断核心链路；`P1` 影响主要功能；`P2` 边界或体验 |
| 状态 | `通过`、`失败`、`阻塞`、`待自动化`、`待执行` |
| 变更规则 | 新功能、接口契约、持久化格式、并发规则、安全边界变化时同步更新用例 |
| 验证要求 | 每个自动化用例必须可在 Maven 构建中重复执行；手工用例必须写清操作与可判定预期 |

### 1.2 执行命令

```bash
# 全量回归
mvn test

# 当前变更涉及的模块定向回归
mvn -pl dsh-java-domain,dsh-java-infrastructure,dsh-java-trigger -am test

# 完整构建（含打包检查）
mvn clean verify
```

## 2. 用例总览

| 功能域 | 用例数 | 当前状态 |
| --- | ---: | --- |
| Session 事件存储 | 4 | 自动化 |
| Session 投影与重建 | 5 | 自动化；缓存容量待补 |
| Session 写租约 | 5 | 自动化；并发压测待补 |
| Token 计量 | 1 | 自动化；边界待补 |
| Gateway 与 Agent 流 | 6 | 2 自动化 + 4 待自动化 |
| 插件库存 | 7 | 自动化；HTTP 端到端待补 |
| 应用启动与预设插件 | 5 | 自动化 |
| Agent 语义 | 9 | 自动化 |
| Case 编排 | 3 | 自动化 |
| 模型渠道 | 11 | 自动化 |
| 任务审批与工作流 | 8 | 自动化 |
| MCP 与工具配置 | 8 | 自动化 |
| Skills、配置与安全 | 8 | 自动化 |
| LLM 重试 | 2 | 自动化 |
| Java 插件运行时 | 10 | 自动化 |
| 架构与契约 | 16 | 自动化 |
| 安全边界（建议新增） | 1 | 待执行 |
| **合计** | **116** | **104 自动化 + 12 待自动化/待执行** |

## 3. Session 事件存储

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-SESS-001 | P0 | 旧版本会话头迁移 | 存在 `SESSION_FORMAT_VERSION < 3` 的 JSONL 文件 | 加载该会话事件存储 | 能读取会话，并将文件迁移为当前 v3 格式 | `JsonlSessionEventStoreTest#shouldMigrateLegacyHeaderToCurrentFormat` | 通过 |
| TC-SESS-002 | P0 | 拒绝更高格式版本 | JSONL 头版本大于当前版本 | 加载会话事件存储 | 抛出明确异常，不降级改写未知格式 | `JsonlSessionEventStoreTest#shouldRejectNewerSessionFormat` | 通过 |
| TC-SESS-003 | P1 | 旧头写入归一化 | 传入缺少 v3 字段的旧会话头 | 执行保存 | 落盘头使用当前版本，避免继续产生旧格式 | `JsonlSessionEventStoreTest#shouldSaveLegacyHeaderUsingCurrentFormat` | 通过 |
| TC-SESS-004 | P0 | 事件与 Token usage 往返 | 构造带 `AssistantMessage` usage 的事件流 | 追加后重新加载 | 事件序列、类型、负载和 usage 均一致 | `JsonlSessionEventStoreTest#shouldRoundTripEventsAndUsage` | 通过 |

## 4. Session 投影与重建

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-PROJ-001 | P0 | 相同序列命中缓存 | 已按 `lastSeq` 重建投影 | 使用相同会话与 `lastSeq` 再次重建 | 返回缓存投影，不重复遍历事件 | `SessionRebuilderServiceTest#shouldReuseProjectionUntilLastSeqChanges` | 通过 |
| TC-PROJ-002 | P0 | 序列推进后刷新缓存 | 缓存中存在旧 `lastSeq` 投影 | 追加事件后重建 | 丢弃旧缓存并生成最新投影 | `SessionRebuilderServiceTest#shouldReuseProjectionUntilLastSeqChanges` | 通过 |
| TC-PROJ-003 | P0 | 匹配序列返回投影 | 缓存保存指定类型投影 | 用相同会话、类型、`lastSeq` 查询 | 返回原投影对象 | `InMemorySessionProjectionCacheTest#shouldReturnProjectionWhenLastSeqMatches` | 通过 |
| TC-PROJ-004 | P0 | 序列变化判定过期 | 缓存中 `lastSeq=1` | 用 `lastSeq=2` 查询 | 不返回旧投影 | `InMemorySessionProjectionCacheTest#shouldTreatProjectionAsStaleWhenLastSeqChanges` | 通过 |
| TC-PROJ-005 | P1 | 类型隔离与批量清理 | 同一会话存在多类型投影 | 查询另一类型；再按会话清理 | 类型互不影响；清理后全部失效 | `InMemorySessionProjectionCacheTest#shouldIsolateProjectionsByType` / `#shouldEvictAllProjectionTypesForSession` | 通过 |

## 5. Session 写租约

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-LEASE-001 | P0 | 单写者获取与续期 | 会话无活跃租约 | 第一次获取后由同一持有者续期 | 两次均成功，租约过期时间更新 | `SessionWriteLeaseServiceTest#shouldAllowSingleWriterAndRenewal` | 通过 |
| TC-LEASE-002 | P0 | 拒绝第二个写者 | 租约未过期且持有者不同 | 第二方获取租约 | 抛出 `SessionWriteLeaseConflictException` | `SessionWriteLeaseServiceTest#shouldRejectSecondActiveWriter` | 通过 |
| TC-LEASE-003 | P1 | 过期租约可被抢占 | 原租约超过 TTL | 新持有者获取租约 | 新持有者成功；原持有者不可再写 | `SessionWriteLeaseServiceTest#shouldExpireLeaseAndAllowNewWriter` | 通过 |
| TC-LEASE-004 | P0 | 租约内追加并释放 | 当前调用方持有租约 | 追加事件后释放 | 追加成功；释放后可进入下一次写流程 | `SessionEventLogServiceTest#shouldAppendAndReleaseLease` | 通过 |
| TC-LEASE-005 | P0 | 外部租约阻断追加 | 事件日志已被外部租约持有 | 不持租约直接追加 | 追加被拒绝，且不产生半写事件 | `SessionEventLogServiceTest#shouldRejectAppendWhenLeaseIsHeld` | 通过 |

## 6. Token 计量

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-METER-001 | P1 | 聚合多条模型消息 | 会话存在多条带 usage 的模型消息 | 按 session 读取计量结果 | 输入、输出与总 token 均累加正确 | `SessionTokenMeterServiceTest#shouldAggregateModelUsage` | 通过 |

## 7. Gateway 与 Agent 流

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-GW-001 | P0 | 工具结果阶段保留 | Agent 返回工具事件且 `phase=result` | 转换为对外事件 | 保留原始 `status`，并透传 `phase`、`result`、`durationMs` | `AgentApiTest#shouldPreserveToolResultPhase` | 通过 |
| TC-GW-002 | P1 | SSE 契约稳定 | 存在流式 API 契约 | 架构扫描 | 流式契约返回 `SseEmitter` | `ApiArchitectureTest#streamContractsMustReturnSseEmitter` | 通过 |
| TC-GW-003 | P1 | Agent SSE 端到端 | 应用启动且可访问 Agent 用例 | 请求 `/api/gateway/stream?source=agent` | 收到统一事件信封，最终包含完成或错误事件 | 端到端用例待补 | 待自动化 |
| TC-GW-004 | P1 | Workflow SSE 端到端 | 应用启动且工作流可执行 | 请求 `/api/gateway/stream?source=workflow` | 保留 workflow 语义并按统一信封输出 | 端到端用例待补 | 待自动化 |
| TC-GW-005 | P2 | 客户端断连清理 | 建立 SSE 后客户端断开 | 触发连接完成或超时 | `StreamContext` 只完成一次，不向关闭连接继续写 | 异步生命周期用例待补 | 待自动化 |
| TC-GW-006 | P2 | 上游错误统一降级 | 模拟 Agent/workflow 执行异常 | 建立 SSE | 输出统一错误事件，HTTP 通道不悬挂 | 错误注入用例待补 | 待自动化 |

## 8. 插件库存

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-PLUG-001 | P1 | 库存排序与映射 | 多个插件包含名称、描述、scope、状态 | 执行库存查询 | 输出 DTO 字段正确且按展示规则排序 | `PluginInventoryServiceTest#shouldSortAndMapInventory` | 通过 |
| TC-PLUG-002 | P1 | Codex manifest 优先 | 插件目录存在 `.codex-plugin/plugin.json` | 读取元数据 | 使用 Codex 清单的作者、描述、scope | `PluginInventoryMetadataPortTest#shouldReadManifestMetadata` | 通过 |
| TC-PLUG-003 | P2 | artifact 文件兜底 | 无 Codex manifest 但存在 artifact 文件 | 读取元数据 | 使用 artifact 文件名和可用默认值 | `PluginInventoryMetadataPortTest#shouldReadArtifactFileMetadata` | 通过 |
| TC-PLUG-004 | P1 | JAR manifest 解析 | JAR 内含 `Plugin-*` manifest 属性 | 读取 JAR 元数据 | 正确解析作者、描述、scope | `PluginInventoryMetadataPortTest#shouldReadJarManifestMetadata` | 通过 |
| TC-PLUG-005 | P2 | 缺少 manifest 降级 | JAR 无相关 manifest | 读取 JAR 元数据 | 返回空元数据，不抛异常 | `PluginInventoryMetadataPortTest#shouldFallbackWhenJarManifestIsMissing` | 通过 |
| TC-PLUG-006 | P1 | 损坏 JSON 安全降级 | Codex manifest 是非法 JSON | 读取元数据 | 不中断库存查询，元数据降级 | `PluginInventoryMetadataPortTest#shouldNotFailWhenManifestIsCorrupt` | 通过 |
| TC-PLUG-007 | P2 | 空白 scope 归一化 | manifest 的 scope 只有空白字符 | 读取元数据 | scope 归一化为 `project` | `PluginInventoryMetadataPortTest#shouldNormalizeBlankScopeToProject` | 通过 |

## 9. 应用启动与预设插件

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-APP-001 | P0 | 应用启动冒烟 | 默认 standalone 配置可用 | 启动 Spring Boot 应用 | 上下文启动成功，无阻断性 Bean 缺失 | `ApplicationIntegrationSmokeTest#startApplication` | 通过 |
| TC-APP-002 | P0 | 核心端点可访问 | 应用已启动 | 请求控制台与核心查询端点 | 返回 HTTP 200 且包含预期内容 | `ApplicationIntegrationSmokeTest#exposesCoreConsoleAndQueryEndpoints` | 通过 |
| TC-APP-003 | P0 | 恢复已激活插件 | 数据库存在 active 预设插件记录 | 应用重启 | 插件恢复注册并保持可用 | `PresetPluginLoaderTest#restoresPersistedActivePluginAfterHostRestart` | 通过 |
| TC-APP-004 | P1 | 幂等恢复插件 | 同一 active 预设插件已恢复 | 再次执行恢复流程 | 不重复注册 | `PresetPluginLoaderTest#doesNotRestoreActivePresetTwice` | 通过 |
| TC-APP-005 | P1 | 失败插件状态校正 | 注册表显示失败但插件实际运行中 | 执行启动协调 | 注册状态校正为运行态 | `PresetPluginLoaderTest#reconcilesFailedRegistryStatusFromRunningPlugin` | 通过 |

## 10. Agent 语义与编排

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-AGENT-001 | P1 | 绝对路径目录问题 | 用户消息询问绝对路径目录 | 执行意图识别 | 判定为任务执行 | `AgentIntentNodeTest#classifiesDirectoryQuestionWithAbsolutePathAsTaskExecution` | 通过 |
| TC-AGENT-002 | P1 | 当前目录问题 | 用户消息询问当前目录 | 执行意图识别 | 判定为任务执行 | `AgentIntentNodeTest#classifiesCurrentDirectoryQuestionAsTaskExecution` | 通过 |
| TC-AGENT-003 | P1 | 问候语归类 | 用户发送普通问候 | 执行意图识别 | 判定为聊天 | `AgentIntentNodeTest#classifiesGreetingAsChat` | 通过 |
| TC-AGENT-004 | P1 | 显式任务动词保留 | 消息包含明确执行动词 | 执行意图识别 | 不被普通问题规则覆盖 | `AgentIntentNodeTest#preservesExplicitTaskVerbClassification` | 通过 |
| TC-AGENT-005 | P2 | 概念问题不转澄清 | 用户询问概念 | 执行意图识别 | 判定为聊天而不是澄清 | `AgentIntentNodeTest#classifiesConceptualQuestionAsChatInsteadOfClarification` | 通过 |
| TC-AGENT-006 | P1 | 目录任务构造检索指令 | 任务指向目录问题 | 执行派发节点 | 生成文件系统搜索指令 | `AgentDispatchNodeTest#buildsFsSearchInstructionForDirectoryQuestion` | 通过 |
| TC-AGENT-007 | P1 | 通用任务兜底指令 | 其他任务进入执行 | 执行派发节点 | 使用通用工具执行指令 | `AgentDispatchNodeTest#fallsBackToGeneralToolInstructionForOtherTaskExecution` | 通过 |
| TC-AGENT-008 | P1 | 插件提及追加提示 | 消息包含插件提及标记 | 执行派发节点 | 追加插件使用说明 | `AgentDispatchNodeTest#appendsPluginMentionInstruction` | 通过 |
| TC-AGENT-009 | P2 | 邮箱不误判插件提及 | 消息包含邮箱地址 | 执行派发节点 | 不触发插件提及逻辑 | `AgentDispatchNodeTest#ignoresEmailLikeTextAsPluginMention` | 通过 |
| TC-ORCH-001 | P0 | 新上下文执行根节点 | 构造 pipeline 与根节点 | 执行根节点 | 使用全新上下文并返回结果 | `CasePipelineTest#executesRootWithFreshContext` | 通过 |
| TC-ORCH-002 | P1 | 运行时异常不二次包装 | 根节点抛出运行时异常 | 执行 pipeline | 原异常类型与消息保留 | `CasePipelineTest#rethrowsRuntimeWithoutWrapping` | 通过 |
| TC-ORCH-003 | P1 | 受检异常补充操作名 | 根节点抛出受检异常 | 执行 pipeline | 包装后的异常包含操作名 | `CasePipelineTest#wrapsCheckedFailureWithOperationName` | 通过 |

## 11. 模型渠道

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-MODEL-001 | P0 | 第一条启用渠道生效 | 当前无渠道 | 保存第一条启用渠道 | 自动成为 active 渠道 | `ModelSettingServiceTest#firstEnabledChannelBecomesActive` | 通过 |
| TC-MODEL-002 | P0 | 新渠道不抢占 active | 已有 active 渠道 | 追加另一个启用渠道 | 原 active 保持不变 | `ModelSettingServiceTest#addingAnotherChannelDoesNotReplaceActiveChannel` | 通过 |
| TC-MODEL-003 | P0 | 显式激活迁移标记 | 存在多个启用渠道 | 激活指定渠道 | 全局唯一 active 指向该渠道 | `ModelSettingServiceTest#activatingChannelMovesGlobalActiveMarker` | 通过 |
| TC-MODEL-004 | P0 | 停用渠道不能激活 | 渠道 `enabled=false` | 执行激活 | 操作被拒绝 | `ModelSettingServiceTest#disabledChannelCannotBeActivated` | 通过 |
| TC-MODEL-005 | P0 | 停用 active 后自动切换 | active 渠道被停用且还有启用渠道 | 执行停用 | 自动切换到另一个启用渠道 | `ModelSettingServiceTest#disablingActiveChannelSelectsAnotherEnabledChannel` | 通过 |
| TC-MODEL-006 | P0 | 删除 active 后自动切换 | active 渠道被删除且还有启用渠道 | 执行删除 | 自动切换到另一个启用渠道 | `ModelSettingServiceTest#deletingActiveChannelSelectsAnotherEnabledChannel` | 通过 |
| TC-MODEL-007 | P1 | 唯一渠道重新启用恢复 | 唯一渠道处于停用 | 重新启用 | 恢复为 active 渠道 | `ModelSettingServiceTest#reEnablingOnlyChannelMakesItActiveAgain` | 通过 |
| TC-MODEL-008 | P1 | 无请求渠道使用 active | 数据库存在 active 渠道 | 请求不携带 `channelCode` | 解析为当前 active 渠道 | `AgentModelSettingResolverTest#usesActiveChannelWhenRequestHasNoChannel` | 通过 |
| TC-MODEL-009 | P0 | 请求渠道优先 | 请求指定启用渠道 | 解析模型设置 | 使用请求渠道而非全局 active | `AgentModelSettingResolverTest#usesRequestedChannelWhenProvided` | 通过 |
| TC-MODEL-010 | P1 | 空数据库回退配置 | 数据库无渠道 | 解析模型设置 | 使用本地兜底模型配置 | `AgentModelSettingResolverTest#fallsBackToFileModelWhenDatabaseIsEmpty` | 通过 |
| TC-MODEL-011 | P1 | 无效请求渠道回退 | 请求渠道不存在 | 解析模型设置 | 回退到兜底配置 | `AgentModelSettingResolverTest#requestedChannelFallsBackWhenMissing` | 通过 |

## 12. 任务审批与工作流

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-TASK-001 | P0 | 审批通过并排队执行 | 会话处于待审批状态 | 执行 approve | 会话迁移为 queued 并触发执行 | `ApprovalCommandServiceTest#approveMovesPendingSessionToQueuedAndExecutesIt` | 通过 |
| TC-TASK-002 | P0 | 拒绝非待审批会话 | 会话不处于待审批状态 | 执行 approve | 返回明确失败，不执行任务 | `ApprovalCommandServiceTest#approveRejectsSessionWhichIsNotPendingApproval` | 通过 |
| TC-TASK-003 | P0 | 会话缺失时拒绝 | 会话 ID 不存在 | 执行 approve | 返回明确失败 | `ApprovalCommandServiceTest#approveRejectsMissingSession` | 通过 |
| TC-TASK-004 | P1 | 按注入顺序执行过滤器 | 多个提交过滤器按顺序配置 | 提交任务 | 依序执行并可在首个失败处停止 | `TaskSubmissionPolicyServiceTest#appliesFiltersInInjectedOrder` / `#stopsValidationWhenFilterFails` | 通过 |
| TC-TASK-005 | P0 | 需审批工具进入待审批 | 启用工具中存在受限工具 | 构造权限策略 | 工具进入 approval required 集合 | `PermissionPolicyServiceTest#splitsEnabledToolsIntoApprovalRequiredAndAutoApproved` | 通过 |
| TC-TASK-006 | P1 | 无受限工具自动通过 | 启用工具均不受限 | 构造权限策略 | 不产生待审批项 | `PermissionPolicyServiceTest#doesNotRequireApprovalWhenNoEnabledToolIsGated` | 通过 |
| TC-FLOW-001 | P1 | adhoc 工作流参数 | 提交 adhoc 工作流 | 执行启动策略 | 使用 adhoc 名称并复制参数 | `WorkflowStartPolicyTest#appliesAdhocNameAndCopiesArguments` | 通过 |
| TC-FLOW-002 | P1 | 工作流结果映射 | 工作流分别返回错误与完成 | 执行启动策略 | 错误映射为 error 事件，完成映射为 done 事件 | `WorkflowStartPolicyTest#mapsErrorsToErrorEvents` / `#mapsCompletedResultsToDoneEvents` | 通过 |

## 13. MCP 与工具配置

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-MCP-001 | P1 | 缺省 transport 为 stdio | 配置未填 transport | 校验 MCP 配置 | 默认使用 `stdio` | `McpServerConfigTest#defaultsMissingTransportToStdio` | 通过 |
| TC-MCP-002 | P1 | HTTP transport 别名归一化 | 配置使用可识别的 HTTP 别名 | 校验配置 | 归一化为标准 streamable HTTP 类型 | `McpServerConfigTest#normalizesStreamableHttpAliases` | 通过 |
| TC-MCP-003 | P1 | 拒绝未知 transport | 配置使用不支持的 transport | 校验配置 | 校验失败并提示原因 | `McpServerConfigTest#rejectsUnsupportedTransport` | 通过 |
| TC-MCP-004 | P1 | stdio 必须有 command | transport 为 `stdio` 且缺 command | 校验配置 | 校验失败 | `McpServerConfigTest#requiresCommandForStdio` | 通过 |
| TC-MCP-005 | P1 | HTTP 必须有 URL | HTTP/SSE transport 缺 URL | 校验配置 | 校验失败 | `McpServerConfigTest#requiresUrlForHttpTransports` | 通过 |
| TC-MCP-006 | P1 | stdio 客户端列出工具 | 本地 MCP 测试服务可用 | 连接并列出工具 | 建立连接并返回工具清单 | `StdioMcpClientTest#connectsAndListsToolsOverChildProcessStdio` | 通过 |
| TC-MCP-007 | P1 | streamable HTTP 列出工具 | HTTP MCP 测试服务可用 | 连接并列出工具 | 建立连接并返回工具清单 | `HttpMcpClientTest#connectsAndListsToolsOverStreamableHttp` | 通过 |
| TC-MCP-008 | P2 | 兼容旧 SSE 连接 | 旧 SSE MCP 测试服务可用 | 分别用原生和兼容 POST 方式连接 | 两种模式都能建立连接并列出工具 | `HttpMcpClientTest#connectsAndListsToolsOverLegacySse` / `#connectsAndListsToolsWhenLegacySsePostIsAccepted` | 通过 |

## 14. Skills、配置与安全

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-SKILL-001 | P1 | 多根目录技能发现 | 配置多个 skill 根目录 | 扫描技能 | 返回技能清单；重复名称按低 rank 优先 | `FilesystemSkillProviderPortTest#discoversConfiguredRootsAndUsesLowerRankForDuplicateNames` | 通过 |
| TC-SKILL-002 | P1 | 禁用 provider 清空目录 | provider 已禁用 | 扫描技能 | 返回空目录 | `FilesystemSkillProviderPortTest#disabledProviderReturnsEmptyCatalog` | 通过 |
| TC-SKILL-003 | P2 | 禁用项目根跳过技能 | project root 被禁用 | 扫描技能 | 不读取 workspace skills | `FilesystemSkillProviderPortTest#disabledProjectRootSkipsWorkspaceSkills` | 通过 |
| TC-SKILL-004 | P1 | 目录包技能发现 | skill 位于目录 bundle 中 | 扫描技能 | 正确发现 bundle 技能 | `FilesystemSkillProviderPortTest#discoversDirectoryBundleSkills` | 通过 |
| TC-CFG-001 | P1 | 空配置可接受 | MCP servers 为空且 sandbox 合法 | 执行配置校验 | 校验通过 | `HarnessConfigValidatorTest#acceptsEmptyMcpServersAndValidSandboxMode` | 通过 |
| TC-CFG-002 | P1 | 拒绝重复 MCP 名称 | 两个 MCP server 同名 | 执行配置校验 | 校验失败 | `HarnessConfigValidatorTest#rejectsDuplicateMcpServerNames` | 通过 |
| TC-CFG-003 | P1 | 拒绝缺少 URL 的 HTTP | streamable HTTP 缺 URL | 执行配置校验 | 校验失败 | `HarnessConfigValidatorTest#rejectsStreamableHttpWithoutUrl` | 通过 |
| TC-CFG-004 | P0 | 敏感配置脱敏 | effective config 包含敏感键 | 输出配置 | 敏感键遮蔽，其他键保留原值 | `HarnessEffectiveConfigPortTest#masksSensitiveKeysAndPreservesOtherValues` | 通过 |

## 15. LLM 重试

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-LLM-001 | P1 | 可重试失败自动重试 | 模拟第一次可重试失败、第二次成功 | 执行模型调用 | 调用成功并暴露重试次数 | `InMemoryLlmRuntimePortTest#retriesRetryableFailureAndExposesRetryNumber` | 通过 |
| TC-LLM-002 | P1 | 重试预算耗尽上报 | 每次调用都返回可重试失败 | 执行模型调用 | 最终失败并报告重试次数 | `InMemoryLlmRuntimePortTest#reportsRetryCountWhenBudgetIsExhausted` | 通过 |

## 16. Java 插件运行时

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-PLUGIN-001 | P0 | 旧工具注册限定名 | 插件声明兼容旧 API 的工具 | 注册插件上下文 | 工具使用带插件限定的名称 | `SpringPluginContextTest#registersLegacyToolsWithQualifiedNames` | 通过 |
| TC-PLUGIN-002 | P0 | 关闭注册按逆序执行 | 注册多个资源 | 关闭插件上下文 | 按注册相反顺序释放 | `SpringPluginContextTest#closesRegistrationsInReverseOrder` | 通过 |
| TC-PLUGIN-003 | P1 | 事件发布与销毁 | 插件注册事件订阅者 | 发布事件后销毁上下文 | 事件可达，销毁后订阅者移除 | `SpringPluginContextTest#eventBusPublishesAndDisposesSubscribers` | 通过 |
| TC-PLUGIN-004 | P1 | 事件处理器异常隔离 | 订阅者抛出异常 | 发布事件 | 异常被容器捕获，不破坏事件总线 | `SpringPluginContextTest#eventBusContainsHandlerExceptions` | 通过 |
| TC-PLUGIN-005 | P1 | Hook 注册与注销 | 插件声明生命周期 hook | 注册后注销 | 注册可达，注销后不可达 | `SpringPluginContextTest#hookRegistersAndUnregistersWithLifecycle` | 通过 |
| TC-PLUGIN-006 | P0 | Hook 阻断决策 | hook 返回阻断结果 | 执行 hook | 返回 block decision，并可拦截流程 | `SpringPluginContextTest#hookExecutesAndReturnsBlockDecision` | 通过 |
| TC-PLUGIN-007 | P1 | 默认层替换 | 配置 store 存在默认层 | 保存 defaults | 仅替换默认层，不影响 override | `DatabasePluginConfigStoreTest#defaultsReplaceOnlyDefaultLayer` | 通过 |
| TC-PLUGIN-008 | P1 | Override 覆盖与清除 | 同一 key 存在默认值和 override | 清除 override | 读取结果回退到默认值 | `DatabasePluginConfigStoreTest#overrideWinsAndCanBeCleared` | 通过 |
| TC-PLUGIN-009 | P1 | 合并默认与 override | 两个层存在不同或相同 key | 读取全部配置 | override 覆盖默认层 | `DatabasePluginConfigStoreTest#getAllMergesOverrideOverDefault` | 通过 |
| TC-PLUGIN-010 | P2 | 空白配置键值清洗 | 配置中存在空白 key/value | 保存配置 | 空白 key 被忽略，value 做规范化 | `DatabasePluginConfigStoreTest#blankKeysAreIgnoredAndValuesAreNormalized` | 通过 |

## 17. 架构与契约

| 编号 | 优先级 | 用例 | 前置条件 | 操作 | 预期结果 | 自动化验证 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-ARCH-001 | P0 | API 层依赖边界 | `api` 模块源码就绪 | 执行 ArchUnit 扫描 | 不依赖 domain 或 infrastructure | `ApiArchitectureTest#apiMustNotDependOnDomainOrInfrastructure` | 通过 |
| TC-ARCH-002 | P0 | API DTO 命名 | `api` 模块契约类就绪 | 执行 ArchUnit 扫描 | 数据契约类以 `DTO` 结尾 | `ApiArchitectureTest#apiContractsMustUseDtoNaming` | 通过 |
| TC-ARCH-003 | P0 | API 返回包装类型 | `api` 模块响应类就绪 | 执行 ArchUnit 扫描 | 返回类型只允许统一 Wrapper | `ApiArchitectureTest#apiResponseMustOnlyContainWrappers` | 通过 |
| TC-ARCH-004 | P0 | API 接口统一返回 | API 接口方法就绪 | 执行 ArchUnit 扫描 | 返回 `ResponseWrapper` | `ApiArchitectureTest#apiInterfaceMethodsMustReturnResponseWrapper` | 通过 |
| TC-ARCH-005 | P0 | 流式契约返回 SSE | 流式 API 接口就绪 | 执行 ArchUnit 扫描 | 返回 `SseEmitter` | `ApiArchitectureTest#streamContractsMustReturnSseEmitter` | 通过 |
| TC-ARCH-006 | P0 | Case 层依赖边界 | `case` 模块源码就绪 | 执行 ArchUnit 扫描 | 不依赖 infrastructure | `CaseArchitectureTest#casesMustNotDependOnInfrastructure` | 通过 |
| TC-ARCH-007 | P0 | Case 层不声明业务对象 | `cases` 包源码就绪 | 执行 ArchUnit 扫描 | 不新增业务实体或值对象 | `CaseArchitectureTest#casesMustNotDeclareBusinessObjects` | 通过 |
| TC-ARCH-008 | P0 | Factory 暴露统一 Pipeline | `..cases..factory..` 下存在 Factory | 执行 ArchUnit 扫描 | Factory 实现 `CasePipelineFactory` | `CaseOrchestrationArchitectureTest#factoriesMustExposeUnifiedPipelines` | 通过 |
| TC-ARCH-009 | P0 | Node 使用策略路由 | orchestration node 就绪 | 执行 ArchUnit 扫描 | Node 通过策略路由分发 | `CaseOrchestrationArchitectureTest#nodesMustUseStrategyRouter` | 通过 |
| TC-ARCH-010 | P0 | CaseImpl 经由 Factory | `*CaseImpl` 类就绪 | 执行 ArchUnit 扫描 | 委托对应 `*Factory` | `CaseOrchestrationArchitectureTest#caseImplsMustDelegateThroughFactories` | 通过 |
| TC-ARCH-011 | P0 | Domain 不依赖 Infrastructure | agent domain 源码就绪 | 执行 ArchUnit 扫描 | 不引入 infrastructure 包 | `AgentRunArchitectureTest#domainMustNotDependOnInfrastructure` | 通过 |
| TC-ARCH-012 | P0 | Agent Domain 无 Spring | agent domain 源码就绪 | 执行 ArchUnit 扫描 | 不依赖 Spring 类型 | `AgentRunArchitectureTest#agentDomainMustNotDependOnSpring` | 通过 |
| TC-ARCH-013 | P0 | Agent Domain 无 Jackson | agent domain 源码就绪 | 执行 ArchUnit 扫描 | 不依赖 Jackson 类型 | `AgentRunArchitectureTest#agentDomainMustNotDependOnJackson` | 通过 |
| TC-ARCH-014 | P0 | Trigger 层依赖边界 | trigger 源码就绪 | 执行 ArchUnit 扫描 | 只依赖 API 与 case | `TriggerArchitectureTest#triggerMustOnlyDependOnApiAndCase` | 通过 |
| TC-ARCH-015 | P0 | Trigger Facade 返回包装 | Trigger facade 方法就绪 | 执行 ArchUnit 扫描 | 返回 `ResponseWrapper` | `TriggerArchitectureTest#triggerFacadeMethodsMustReturnResponseWrapper` | 通过 |
| TC-ARCH-016 | P0 | Trigger HTTP 返回类型 | Trigger HTTP 方法就绪 | 执行 ArchUnit 扫描 | 返回 Wrapper 或 SSE | `TriggerArchitectureTest#triggerHttpMethodsMustReturnWrapperOrSse` | 通过 |

## 18. 后续待补用例

| 建议编号 | 优先级 | 待补场景 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| TC-SESS-005 | P1 | JDBC SessionEventStore 等价性 | 使用 H2/Testcontainers 验证与 JSONL 相同的版本迁移、追加、读取、usage 往返语义 | 待自动化 |
| TC-SESS-006 | P2 | 损坏 JSONL 行容错 | 明确定义跳过、告警或快速失败策略，防止静默丢事件 | 待自动化 |
| TC-PROJ-006 | P2 | 投影缓存 TTL 与容量 | 如未来启用持久化或限制缓存容量，需验证淘汰、过期和内存上限 | 待自动化 |
| TC-LEASE-006 | P1 | 多线程租约竞争 | 并发获取、续期、释放与过期抢占，验证不存在两个活跃写者同时写入 | 待自动化 |
| TC-METER-002 | P2 | 缺失 usage 边界 | 验证无 usage、零 usage 和非法 usage 的容错口径 | 待自动化 |
| TC-PLUG-008 | P2 | 插件库存 HTTP 端到端 | 启动应用后请求 `/api/harness/plugins/inventory`，验证 Wrapper、排序与插件目录隔离 | 待自动化 |
| TC-PLUGIN-011 | P2 | 插件 JAR 损坏与冲突 | 验证损坏 JAR、重复插件 ID、依赖冲突时的失败状态与用户提示 | 待自动化 |
| TC-SEC-001 | P0 | HTTP 鉴权与公网边界 | 部署前验证未授权访问、插件命令接口暴露面和反向代理策略 | 待执行 |

> 其中 TC-GW-003 至 TC-GW-006 已登记在 Gateway 用例表中，后续实现时直接补充自动化映射即可。

## 19. 新功能补测流程

1. **开发前登记**：在对应功能域新增用例编号；如果开启新域，先补充功能域说明和总览行。
2. **开发中验证**：优先实现单元或模块级自动化测试；涉及 HTTP、持久化、并发、SSE 的用例必须写明可执行环境。
3. **提交前回归**：执行 `mvn test`；只改特定模块时可先执行定向模块测试，最终提交前仍需全量验证。
4. **缺陷修复**：为缺陷新增用例，先复现失败，再修复通过，并保留用例作为回归基线。
5. **文档同步**：修改接口、格式、并发规则或错误语义时，同步更新预期结果；废弃用例保留编号并标记废弃原因。
6. **状态维护**：状态只能是 `通过`、`失败`、`阻塞`、`待自动化`、`待执行`；禁止把没有自动化映射的用例标记为 `通过`。

## 20. 当前验证记录

| 日期 | 分支 | 命令 | 结果 |
| --- | --- | --- | --- |
| 2026-09-14 | `v0.1.7` | `mvn test` | 通过；10 个模块全部成功 |
| 2026-09-14 | `v0.1.7` | `mvn clean test` | 通过；Surefire 执行 107 个测试，失败 0，错误 0，跳过 0 |
| 2026-09-14 | `v0.1.7` | `git diff --check` | 通过 |
