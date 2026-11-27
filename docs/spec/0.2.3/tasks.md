# DeepSeek Harness Java v0.2.3 编码任务清单

> 版本：`v0.2.3` | 基线：v0.2.2 | 架构：DDD 六边形架构（Maven 多模块 / Spring Boot 3.5.16 / Java 17）
>
> 本清单将 `docs/spec/0.2.3/spec.md` 的 §5.1–§5.7 能力领域与 `design.md` 的分层落位、接口清单转化为可执行、可验收的编码任务。
>
> 主线为**移动端接入**：移动设备访问开关（默认关闭）+ 设备配对重构回归（DTO 规范化 + 聚合建模）+ 微信小程序登录（jscode2session）+ `devt_` 拦截校验恢复 + AI 可发现性文档。
>
> **范围边界（强制）**：
> 1. **不新增 Maven 模块**，依赖方向保持 `trigger → api ← case → domain ← infrastructure`。
> 2. **不新增第三方依赖**：HTTP 使用 JDK `HttpClient`，JSON 使用既有 Jackson。
> 3. **不新增数据库表**：设备凭据与小程序会话为进程内存储；数据库仓储为后续版本任务。
> 4. **DTO 命名规范**：对外 DTO 统一 `*DTO` 后缀。
>
> **任务统计**：迭代一为 **9 个任务组**、**24 个子任务节**、约 **55 条可勾选条目**；迭代二（第十一节）新增 **12 个任务组**、**46 条验收条目（均已完成）**。
>
> **完成状态图例**：`[x]` 已完成 ｜ `[ ]` 待办/后续优化

---

## 1. 版本治理

> 依赖：无（基座） ｜ 覆盖：spec §5.6 ｜ 完成状态：已完成

### 1.1 统一版本标识为 0.2.3
- [x] 根 pom 与 7 个模块 pom、2 个插件 pom 版本统一升级为 `0.2.3`；验收：10 个 pom 版本一致 —— `pom.xml`、`dsh-java-{app,trigger,api,case,domain,infrastructure,types}/pom.xml`、`plugins/{sample-tools-plugin,dsh-plugin-archetype}/pom.xml`
- [x] `CHANGELOG.md` 新增 0.2.3 分节，区分"新增"与"更新"；验收：分节存在且分类清晰；依赖：无 —— `CHANGELOG.md`

---

## 2. api 契约

> 依赖：无（可与 3 并行） ｜ 覆盖：spec §5.1–§5.5、design §2.2 ｜ 完成状态：已完成

### 2.1 定义 IDeviceApi 与 5 个 DTO
- [x] 定义 `IDeviceApi`（8 方法：`getMobileAccess`/`setMobileAccess`/`createPairingCode`/`pair`/`listDevices`/`revoke`/`rotate`/`validateDeviceToken`，均返回 `Response<T>`）；验收：方法签名与 design §2.2（1）一致 —— `dsh-java-api/src/main/java/net/linlan/dsh/api/IDeviceApi.java`
- [x] 实现 `MobileAccessDTO`（record，1 字段 `enabled`）；验收：JSON 序列化字段名稳定 —— `dsh-java-api/.../dto/MobileAccessDTO.java`
- [x] 实现 `DevicePairingCodeDTO`（record，3 字段：6 位码 / expireAt / expireInSec）；依赖：无
- [x] 实现 `DevicePairRequestDTO`（record，3 字段：`pairingCode`/`deviceName`/`fingerprint`）；验收：替代旧 `DevicePairRequest`（无后缀命名）—— `dsh-java-api/.../dto/DevicePairRequestDTO.java`
- [x] 实现 `DevicePairResponseDTO`（record，4 字段：`deviceId`/`deviceName`/`token`/`issuedAt`）；验收：替代旧 `DevicePairResult`；token 仅在响应出现一次 —— `dsh-java-api/.../dto/DevicePairResponseDTO.java`
- [x] 实现 `DeviceDTO`（record，5 字段：`deviceId`/`deviceName`/`fingerprint`/`createdAt`/`lastSeenAt`，**不含 token 任何形态**）—— `dsh-java-api/.../dto/DeviceDTO.java`

