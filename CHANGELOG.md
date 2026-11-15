# 版本

> 各版本详情见 `docs/md/release-vX.Y.Z-development-notes.md`；以下按 7 个模块记录包级功能更新。
## 版本号：0.2.3
- 更新日期：2026-11-08
- 详情：docs/spec/0.2.3/release-v0.2.3-development-notes.md

- **新增**：
  - **dsh-java-api**：新增 `IMiniAppApi`（微信小程序登录用例接口）与 `MiniAppLoginRequestDTO`/`MiniAppLoginResponseDTO`
  - **dsh-java-trigger**：新增 `MiniAppController`（`POST /api/miniapp/login`）
  - **dsh-java-case**：新增 `MiniAppLoginUseCase`——以 `wx.login` 临时 code 调用微信 `jscode2session` 换取 `openid`，签发 2 小时短期会话令牌（`mini_` 前缀）；未配置 appid/secret 时返回 `50300`，不降级为匿名登录
  - **dsh-java-app**：`application.yml` 新增 `dsh.miniapp.wechat.appid/secret`（通过 `DSH_MINIAPP_WECHAT_APPID`/`DSH_MINIAPP_WECHAT_SECRET` 环境变量注入，AppSecret 仅服务端配置）
  - **移动端能力（移动访问开关 + 设备配对，重构回归）**：
    - **dsh-java-api**：`IDeviceApi` 新增 `getMobileAccess`/`setMobileAccess` 开关接口；新增 `MobileAccessDTO`；DTO 统一 `*DTO` 后缀（`DevicePairRequestDTO`、`DevicePairResponseDTO`）
    - **dsh-java-trigger**：`DeviceController` 新增 `GET/PUT /api/devices/mobile-access`
    - **dsh-java-case**：`DevicePairingUseCase` 移动访问默认关闭，需桌面端显式开启；新增 `DevicePairingUseCaseTest`
    - **dsh-java-domain**：设备凭据改为聚合 `DeviceCredentialAggregate`（仅存 Token SHA-256 摘要，不存明文）
    - **dsh-java-infrastructure**：新增 `DeviceRepository`（内存最小闭环实现，后续可替换为数据库仓储）
  - **AI 可发现性**：新增 `AGENTS.md`（AI 编码助手的构建/测试命令与约定）、`llms.txt`（面向 LLM 的项目摘要）、`docs/md/ai-discoverability.md`（系列仓库 About/topics 配置清单）
- **更新**：
  - 全模块 pom 版本 `0.2.2` → `0.2.3`
  - `ApiKeyAuthInterceptor` 重新接入设备级 Bearer Token 校验（`devt_` 前缀，吊销后立即 401）
  - Web 控制台（`app.js`/`app.css`）适配移动访问开关与设备管理面板