### 2.2 定义 IMiniAppApi 与 2 个 DTO
- [x] 定义 `IMiniAppApi`（2 方法：`login`/`validateSession`）—— `dsh-java-api/src/main/java/net/linlan/dsh/api/IMiniAppApi.java`
- [x] 实现 `MiniAppLoginRequestDTO`（record，1 字段 `code`）与 `MiniAppLoginResponseDTO`（record，2 字段：`token`/`expireAt`）；验收：替代旧 `MiniAppLoginRequest`/`MiniAppSessionDTO` 命名 —— `dsh-java-api/.../dto/`

---

## 3. domain 模型与端口

> 依赖：无（可与 2 并行） ｜ 覆盖：spec §2、§6 ｜ 完成状态：已完成

### 3.1 设备聚合与仓储端口
- [x] 实现 `DeviceCredentialAggregate`（record，6 字段：`deviceId`/`deviceName`/`fingerprint`/`tokenHash`/`createdAt`/`lastSeenAt`，仅存摘要不存明文）；验收：包路径 `domain/device/model/aggregate`，替代旧 `DeviceCredential` —— `dsh-java-domain/src/main/java/net/linlan/dsh/domain/device/model/aggregate/DeviceCredentialAggregate.java`
- [x] 定义 `IDeviceRepository`（`findByDeviceId`/`findAll`/`save`/`delete`）—— `dsh-java-domain/src/main/java/net/linlan/dsh/domain/device/adapter/repository/IDeviceRepository.java`

---

## 4. case 用例

> 依赖：2（api）、3（domain） ｜ 覆盖：spec §5.1–§5.3、§5.5、design §2.1.2/§2.3/§2.4 ｜ 完成状态：已完成

### 4.1 实现 DevicePairingUseCase（设备配对用例）
- [x] 实现 `DevicePairingUseCase implements IDeviceApi`（`@Service`），常量 `PAIRING_TTL_MS=10min`、`MAX_ATTEMPTS_PER_WINDOW=10`、`ATTEMPT_WINDOW_MS=60s`；`mobileAccess: AtomicBoolean`（默认 false）；验收：与 design §2.1.2 状态模型一致 —— `dsh-java-case/src/main/java/net/linlan/dsh/cases/device/DevicePairingUseCase.java`
- [x] 开关联动：`setMobileAccess(false)` 清空 `pairingCodes`；关闭状态下 `createPairingCode`/`pair` 返回 `40300`；验收：spec §5.1 全规则
- [x] 配对码生命周期：`createPairingCode` 生成 6 位码并惰性清理过期条目；`pair` 中 `remove(code)` 一次性消费；无效 `40001`、过期 `40002`；验收：spec §5.2.1.5
- [x] 限流：按指纹摘要的 60s 滑动窗口，超 10 次返回 `42900`；验收：spec §5.2.1.4
- [x] 指纹唯一：配对前按摘要查重，重复返回 `40003`；验收：spec §5.2.1.6
- [x] 凭据签发与轮换：`dev-` deviceId、`devt_` token、SHA-256 摘要存储；`rotate` 保留设备信息仅换 tokenHash 并更新 `lastSeenAt`，设备不存在返回 `40004`；验收：spec §5.2.1.7/§5.3
- [x] token 校验：`validateDeviceToken` 摘要比对，空白返回 false；验收：spec §5.4

### 4.2 实现 MiniAppLoginUseCase（小程序登录用例）
- [x] 实现 `MiniAppLoginUseCase implements IMiniAppApi`（`@Service`），配置读取 `dsh.miniapp.wechat.appid/secret`（缺省空串）；未配置返回 `50300` 不降级；验收：spec §5.5.1.1 —— `dsh-java-case/src/main/java/net/linlan/dsh/cases/miniapp/MiniAppLoginUseCase.java`
- [x] jscode2session 调用：JDK `HttpClient`（连接 5s/请求 8s），URL 参数编码；`errcode` 非零 `40101`、缺 openid `40102`、异常 `50200`（warn 日志）；验收：spec §5.5.3
- [x] 会话签发与校验：`mini_` token、TTL 2h、`ConcurrentHashMap` 存储、校验时惰性清除过期 —— 同上

---

## 5. infrastructure 仓储

> 依赖：3（domain 端口） ｜ 覆盖：design §2.5 ｜ 完成状态：已完成

### 5.1 实现 DeviceRepository（内存仓储）
- [x] 实现 `DeviceRepository implements IDeviceRepository`（`@Repository`，`ConcurrentHashMap` 存储），javadoc 注明"最小闭环实现，后续可替换为数据库仓储"；验收：包路径 `infrastructure/adapter/repository`，替代旧 `InMemoryDeviceRepository` —— `dsh-java-infrastructure/src/main/java/net/linlan/dsh/infrastructure/adapter/repository/DeviceRepository.java`