- **迭代二（进行中）**：参考源平台能力对齐
  - **dsh-java-domain**：新增 `HarnessError` 统一错误基类与 `ToolArgsError`；`StorageException`/`AttachmentError`/`WebFetchError`/`TerminalBackendCleanupError` 接入统一体系
  - **dsh-java-domain**：`ToolCallExecutor` 新增 `mapFailure`，工具抛出 `HarnessError` 时在 `tool/result` 中保留 `error.name` 与 `error.code`，不再统一回落 `TOOL_ERROR`
  - **dsh-java-domain**：`ToolExecutionResult` 新增结构化失败重载；`ToolErrorCodes` 新增 `TOOL_ERROR`、`MODEL_DOES_NOT_SUPPORT_IMAGES`
  - **dsh-java-domain**：`WebFetchTool` 捕获抓取分类错误；新增 `WEB_FETCH_DNS_FAILED` 等 7 个传输错误码
  - **dsh-java-infrastructure**：`LocalWebService` 按异常类型分类抓取失败；`LocalTerminalPort` 清理失败抛出 `CLEANUP_FAILED`
  - **dsh-java-domain**：CUA、浏览器、Worktree、Ralph、Hook、MCP 错误类统一继承 `HarnessError`，新增 `CUA_*`、`BROWSER_*`、`WORKTREE_NOT_GIT_REPO`、`RALPH_LOOP_*`、`HOOK_BLOCKED`、`MCP_RECONNECT_EXHAUSTED` 错误码
  - **dsh-java-domain**：Shell/FS 参数错误由 `MISSING_PARAM` 收敛为 `ToolArgsError`/`INVALID_ARGS`，统一 `invalid arguments: ` 前缀；修复缺省参数被当作字符串 `"null"` 继续执行的问题
  - **dsh-java-domain / infrastructure**：新增 `TerminalError` 基类；`TerminalBackendCleanupError` 聚合启动与清理失败（`cause` + `suppressed`），终端会话查找/运行状态错误改用 `TERMINAL_*` 码
  - **错误码总表**：新增 [docs/spec/0.2.3/error-codes.md](docs/spec/0.2.3/error-codes.md)，覆盖通用码、17 个子系统码表与旧→新映射
  - **Office 能力工具化**：新增 `office_render`/`office_convert`/`office_recalculate` 并注册到工具目录；`OfficeSkill` 收敛为 `office` 路由 + `office-docx`/`office-pptx`/`office-xlsx` 内置写作技能；新增 `dsh.office.*` 输出治理配置与 `OfficeKitOutputStore` 过期/容量清理；LibreOffice 缺失映射为 `OFFICE_KIT_UNAVAILABLE`，不阻断启动
  - **Subagent 名称寻址**：`InProcessSubagentDriver` 增加名称索引与同级唯一校验、名称/会话 id 解析、`listAgents` 投影；`send_message`/`interrupt_agent` 改用 `agent` 参数并拒绝 `agent_id`；`SubagentTool` 支持可选 `name`，返回 `started subagent <name>` 与 `{kind:'activation', name}`；`list_agents` 返回 `{name, status, description?}`
  - **Web 计费与凭据**：新增 `searchBilling`/`fetchBilling`（`auto|account|api-key`）与有序凭据来源，`WebToolConfig`/`WebCredentialResolver` 解析优先级；`web_search`/`web_fetch` 按解析结果传递凭据；取消异常映射 `WEB_ABORTED`，`WebBillingError` 保留 cause
  - **Worktree 归档清理**：新增 `WorktreeArchiveCleaner`/`WorktreeArchivePolicy`/`IWorktreeCleanupPort`，归档时按脏树、子模块、锁、分离 HEAD 条件跳过，非 force 移除且保留分支与提交；`JGitWorktree` 实现状态检查与移除；新增 `plugin:worktree/created` 事件类型与载荷
  - **运行时上下文提醒**：新增 `RuntimeContextSnapshot`，`runtime-context` 统一包裹 `<system-reminder>`，首份/后续快照文案区分，清空输出 `form=snapshot`/`sections=[]`；旧快照缺 `sections` 仅刷新一次并保留原前缀
  - **Plugin Bundle 更新**：新增 `PluginBundleInfo`（可选 `update=registry|git`）、`PluginBundleUpdateService`（Git 仅固定完整提交可更新）、`unexpected-package` 错误码；`PluginRegistryService` 新增带 `expectedPluginId/expectedVersion` 的安装校验
  - **DeepSeek 文件上传超时**：`DeepSeekAdapterOptions` 新增 `filesApiTimeoutMs`（默认 20s）与 `filesConnectTimeoutMs`（默认 5s）；新增 `DeepSeekImageAttachmentPolicy`，上传超时降级内联 data URL，超时预算按 2 倍退避（上限 8 倍），成功后复位
  - **WebServer 默认鉴权**：新增 `@RouteAuth`（`required|anonymous`）与 `RouteAuthResolver`，未声明路由默认要求认证，`.well-known` 公开路由显式匿名；认证逻辑抽为 `WebAuthProvider`/`DefaultWebAuthProvider`，支持 `X-API-Key`、`Bearer` 与 `dsh_session` Cookie；`ConnectionOriginGuard` 在连接监听器活动时对匿名路由执行同源/白名单来源检查；新增 `harness.auth.cookie-name`、`harness.auth.connection-listener.enabled`、`harness.auth.allowed-origins`、`harness.webserver.auth-default` 配置
  - **会话状态与事件写入**：新增 `SessionStatus`（`running`/`completionUnread`/`completionFailed`）与 `SessionReadState`；`SessionReadingRegistry` 维护宿主共享阅读记录，`mark` 在 `readingReady` 前拒绝，`turn/end` 自动更新完成位置，失败轮次驱动 `completionFailed`；`ISessionEventLogService.append` 新增事件数组重载（委托 `appendBatch`），单事件签名保持兼容
  - **CUA / PTC 部分对齐**：新增 `CuaTool`（`cua`）与 `CuaRunCodeTool`（`cua_run_code`），`AgentToolCatalog` 按 `agentPreset` 选择，未组合 preset 默认获得 `cua_run_code`；CUA 运行时缺失时按 preset 抛出可读错误；新增 `PtcProgramOutput`，PTC 输出改为 `inspect(...)` 显式选择，图片按调用位置附加，返回值与末尾表达式不再自动输出

  **旧 → 新错误码映射**

  | 旧 | 新 | 范围 |
  | --- | --- | --- |
  | `MISSING_PARAM` | `INVALID_ARGS`（`ToolArgsError`） | Shell/FS 参数入口 |
  | `<field> parameter is required` | `invalid arguments: <field> is required` | Shell/FS 文案 |
  | `IMAGE_UNSUPPORTED_MODEL` | `MODEL_DOES_NOT_SUPPORT_IMAGES` | 图片输入 |
  | `JOB_BACKGROUND_DISABLED` | 删除 | 后台执行 |
  | `StorageException(UNIT_NOT_FOUND, "attachment not found")` | `AttachmentError(ATTACHMENT_NOT_FOUND)` | 附件读取 |
  | `IllegalArgumentException("terminal session not found")` | `TerminalError(TERMINAL_SESSION_NOT_FOUND)` | 终端 |
  | `IllegalStateException("terminal session is not running")` | `TerminalError(TERMINAL_SESSION_NOT_RUNNING)` | 终端 |
  | 清理失败 `cause=null` | `TerminalBackendCleanupError(CLEANUP_FAILED)` | 终端关闭 |
  | 未知异常 `name=code` | `ToolInternalError` / `TOOL_ERROR` | 工具管线 |
  | Hook 失败 `name=HOOK_BLOCKED` | `HookError` / `HOOK_BLOCKED` | 工具管线 |
  | Web 抓取返回 `Fetch error: ...` 成功文本 | `WebFetchError(WEB_FETCH_*)` | 本地抓取 |
  | `CancellationException` 归为网络错误 | `WEB_ABORTED` | Web 抓取取消 |
  | 无凭据/账户缺失静默失败 | `WEB_CREDENTIAL_MISSING` / `WEB_ACCOUNT_UNAVAILABLE` | Web 计费 |

  **变更分类**
  - 新增：`HarnessError` 体系与 `error-codes.md`、Office 三工具、Worktree 归档清理、Plugin Bundle 更新、运行时上下文快照、`SessionReadingRegistry`、`@RouteAuth`/`WebAuthProvider`、`CuaTool`/`CuaRunCodeTool`、`PtcProgramOutput`
  - 更新：`send_message`/`interrupt_agent` 改用 `agent` 名称寻址；`web_search`/`web_fetch` 支持计费模式与有序凭据；DeepSeek 文件上传新增超时与内联降级；会话摘要新增 `completionUnread`/`completionFailed`；事件 API 新增数组重载
  - 破坏性变更：未声明 `@RouteAuth` 的路由默认要求认证（可用 `harness.webserver.auth-default=anonymous` 回滚）；Shell/FS 参数错误统一为 `INVALID_ARGS`；子代理工具不再接受 `agent_id`；`SessionStatus` 投影字段扩展

## 版本号：0.2.2
- 更新日期：2026-11-02
- 详情：docs/spec/0.2.2/release-v0.2.2-development-notes.md

- **dsh-java-app**：
  - 新增 `feishu_bot` 表迁移（`V20261102__feishu_bot.sql`）并同步 `schema.sql`（表数 30 → 31）
  - `harness.yml` 新增 `dsh.feishu.*` 配置项（启停开关与长连接/流式/配对参数）
- **dsh-java-trigger**：
  - 新增 `BotController`（`/api/harness/bots`：list/pairing/save/update/delete/enable/disable/test）
- **dsh-java-api**：
  - 新增 `IBotApi` 与 `FeishuBotDTO`/`FeishuBotSaveDTO`/`FeishuPairingDTO`/`FeishuPairingStartDTO`
  - 新增 `FeishuEnabledCondition` 条件装配开关
- **dsh-java-case**：
  - 新增 `FeishuBotUseCase`（Bot 生命周期）、`FeishuPairingUseCase`（一键配对）、`FeishuBotInboundHandler`（入站桥接与流式回发）
- **dsh-java-domain**：
  - 新增 `domain.bot` 聚合 `FeishuBotAggregate`、值对象、`FeishuEventDeduplicator`、4 端口与仓储接口
  - 新增 `LlmRequestPurposes` 用途声明值对象（`declare`/`require`/`isDeclared`/`declared`），`GenerateOptions` 构造期对 `purpose` 声明式校验（`null` 透传、未声明/空白抛 `IllegalArgumentException`）
  - `BasicCompactionEngine`/`PlannerService` 分别声明 `COMPACTION`/`COLLABORATION_PLANNING` 用途键
  - 调度工具组重构：`ScheduleTools` 由旧 `Create`/`ListAll`/`Delete` 改为 `schedule_create`/`schedule_list`/`schedule_view`/`schedule_edit` 四工具（`AgentToolCatalog` 条件注册）；新增 `SubagentSessions`（`child-` 前缀判定），子 agent 调用调度工具返回 `subagent_session`
  - 调度宿主全域：`ScheduleRecord` 改为按 `id` 全域管理（`sourceSessionId` 仅来源标注、跨会话可见）；`ScheduleTiming` 带类型（`AFTER`/`AT`/`EVERY`/`DAILY`/`WEEKLY`/`RRULE`）+ `RRule` 解析器（支持 `DTSTART`/`TZID`/`INTERVAL`/`BYDAY`/`BYHOUR`/`BYMINUTE`，显式拒绝 `COUNT`/`UNTIL`/`BYMONTH`）；`ScheduleKind` 由 RRULE `FREQ` 派生
  - `ScheduleService` 支持 `edit` 乐观并发（`expectedUpdatedAt` 不一致抛 `ScheduleConflictException`，码 `schedule_conflict`），`drive(now)` 推进重复任务 `nextAt`、一次性任务 `ended=true`；`InMemoryScheduleRepository` 按 `id` 寻址（standalone 仍为内存实现，不新增数据库表）