---

## 6. trigger 触发层

> 依赖：2（api）、4（case 实现契约） ｜ 覆盖：spec §5.1–§5.4、design §2.2（3） ｜ 完成状态：已完成

### 6.1 实现 DeviceController 与 MiniAppController
- [x] 实现 `DeviceController`（`/api/devices`，7 端点：`GET/PUT mobile-access`、`POST pairing-codes`、`POST pair`、`GET /`、`DELETE {deviceId}`、`POST {deviceId}/rotate`），全部委托 `IDeviceApi`；验收：端点与 design §2.2（3）一致 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/http/DeviceController.java`
- [x] 实现 `MiniAppController`（`/api/miniapp`，1 端点：`POST /login`），请求体缺失按 `code=null` 处理走用例校验 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/http/MiniAppController.java`

### 6.2 恢复拦截器设备 token 校验
- [x] `ApiKeyAuthInterceptor` 重新注入 `IDeviceApi`，恢复 `devt_` 前缀分支（位于主 Key 校验之前、无论主 Key 是否配置均强校验），失败 401"Device credential revoked or invalid"；验收：spec §5.4 全规则 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/filter/ApiKeyAuthInterceptor.java`
- [x] 核对 `WebMvcConfig` 拦截器注册不受构造签名变化影响；验收：全路径拦截 `/**` 生效 —— `dsh-java-trigger/.../filter/WebMvcConfig.java`

---

## 7. app 装配与控制台

> 依赖：4（用例）、6（触发层） ｜ 覆盖：spec §5.5、design §2.4 ｜ 完成状态：已完成

### 7.1 配置项落位
- [x] `application.yml` 新增 `dsh.miniapp.wechat.appid/secret`，占位符 `${DSH_MINIAPP_WECHAT_APPID:}` / `${DSH_MINIAPP_WECHAT_SECRET:}`，注释说明取值来源与"未配置返回 50300、secret 仅服务端"；验收：环境变量注入生效 —— `dsh-java-app/src/main/resources/application.yml`

### 7.2 控制台适配（原生 JS，无前端框架）
- [x] `app.js` 增加移动访问开关与设备管理（列表/吊销/轮换/生成配对码）请求逻辑，复用既有鉴权头与 `Response<T>` 结构 —— `dsh-java-app/src/main/resources/static/app.js`
- [x] `app.css` 面板样式，不引入前端框架 —— `dsh-java-app/src/main/resources/static/app.css`

---

## 8. AI 可发现性文档

> 依赖：无（横切） ｜ 覆盖：spec §5.7 ｜ 完成状态：已完成

### 8.1 仓库级机器可读入口
- [x] 新增 `AGENTS.md`：项目定位、模块布局与依赖方向、构建/测试命令（含单模块与离线模式）、代码约定、禁止事项、关键文档索引 —— `AGENTS.md`
- [x] 新增 `llms.txt`：面向 LLM 的项目摘要（能力清单、技术栈、版本、构建/运行命令、模块地图、文档链接、关联仓库）—— `llms.txt`

### 8.2 系列仓库配置清单
- [x] 新增 `docs/md/ai-discoverability.md`：dsh-java 系列五个仓库（dsh-java / plugins / desktop / mobile / release）的 `llms.txt`/`AGENTS.md` 落地状态、GitHub About 描述建议与统一 topics —— `docs/md/ai-discoverability.md`

---

## 9. 测试与文档归档

> 依赖：全部实现 ｜ 覆盖：design §2.6 ｜ 完成状态：进行中

### 9.1 单元测试
- [x] 编写 `DevicePairingUseCaseTest`：开关默认关闭、关闭拒绝（`40300`）、参数校验、配对码一次性/过期（`40001`/`40002`）、指纹重复（`40003`）、限流（`42900`）、轮换与吊销后 token 失效、`validateDeviceToken` 摘要比对 —— `dsh-java-case/src/test/java/net/linlan/dsh/cases/device/DevicePairingUseCaseTest.java`
- [ ] 补充 `MiniAppLoginUseCaseTest`：mock 微信接口（成功/errcode/缺 openid/网络异常/未配置 50300）—— `dsh-java-case/src/test/java/net/linlan/dsh/cases/miniapp/`（待办）
- [x] 全模块 `mvn test` 回归，ArchUnit 架构规则通过

### 9.2 端到端验证
- [x] 桌面端链路：开开关 → 生成配对码 → 配对成功 → `devt_` 请求通过拦截器 → 吊销后 401 → 轮换后旧 token 401
- [ ] 小程序真机链路：`wx.login` → `/api/miniapp/login` → 会话令牌使用（依赖真机与微信凭据配置）

### 9.3 四件套归档
- [x] 归档四件套（`spec.md`/`design.md`/`tasks.md`/`release-v0.2.3-development-notes.md`）于 `docs/spec/0.2.3/`；验收：四件套齐备
- [x] `CHANGELOG.md` 0.2.3 分节补充完成（新增/更新分类）
- [ ] 0.2.3 开发说明中"验证结论"章节随真机验证补齐

---

## 10. 已知边界与后续版本任务（本版本不做）

- [ ] 设备凭据数据库仓储（`device_credential` 表 + 双库幂等迁移 + SQL 三方口径校验）
- [ ] 小程序会话持久化与 openid ↔ 用户档案绑定
- [ ] 设备 token 细粒度权限（按设备裁剪工具/审批画像）
- [ ] 多实例部署下的配对码/会话共享（外部存储）

---

## 11. 参考源平台能力对齐（迭代二）

> 依赖：迭代一全部完成 ｜ 覆盖：spec §8.1–§8.21、design 第三章 ｜ 完成状态：11.1–11.20 已完成
>
> 对齐基线：参考源 tag `dsh-v0.2.3-alpha.2`，升级指南覆盖 `v0.2.2-alpha.3` ~ `v0.2.3-alpha.1`（共 82 条）；不纳入项见 spec §8.13。
> 范围边界：不新增 Maven 模块、不新增数据库表；错误码改名为破坏性变更，须同步测试与文档；连接器按插件分期，不引入 Node CLI 运行时。

### 11.1 统一错误码与 HarnessError
- [x] 新增 `HarnessError`（domain 公共异常包），字段 `code`/`name`/`message`/`cause`；验收：`instanceof HarnessError` 可用于统一捕获 —— `dsh-java-domain/.../common/error/HarnessError.java`
- [x] 附件、CUA、浏览器、Worktree、Ralph、终端、OAR、Liveshare、Hook 错误类改为继承 `HarnessError`；验收：既有 `name` 与码不变（OAR/Liveshare 项目内无对应实现，已跳过）
- [x] `ToolPipeline` / `ToolCallExecutor` 写入 `tool/result.error={name, code}`，禁止回落 `UNKNOWN`；验收：失败结果必含 `error.code`
- [x] Shell/FS：无沙箱执行器与 `run_in_background` 不支持统一报 `INVALID_ARGS`，消息以 `invalid arguments: ` 开头；验收：对应单测（当前入参无这两项，已统一缺失参数/非法参数为 `ToolArgsError`/`INVALID_ARGS`）
- [x] 移除 `JOB_BACKGROUND_DISABLED`，`IMAGE_UNSUPPORTED_MODEL`→`MODEL_DOES_NOT_SUPPORT_IMAGES`；验收：全仓库无旧码
- [x] Web fetch 增加 6 个 `WEB_FETCH_*` 码与兜底码，消息含故障类别/系统码/底层错误；验收：DNS、拒绝、超时、证书用例
- [x] `TerminalBackendCleanupError` 继承 `TerminalError`，`code=CLEANUP_FAILED`；启动失败与清理失败通过 `cleanupFailure` + `suppressed` 聚合（`isAggregate()` 判定），不依赖 Java 不存在的 `AggregateError`
- [x] 更新 `ToolErrorCodes` 与各子系统码表，补充旧→新映射到 `CHANGELOG.md`；验收：文档可追溯 —— 总表见 [error-codes.md](error-codes.md)

### 11.2 Subagent 名称寻址
- [x] `subagent/catalog` 支持同级唯一名称与路径名（根 `lead`），载荷升至第 3 版；验收：`reviewer/checker` 可解析 —— Java 无持久化 catalog，改为 `InProcessSubagentDriver` 名称索引 + `listAgents` 投影，路径名按字面键支持
- [x] `SendMessageTool` / `InterruptTool` 参数由 `agent_id` 改为 `agent`（名称或 Session id）；验收：仅传 `agent_id` 参数校验失败
- [x] `SubagentTool` / fork / oar 接受可选 `name`，返回 `started subagent <name>` 与 `{kind:'activation', name}`；验收：结果结构一致
- [x] `ListAgentsTool` 返回 `{name, status, description?}`，支持 `corrupt`/`unsupported`/`unavailable`；验收：文本行格式一致（进程内仅有 running/done，其余状态保留给外部驱动）
- [x] Team 任务投影以 `owner` 取代 `ownerId`，无主任务用 `unowned` 查询；当前代码无 Team 任务投影，跳过
- [x] 内置组合默认 `interrupt=false`，不注册 `interrupt_agent`；验收：工具目录断言（当前 `AgentToolCatalog` 未注册 interrupt 工具）

### 11.3 Office 能力工具化
- [x] 新增 `office_render` / `office_convert` / `office_recalculate` 三个工具并注册到工具目录；验收：加载技能后目录含三工具 —— `dsh-java-domain/.../tool/office/`
- [x] 收敛 `OfficeSkill`：删除三个旧技能动作，保留 `office` 路由与 docx/pptx/xlsx 编写技能；验收：技能清单
- [x] 删除 `skill-office.node/cli` 与 `DSH_BUNDLED_OFFICE_*` 配置/环境变量，新增 `pythonEnvironment`；验收：配置读取无旧键（当前仓库无旧键，已新增 `dsh.office.python-environment`）
- [x] 新增 Office Kit 输出治理配置（`outputDirectory`/`outputRetentionMs`/`maxOutputStoreBytes`），复用转换端口与 `OfficeKitOutputStore`；验收：过期清理单测
- [x] LibreOffice Kit 缺失时返回 `OfficeKitError(unavailable)` 且不阻断启动；验收：缺 Kit 启动 + 转换失败用例
- [x] Office 技能资源改为内置 assets 加载；验收：无外部目录依赖

### 11.4 Web 计费与凭据
- [x] 新增 `searchBilling`/`fetchBilling`（`auto|account|api-key`）；验收：三模式解析用例 —— `dsh-java-domain/.../tool/web/`
- [x] 凭据改为有序来源列表并移除顶层 `baseURL`；验收：顺序优先级用例
- [x] 取消异常映射 `WEB_ABORTED`，凭据错误保留 `cause`；验收：取消与凭据异常用例

### 11.5 Worktree 归档清理
- [x] 创建成功后写 `plugin:worktree/created` 会话事件；验收：事件可重放 —— `WorktreeTool.create(repo, branch, sessionId)` 写入 `Generic` 事件，含 repo/worktree/branch
- [x] 归档时按条件执行 `git worktree remove`（非 force），失败仅提示；验收：脏树/子模块/锁/分离 HEAD 均保留 —— `WorktreeArchiveCleaner` + `JGitWorktree`，当前仓库无归档入口，供后续归档流程调用
- [x] `retainOnArchive` 默认 false，分支与提交保留；验收：清理后分支仍存在（移除仅执行 `git worktree remove`，不删除分支）

### 11.6 运行时上下文提醒
- [x] `runtime-context` 消息包 `<system-reminder>`，首份/后续快照文案区分；验收：快照文本断言 —— `RuntimeContextSnapshot`
- [x] 清空提醒输出 `form=snapshot`、`sections=[]`；验收：清空用例
- [x] 旧快照缺少 `sections` 时仅刷新一次且不改写前缀；验收：二次请求不重复刷新 —— `seedLegacy` + `refreshCount`，当前无持久化快照，由调用方注入旧前缀

### 11.7 Plugin Bundle 更新
- [x] `BundleInfo.updatable` 改为可选 `update`（`registry|git`）；验收：listBundles 契约 —— `PluginBundleInfo` + `PluginBundleUpdateService`（当前仓库无 bundle 列表端点，模型与判定已就绪）
- [x] `updateBundle` 支持 Git 较新提交；新增 `unexpected-package`；验收：预期不符安装失败 —— Git 仅固定完整提交可更新；`PluginRegistryService.installPlugin(..., expectedPluginId, expectedVersion)` 命中其他包/版本抛 `unexpected-package`

### 11.8 DeepSeek 文件上传超时
- [x] `filesApiTimeoutMs` 默认改 20s，连接/请求超时分离；验收：配置默认值 —— `DeepSeekAdapterOptions`（20s + 5s 连接超时）
- [x] 超时后内联图片降级与 2 倍退避、成功复位；验收：连续超时用例 —— `DeepSeekImageAttachmentPolicy`；当前仓库无 Files API 上传器，策略以上传器注入方式可用，适配器默认内联

### 11.9 WebServer 默认鉴权
- [x] 路由注册增加 `auth` 维度，默认 `required`，公开路由显式 `anonymous`；验收：未声明路由被拒 —— 新增 `@RouteAuth`/`RouteAuthResolver`，`ApiKeyAuthInterceptor` 对无注解路由按 `required` 拒绝，`AgentCardController` 等 `.well-known` 路由显式 `ANONYMOUS`；`harness.webserver.auth-default=anonymous` 可回滚
- [x] Token 校验/登录交换抽为认证提供方，Cookie 可认证页面与 API；验收：Cookie 用例 —— `WebAuthProvider`/`DefaultWebAuthProvider`，按 `X-API-Key` → `Bearer` → `dsh_session` Cookie 顺序校验，`issueSessionToken` 供登录交换
- [x] 连接监听器活动时匿名路由执行来源检查；验收：跨源拒绝用例 —— `ConnectionOriginGuard`（`harness.auth.connection-listener.enabled`），同源/白名单放行，跨源 403

### 11.10 会话状态与事件写入
- [x] 扩展 `SessionStatus` 表达阅读态/完成未读态，新增 `completionUnread`/`completionFailed`；验收：投影字段 —— 新增 `domain/session/status/model/valobj/SessionStatus`（running/completionUnread/completionFailed）与 `SessionReadState`；`ConversationSessionSummaryVO` 增加两个投影字段并由 `ConversationQueryService` 填充
- [x] 宿主共享阅读记录与 `mark(sessionId, unread)`（`readingReady` 前拒绝）；验收：标记用例 —— `SessionReadingRegistry`（内存共享记录、`turn/end` 更新完成位置、`mark`/`acknowledge`/`read`，`readingReady` 前抛 `IllegalStateException`；首次会话查询惰性建立基线）
- [x] `append` 增加事件数组重载，按序批量写入且可重放；验收：批量重放用例 —— `ISessionEventLogService.append(String, List<SessionEvent>)` 默认委托 `appendBatch`，单事件签名保留；`SessionEventLogService` 写入后记录 `turn/end` 阅读状态

### 11.11 CUA / PTC 部分对齐
- [x] 未组合 preset 的 Agent 注册 `cua_run_code`，按 `agentPreset` 选择 `cua`/`cua_run_code`；验收：工具目录用例 —— `AgentToolCatalog` 依据 `SessionHeader.agentPreset` 选择：`cua` → `CuaTool`，其余/未组合 → `CuaRunCodeTool`；新增 `CuaTool`/`CuaRunCodeTool`
- [x] 专用运行时缺失时创建 Agent 失败并返回可读原因；验收：错误消息 —— preset 要求 CUA 但 `IComputerUsePort` 缺失时抛 `IllegalStateException("CUA program tool is unavailable")`
- [x] PTC 程序输出改为 `inspect(...)` 显式选择，图片经 `inspect` 附加；验收：输出顺序与图片用例 —— 新增 `PtcProgramOutput`：按 inspect 调用顺序收集，字符串直出、图片转 `ImageBlock`、其他值对象格式；不自动收集返回值/末尾表达式

### 11.12 测试、文档与发布治理
- [x] 各能力域补充单元测试与 ArchUnit 回归；验收：`mvn test` 全绿 —— 新增/更新 11.1–11.11 相关测试类，`mvn test` exit 0，既有 ArchUnit 规则随全量回归通过
- [x] 更新 `CHANGELOG.md` 0.2.3 迭代二分节（新增/更新/破坏性变更）；验收：旧→新错误码映射表 —— 迭代二补「变更分类」三段，旧→新错误码映射表位于同节
- [x] 更新 `llms.txt`、`AGENTS.md` 与 `docs/md/` 相关说明；验收：版本与命令一致 —— 版本号与 jar 名统一为 `0.2.3`，spec 路径改为 `docs/spec/0.2.3/`，`ai-discoverability.md` 同步 release bundle 版本
- [x] 端到端冒烟：Office 转换、Worktree 归档、WebServer 鉴权、Subagent 名称链路；验收：冒烟记录归档 —— 记录见 [smoke.md](smoke.md)，四条链路与 `mvn test` 均通过

### 11.13 会话 fork、压缩摘要与冷会话查询
- [x] fork 创建要求显式父前缀，父前缀持久化后再创建共享 fork；验收：缺参数失败、顺序用例 —— `cases/session/*`
- [x] 压缩摘要改为从会话事件投影读取；验收：投影结果与既有摘要一致 —— `domain/session/*`
- [x] 移除 `inspectSession`，冷会话读取改走只读句柄；验收：旧接口调用编译失败
- [x] SDK prompt 未设置时区时上报 `ZoneId.systemDefault()`；验收：请求体含时区
- [x] 补充单测与文档；验收：`mvn test` 通过

### 11.14 Subagent 运行期契约
- [x] 子 agent 初始委派任务记录为父 agent 的 `agent-message`；验收：来源字段断言
- [x] 模型选择每次调用读取宿主当前设置；验收：切换设置后立即生效
- [x] `list_agents` 返回 `{capacity, agents}`；验收：工具 schema 与结果断言（同步 spec §8.2、§8.15）
- [x] 更新工具 schema、快照与文档；验收：快照回归通过

### 11.15 工作树与写入边界收敛
- [x] 移除 `create_worktree` 工具，`enter_worktree` 支持创建或进入同名工作树；验收：工具目录与行为用例
- [x] 移除 `working_directory` 工具与子代理 `cwd` 参数；验收：工具目录无旧工具
- [x] `workspace-write` 边界跟随 Session 工作目录并拒绝写入 `.git`；验收：worktree 内写 `.git` 被拒
- [x] 归档清理取消只读策略豁免，Git 命令不经沙箱；验收：只读创建者目录被清理（同步 spec §8.5 第 5 条）
- [x] 更新提示词与文档；验收：提示词无旧工具引用

### 11.16 PTC / MCP 输入输出
- [x] 展示输出超限截断且程序继续执行；验收：截断提示与退出码用例
- [x] MCP 图片块以附件引用 + `Image` 值传给程序；验收：程序收到引用而非 base64
- [x] 未修改的工具对象/数组结果使用工具渲染文本；验收：渲染一致性用例

### 11.17 DeepSeek 认证、拒答与图片策略
- [x] 认证抽为 `DeepSeekAuthProvider`，路由不再直接持有凭据；验收：缺提供方启动失败并给出配置指引
- [x] 拒答码改为 `CONTENT_REFUSED`；验收：流式拒答用例
- [x] 文件上传策略解析后显式传入文件存储；验收：无隐式默认
- [x] 图片编码硬上限与 `imageTargetBytes` 软目标；验收：超限图片用例
- [x] 更新 `error-codes.md` 与 CHANGELOG 映射；验收：旧→新码可追溯

### 11.18 技能、推荐与 Action 卡片
- [x] Office 合并为单技能，格式指导按需读取；验收：技能清单仅 `office`（同步 spec §8.3 第 2 条）
- [x] 基础 bundle 增加 `upstream-skills` 行；验收：`skill` 行禁用时该行等待并产生启动警告
- [x] 移除推荐 bundle 与 `propose_action` 工具；验收：工具/包清单无旧项
- [x] 支持回答内 `action-card` 代码块生成卡片；验收：解析与可操作用例
- [x] 新旧轮次卡片互不影响；验收：连续两轮卡片均可操作

### 11.19 系统提示与上下文章节
- [x] 章节按来源累计合并，不覆盖他来源；验收：多来源合并用例
- [x] 识别各来源通知并与 `runtime-context` 快照协同；验收：快照与章节一致

### 11.20 连接器插件与账号套件（评估优先）
- [x] 输出连接器评估表（wecom/dingtalk/notion/linear/tencent-docs/tencent-meeting）；验收：逐项“接入/不接入/替代实现”结论 —— 见 design §3.13.8
- [x] 新增连接器账号注册表与统一连接/断开/刷新服务；验收：状态与诊断用例
- [x] MCP 账号支持 OAuth 授权、被动刷新、再次授权与登出；验收：授权流与过期刷新用例
- [x] 已评估连接器按插件模式接入（REST 优先，不引入 Node CLI）；验收：插件页可连接/断开，令牌不落前端
- [x] 品牌图标、功能预览与多语言文案；验收：插件页展示项齐全
- [x] `upstream-skills` 固定 commit 元数据记录；验收：不引入下载运行器
- [x] 补充单测、文档与 CHANGELOG；验收：`mvn test` 通过