- **dsh-java-infrastructure**：
  - 新增飞书适配器 `FeishuWsGateway`（WebSocket 长连接）、`FeishuMessagingClient`（IM 出站）、`FeishuPairingClient`（设备授权）、`FeishuBotRepository`/`IFeishuBotDao`/`FeishuBotPO`
  - 新增 `FeishuBeanConfig` 条件装配（`dsh.feishu.enabled`）
  - 引入 `com.larksuite.oapi:oapi-sdk:2.8.3`、`com.larksuite.oapi:larksuite-oapi-shaded-protobuf:2.4.6`
  - `DeepSeekAdapter` 改用 `LlmRequestPurposes.COMPACTION` 常量判定加 `x-dsh-compact` 头
- **dsh-java-types**：本版本无变更

- **备注**：
  - 除飞书主线外，本版本同步对齐参考源 `v0.2.2-alpha.2` 升级指南的平台能力（`llm-request-purpose-declarations` / `schedule-agent-tools` / `schedule-remote-host-tasks`）；另三条升级项（`context-menu-target` / `forge-git-permissions` / `sdk-run-commands`）在本项目无对应体系，不适用。
  - 平台能力对齐不引入新的第三方依赖，不新增 Maven 模块，不新增数据库表（调度在 standalone 模式仍为内存实现）。

### 修订记录（2026-11-05 ~ 2026-11-06）

- **移除移动端设备配对功能**（功能未上线，整体下线；0.2.3 已按聚合 + DTO 规范重构回归，见上方版本条目）：
  - **dsh-java-api**：删除 `IDeviceApi`、`DeviceDTO`、`DevicePairingCodeDTO`、`DevicePairRequest`、`DevicePairResult`
  - **dsh-java-trigger**：删除 `DeviceController`（`/api/devices/*` 全部端点）
  - **dsh-java-case**：删除 `DevicePairingUseCase`
  - **dsh-java-infrastructure**：删除 `InMemoryDeviceRepository`
  - **dsh-java-domain**：删除 `DeviceCredential`、`IDeviceRepository`
  - **dsh-java-trigger**：`ApiKeyAuthInterceptor` 同步移除 `devt_` 前缀设备 Token 校验分支（改为无参构造）
- **架构修复**：`FeishuInboundConfig#feishuInboundHandler` 返回类型由 domain 端口 `IFeishuInboundHandler` 改为 case 层 `FeishuBotInboundHandler`（实现该端口），消除 trigger→domain 分层违规，修复 ArchUnit `triggerMustOnlyDependOnApiAndCase` 测试失败
- **发布配置**：`plugins/dsh-plugin-archetype` 增加 `skipPublishing=true`，`mvn deploy` 不再发布该模块（`central-publishing-maven-plugin` 0.10.0 忽略 `maven.deploy.skip`，必须用其自身的 `skipPublishing` 排除）
- **SQL 脚本**：`lead` 关键字调整为 `leader`；0.2.1 设计文件更新并标记完成

## 版本号：0.2.1
- 更新日期：2026-10-12 - 2026-10-16
- 详情：docs/spec/0.2.1/release-v0.2.1-development-notes.md

- **dsh-java-app**：
  - 新增 checkpoint 与 workspace 表迁移（`V20261011__checkpoint.sql`、`V20261011__workspace.sql`）
  - fat jar 改用 exec 分类器并跳过上传（`classifier=exec`、`attach=false`）
  - 补充应用上下文冒烟测试 5 个
- **dsh-java-trigger**：本版本无变更
- **dsh-java-api**：本版本无变更
- **dsh-java-case**：本版本无变更
- **dsh-java-domain**：
  - 新增 LLM 重试策略与错误分类（`LlmRetryPolicy` / `RetryableErrorClassifier` / `LlmErrorCodes`）
  - 新增上下文注入链（AgentsMd / FileReference / SessionSnapshot / Time / Tmux）与 Slash 命令
  - 新增压缩：`CompactCommand`、`ToolResultPruner`、`ImageOffloader`
  - 新增护栏：`DuplicateToolGuard`、`PerToolDeadlinePolicy`、`SandboxPolicyInjector`
  - 新增 Codex Hook 协议桥接与进程内子代理驱动
  - 新增会话检查点（`CheckpointStrategy` / `CheckpointSnapshot`）与快照策略
  - 新增会话全文检索契约（`SessionSearchProvider` / `SessionFullTextIndex`）
  - 新增 OTel 遥测导出链路（`OtelService` / `SessionTelemetryExporter` / `OtlpBuffer`）
  - 新增统计、标题、大纲三类会话投影
  - 新增 MCP Resource 发现能力（`McpResourceDiscovery` / `IMcpResourcePort`）
  - 新增 Glob、Grep、Schedule 工具与 `ToolPresentation` 值对象
  - 新增网页搜索（Exa / Perplexity）与 Typert、PTC 沙箱 Node 进程
  - 新增 LSP stdio Provider 与 Profile Bundle 装配
  - 新增内容寻址附件存储与 Workspace 聚合实体
- **dsh-java-infrastructure**：
  - 新增 PiAi 协议 LLM 适配器（`PiAiAdapter`）
  - 新增 Lucene 9.11.1 会话全文索引（`LuceneSessionSearchIndex`）
  - 新增 OTLP HTTP 遥测导出器（`OtlpHttpExporter`）
    - 按 `dsh.otel.enabled=true` 条件装配，端点由 `dsh.otel.exporter.otlp-endpoint` 配置（默认 `http://127.0.0.1:4318/v1/traces`）
    - 修复导出器无默认构造器导致 Spring 上下文启动失败的问题
  - 新增检查点与工作区 JDBC 仓储（`JdbcCheckpointRepository` / `JdbcWorkspaceRepository`）
  - 新增 Exa 与 Perplexity 网页搜索 HTTP 适配
  - 新增 Typert 生成器（ts-morph）、PTC 沙箱 Node 进程与 MCP stdio 资源端口
  - 新增 SHA-256 内容寻址存储实现
- **dsh-java-types**：本版本无变更
- **备注**：
  - 全量 pom 发布元数据对齐 GitHub 仓库，POM 版本统一为 0.2.1
  - Dockerfile 与打包脚本同步指向 `*-exec.jar`
  - 全量测试：domain 564 + infrastructure 53 + app 5 = 622 通过，0 回归

## 版本号：0.2.0
- 更新日期：2026-10-11
- 详情：docs/md/release-v0.2.0-development-notes.md

1. 前后端工程合并
2. 调整pom文件内路径
3. 修改CHANGELOG.md文件
4. 按app、trigger、api、case、domain、infrastructure、types七个包补充版本更新

- **dsh-java-app**：新增 token_snapshot 表及建表迁移
- **dsh-java-trigger**：本版本无变更
- **dsh-java-api**：本版本无变更
- **dsh-java-case**：本版本无变更
- **dsh-java-domain**：
  - LLM 生成服务接入 waterfall
  - 新增重放感知 Token 快照与计量
  - 新增 MCP 自动重连与重连策略
  - 新增模块级 HMR 与运行时解析
  - 扩展 SDK 会话、工具与资源查询
- **dsh-java-infrastructure**：
  - 新增 Token 快照 JDBC 仓储
  - 新增进程内运行时解析适配器
  - Stdio 传输新增请求处理器路由
- **dsh-java-types**：本版本无变更
- **备注**：全模块包路径统一迁移

## 版本号：0.1.9
- 更新日期：2026-10-05
- 详情：docs/md/release-v0.1.9-development-notes.md

- **dsh-java-app**：补充 Sonatype 发布元数据
- **dsh-java-trigger**：本版本无变更
- **dsh-java-api**：本版本无变更
- **dsh-java-case**：本版本无变更
- **dsh-java-domain**：
  - 新增 Scope 路由与事件分发
  - 新增 Waterfall 拦截链执行器
  - 工具执行改为五阶段管线
  - 会话格式升级至 V4 迁移链
  - 新增投影框架与写后合流缓存
  - 新增 LLM Stream Waterfall 链路
- **dsh-java-infrastructure**：
  - 会话存储实现持久化 Seam
  - 新增持久化契约测试用例
- **dsh-java-types**：本版本无变更

## 版本号：0.1.8
- 更新日期：2026-09-28
- 详情：docs/md/release-v0.1.8-development-notes.md

- **dsh-java-app**：新增全局 CORS 与运行期配置
- **dsh-java-trigger**：
  - 新增运行期问答等 10 个控制器
  - 增量批处理与 SSE 心跳保活
- **dsh-java-api**：
  - 新增运行期问答契约与 DTO
  - 请求响应新增推理力度与工件
- **dsh-java-case**：
  - 新增运行期问答用例链路
  - 支持会话恢复与工件收集回传
- **dsh-java-domain**：
  - 新增 cwd 感知与路径解析
  - 新增审批感知 FS 与 Shell
  - 工具执行支持超时取消
  - 新增沙箱额外可写根目录
  - OpenAI 兼容端点 URI 统一解析
- **dsh-java-infrastructure**：
  - 新增扩展管理与 MCP 运行时
  - 新增数字人、协作与审批网关
  - 会话事件支持批量写入
- **dsh-java-types**：工具运行上下文新增工件记录

## 版本号：0.1.7
- 更新日期：2026-09-25
- 详情：docs/md/release-v0.1.7-development-notes.md

- **dsh-java-app**：本版本无变更
- **dsh-java-trigger**：
  - 新增统一网关 SSE 流接口
  - 新增插件库存查询接口
  - 修正工具结果事件状态透传
- **dsh-java-api**：
  - 新增网关流与插件库存契约
  - 工具结果 DTO 新增三个字段
- **dsh-java-case**：新增插件库存查询用例组装
- **dsh-java-domain**：
  - 会话事件格式升级到 v3
  - 新增单会话写租约保护
  - 新增会话投影缓存机制
  - 新增 Token 用量计量服务
- **dsh-java-infrastructure**：
  - 会话存储兼容旧头自动迁移
  - 新增插件清单与 JAR 元数据
- **dsh-java-types**：本版本无变更

## 版本号：0.1.6
- 更新日期：2026-09-12
- 详情：docs/md/release-v0.1.6-development-notes.md

- **dsh-java-app**：控制台支持模型渠道管理与发现
- **dsh-java-trigger**：
  - 新增模型设置与渠道模板接口
  - 新增上游模型发现接口
- **dsh-java-api**：任务响应新增实际渠道字段
- **dsh-java-case**：
  - 新增模型设置与发现流水线
  - 插件激活后校验运行状态
- **dsh-java-domain**：
  - 新增模型设置服务与生命周期
  - 新增模型渠道路由解析服务
- **dsh-java-infrastructure**：
  - 新增模型设置仓储实现
  - 新增多协议 LLM 适配器工厂
- **dsh-java-types**：本版本无变更

## 版本号：0.1.5
- 更新日期：2026-09-10
- 详情：docs/md/release-v0.1.5-development-notes.md

- **dsh-java-app**：
  - 控制台支持 @插件提及与回填
  - 修正系统提示词输出要求
- **dsh-java-trigger**：新增插件命令接口访问鉴权
- **dsh-java-api**：本版本无变更
- **dsh-java-case**：派发节点解析提及生成优先级
- **dsh-java-domain**：
  - 新增插件 ID 字符集安全校验
  - 工具重复注册改为幂等处理
  - 插件描述写入前做提示词清洗
- **dsh-java-infrastructure**：本版本无变更
- **dsh-java-types**：本版本无变更

## 版本号：0.1.4
- 更新日期：2026-09-09 至 2026-09-10
- 详情：docs/md/release-v0.1.4-development-notes.md

- **dsh-java-app**：启动时执行插件状态对账修复
- **dsh-java-trigger**：新增插件产物分析与配置接口
- **dsh-java-api**：新增插件候选与安装相关 DTO
- **dsh-java-case**：新增插件候选解析与配置用例
- **dsh-java-domain**：新增 plugin_status 状态查询工具
- **dsh-java-infrastructure**：
  - 插件配置持久化到数据库
  - 连接池增加快速失败治理配置
- **dsh-java-types**：本版本无变更

## 版本号：0.1.3
- 更新日期：2026-09-08
- 详情：docs/md/release-v0.1.3-development-notes.md

- **dsh-java-app**：
  - 配置收敛至 harness.yml 统一管理
  - 控制台新增工具卡片与会话管理
- **dsh-java-trigger**：新增有效配置查询诊断接口
- **dsh-java-api**：新增有效配置查询相关契约
- **dsh-java-case**：
  - 新增有效配置查询用例
  - 新增 MCP 与技能运行时装配
- **dsh-java-domain**：新增有效配置端口与工具目录
- **dsh-java-infrastructure**：
  - 新增 HTTP MCP 客户端适配
  - 新增配置校验与脱敏端口
- **dsh-java-types**：插件 SPI 新增 PluginContext 入口

## 版本号：0.1.2
- 更新日期：2026-09-05
- 详情：docs/md/release-v0.1.2-development-notes.md（待补齐，当前内容取自架构图）

- **dsh-java-app**：Web 控制台新增工具卡片与渲染
- **dsh-java-trigger**：19 个 REST 控制器接入 API Key
- **dsh-java-api**：契约按命令与查询分离隔离
- **dsh-java-case**：引入 factory + node 用例编排
- **dsh-java-domain**：Agent 生命周期、事件溯源与工具审批
- **dsh-java-infrastructure**：新增 MySQL/H2 持久化与模型适配
- **dsh-java-types**：插件 SPI 契约包对外提供

## 版本号：0.1.1
- 更新日期：2026-09-03

- 搭建初步框架，包含 7 个模块的代码结构
- 梳理plugin的实现逻辑


## 版本号：0.1.0

- 更新日期：2026-09-01
- 初始化版本