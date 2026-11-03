# DeepSeek Harness Java v0.2.2 编码任务清单

> 版本：`v0.2.2` | 基线：v0.2.1 | 架构：DDD 六边形架构（Maven 多模块 / Spring Boot 3.5.16 / Java 17）
>
> 本清单将 `docs/spec/0.2.2/spec.md` 的 **§5.1–§5.9 九大能力领域**、**§7 平台能力对齐**与 **§6 数据约束（含 §6.8/§6.9）**、`docs/spec/0.2.2/design.md` 的全部分层落位、接口清单、数值参数转化为可执行、可验收的编码任务。
>
> 主线为**飞书（Feishu / Lark）Bot WebSocket 长连接接入与 IM 会话渠道打通**：
> - **接入方式**：飞书官方 SDK WebSocket 长连接（`com.lark.oapi.ws.Client` + `EventDispatcher`），**无 HTTP webhook**；出站 IM v1 create/update（文本、就地编辑、Markdown 卡片）；入站事件 `im.message.receive_v1`。
> - **新增依赖（仅两组）**：`com.larksuite.oapi:oapi-sdk:2.8.3`、`com.larksuite.oapi:larksuite-oapi-shaded-protobuf:2.4.6`（传递引入 jackson 2.17.2、gson 2.10.1）。
> - **分层落位**：domain `domain.bot`（聚合/值对象/去重器/端口/仓储）、api（`IBotApi` + 4 DTO）、case（`cases.bot` 三用例）、infrastructure（`adapter/feishu` 三适配器 + `FeishuBotRepository`/`IFeishuBotDao`/`FeishuBotPO`）、trigger（`BotController`）、app（条件装配 + 建表迁移 + 控制台面板）。**全部落在既有模块内，不新增 Maven 模块。**
> - **数据模型**：新增单表 `feishu_bot`（11 列，双库 MySQL8 + H2 `MODE=MySQL` 兼容且幂等），`schema.sql` 表数 30 → 31。
>
> **范围边界（强制）**：
> 1. **不新增 Maven 模块**，依赖方向保持"基础设施 → 领域"，不反向。
> 2. **不改既有契约**：不改 `domain/channel` 与 `harness_session.channel_code`（LLM provider 语义）、不改既有 GitHub Webhook 与 Agent 入口契约、不改 `IAgentUseCase`/`ICredentialProviderPort` 公开签名。
> 3. **不引入 Flyway**：`db/migration/*.sql` 为只读对照，启动迁移仍由 Spring SQL Init（`schema.sql`）+ `SchemaMigrationRunner` 承担。
> 4. **命名隔离**：飞书 IM 渠道使用独立命名 `feishu`/`im` + 独立表 `feishu_bot`，禁止复用 LLM `channel` 语义与数据对象。
>
> **执行顺序与依赖主线**：`1 依赖与版本治理 → 2 数据模型与迁移 → 3 domain（可与 4 api 并行）→ 已含 `3`+`2` 的 `5 infrastructure 适配器` → 已含 `3`+`4` 的 `6 case 用例` → 已含 `4` 的 `7 trigger 接口` → 已含 `5`+`6` 的 `8 app 装配与配置` → `9 凭证管理`（横切，随 6 落地）→ `10 控制台界面`（依赖 7）→ `11 测试` → `12 文档与校验` → `13 端到端验证与验收`。被依赖者在前，可并行者已标注。
>
> **任务统计**：共 **13 个任务组**、**29 个子任务节**、**62 条可勾选条目**（必做 61 条 ｜ 可选 1 条：12.1 开发说明归档任务，本阶段另行处理）。
>
> **完成状态图例**：`[x]` 已完成 ｜ `[ ]` 待办/后续优化

---

## 1. 依赖与版本治理

> 依赖：无（本组为基座，被 5/11/13 依赖） ｜ 覆盖：§5.8.1/§5.8.4/§5.8.5、§6.7、§4.5.6/§4.5.7、design §2.9/§2.10 ｜ 完成状态：待办

### 1.1 引入飞书 SDK 依赖并锁定版本
- [x] 在根 pom `<dependencyManagement>` 统一声明 `com.larksuite.oapi:oapi-sdk:2.8.3` 与 `com.larksuite.oapi:larksuite-oapi-shaded-protobuf:2.4.6`，并新增版本属性（如 `<larksuite.oapi.version>2.8.3</larksuite.oapi.version>`）；验收：两坐标可从根 pom 单一来源解析；依赖：无 —— `pom.xml`
- [x] 在 `dsh-java-infrastructure` 模块引入上述两组依赖（**不带 `<version>`**）作为适配器运行期依赖；验收：`dsh-java-domain`/`dsh-java-case` 对 `com.lark.oapi` 零依赖；依赖：1.1 —— `dsh-java-infrastructure/pom.xml`
- [x] 在根 pom `<dependencyManagement>` 显式覆盖飞书 SDK 传递引入的 `jackson`，**以 Spring Boot 3.5.16 BOM 为准**（避免 2.17.2 与 BOM 管理版本冲突），`gson` 保留但不外泄；验收：`mvn dependency:tree` 无 Jackson 版本分裂；依赖：1.1 —— `pom.xml`
- [x] 校验依赖新增范围：新增项仅为 `oapi-sdk` 与 `shaded-protobuf`（含其传递依赖 jackson 2.17.2、gson 2.10.1），无其他第三方新增；验收：`mvn dependency:tree -pl dsh-java-infrastructure` 与 spec §6.7.3 一致 —— `mvn dependency:tree -pl dsh-java-infrastructure`

### 1.2 统一版本标识为 0.2.2
- [x] 核对根 pom、7 个模块 pom、2 个插件 pom 版本均为 `0.2.2`（含 `dsh-java-app/pom.xml` parent 版本与 line 62 `sample-tools-plugin` 版本）；验收：13 个文件版本标识统一；依赖：无 —— `pom.xml`、`dsh-java-{app,trigger,api,case,domain,infrastructure,types}/pom.xml`、`plugins/{sample-tools-plugin,dsh-plugin-archetype}/pom.xml`
- [x] 更新 `CHANGELOG.md`，新增 `0.2.2` 分模块分节（app/trigger/api/case/domain/infrastructure/types，types 无变更）；验收：存在 0.2.2 分节且按 7 模块记录；依赖：无 —— `CHANGELOG.md`
- [x] 更新 `docs/dev-ops/docker-compose-app.yml` 镜像 tag 与注释为 `0.2.2`；验收：文件中不再残留 `0.2.1` 镜像标识；依赖：无 —— `docs/dev-ops/docker-compose-app.yml`
- [x] 更新 `docs/dev-ops/start-app.sh` 的 `IMAGE` 变量为 `0.2.2`；验收：`IMAGE=...:0.2.2`；依赖：无 —— `docs/dev-ops/start-app.sh`
- [x] 核对不新增 Maven 模块：根 pom `<modules>` 列表与 v0.2.1 一致；验收：模块数不变（§5.8.4）；依赖：无 —— `pom.xml`

---

## 2. 数据模型与迁移

> 依赖：1（依赖版本基座） ｜ 覆盖：§6.1/§6.2、§5.8.2/§5.8.3、§4.2.7/§4.5.5、design §2.3 ｜ 完成状态：待办

### 2.1 新增 feishu_bot 建表脚本（双库幂等、三处口径一致）
- [x] 在 `schema.sql` 追加 `CREATE TABLE IF NOT EXISTS feishu_bot`（11 列：`bot_id` PK、`bot_name`、`app_id`、`secret_ref`、`enabled TINYINT DEFAULT 0`、`agent_base_id`、`channel_code`、`cwd`、`group_require_mention TINYINT DEFAULT 1`、`created_at`/`updated_at TIMESTAMP(3)`，含中文列注释与表注释，**仅主键无辅助索引**，不显式声明字符集）；验收：表数 30 → 31，MySQL8 与 H2 `MODE=MySQL` 均可执行；依赖：无 —— `dsh-java-app/src/main/resources/schema.sql`
- [x] 新增迁移文件 `V20261102__feishu_bot.sql`，与 `schema.sql` 同结构（`CREATE TABLE IF NOT EXISTS`，幂等），遵循 `V<yyyymmdd>__<name>.sql` 命名约定；验收：被 `check-sql-parity.sh` 识别为 migration 表集子集；依赖：无 —— `dsh-java-app/src/main/resources/db/migration/V20261102__feishu_bot.sql`
- [x] 在 MySQL dump 追加同结构 `feishu_bot` 建表；验收：schema 与 dump 表集合差为 0；依赖：无 —— `docs/dev-ops/mysql/sql/dsh_java.sql`
- [x] 更新 `README.md` 与 `README.en.md` 表数声明 30 → 31；验收：README 声明表数 == `schema.sql` 表数 == 31（§1.1.2）；依赖：2.1 —— `README.md`、`README.en.md`

### 2.2 实现持久化对象与 DAO
- [x] 实现 `FeishuBotPO`（record，11 字段；`enabled`/`groupRequireMention` 为 `Integer`，`createdAt`/`updatedAt` 为 `Instant`）；验收：字段与 `feishu_bot` 列一一对应（design §1.1.5.5 映射表）；依赖：2.1 —— `dsh-java-infrastructure/.../dao/po/FeishuBotPO.java`
- [x] 实现 `IFeishuBotDao`（`@Mapper` 注解 SQL）：`save(FeishuBotPO)`（upsert）、`findByBotId(String)`、`findAll()`、`deleteByBotId(String)`；验收：CRUD 在 H2 `MODE=MySQL` 下通过；依赖：2.2 —— `dsh-java-infrastructure/.../dao/IFeishuBotDao.java`

---

## 3. domain 模型与端口

> 依赖：无（纯领域，可与 4 并行） ｜ 覆盖：§5.1.1、§5.2.3、§5.4、§5.5.1、§6.1/§6.3/§6.4/§6.5、design §2.2.2/§2.4 ｜ 完成状态：待办

### 3.1 实现 FeishuBotAggregate 聚合根
- [x] 实现 `FeishuBotAggregate`（13 字段：`botId`/`botName`/`appId`/`secretRef`/`enabled`/`agentBaseId`/`channelCode`/`cwd`/`groupRequireMention` + 运行态 `connectionState`/`lastError` + `createdAt`/`updatedAt`；构造**不含**运行态），提供 `deriveAgentId(chatId)`、`shouldHandle(inbound)`、`rename`、`updateConfig`、`enable`/`disable`、`onConnecting`/`onConnected`/`onError`/`onStopped`、getters + `setGroupRequireMention`/`setUpdatedAt`；验收：字段/方法签名与 design §1.1.5.4 逐项一致；依赖：无 —— `dsh-java-domain/src/main/java/net/linlan/dsh/domain/bot/model/aggregate/FeishuBotAggregate.java`
- [x] 实现派生与判定语义：`deriveAgentId` 形如 `<agentBaseId>-fs-<chatId末16位>`，`chatId` 不可用时回退 `-fs-anon`；`shouldHandle` 单聊（p2p）恒处理、群聊按 `groupRequireMention` 与 @ 判定；验收：同 Bot 不同 `chatId` 派生出不同 `agentId`（spec §5.5.1）；依赖：3.1 —— 同上

### 3.2 实现值对象
- [x] 实现 `FeishuInboundMessage`（record，8 字段 `botId`/`messageId`/`chatId`/`chatType`/`messageType`/`text`/`senderOpenId`/`senderName` + `isP2P()`/`hasText()`）；验收：判定能力可用（§6.3.9）；依赖：无 —— `dsh-java-domain/.../domain/bot/model/valobj/FeishuInboundMessage.java`
- [x] 实现 `FeishuConnectionState`（enum `IDLE`/`CONNECTING`/`CONNECTED`/`ERROR` + `isOnline()`，仅 `CONNECTED` 为真）；验收：默认无连接为 `IDLE`（§6.4）；依赖：无 —— `dsh-java-domain/.../domain/bot/model/valobj/FeishuConnectionState.java`
- [x] 实现 `FeishuPairingBeginVO`（record：`url`/`userCode`/`deviceCode`/`expireInSec:long`/`intervalSec:int`，注意 `intervalSec` 为 `int`）；验收：字段类型与 design §1.1.5.4 一致；依赖：无 —— `dsh-java-domain/.../domain/bot/model/valobj/FeishuPairingBeginVO.java`
- [x] 实现 `FeishuPairingPollVO`（record：`clientId`/`clientSecret`/`openId`/`tenantBrand`/`error`/`errorDescription` + `succeeded()`，当且仅当 `clientId`/`clientSecret` 非空为真）；验收：`succeeded()` 语义符合 §6.5.10；依赖：无 —— `dsh-java-domain/.../domain/bot/model/valobj/FeishuPairingPollVO.java`

### 3.3 实现事件去重器
- [x] 实现 `FeishuEventDeduplicator`（`Set<String> seen` + `ArrayDeque<String> order`，`MAX_SIZE = 2000` **固定无参构造**，`isDuplicate(String)` 为 `public synchronized`，重复返回 `true` 并入队，超容淘汰最旧）；验收：容量有界、线程安全、重复投递仅首次处理（§4.1.1/§4.2.3）；依赖：无 —— `dsh-java-domain/.../domain/bot/service/FeishuEventDeduplicator.java`

### 3.4 定义出站端口与仓储
- [x] 定义 `IFeishuBotRepository`（`save`/`findByBotId`/`findAll`/`deleteByBotId`，`save` 为 upsert）；验收：4 方法签名与 design §2.2.2 一致；依赖：3.1 —— `dsh-java-domain/.../domain/bot/adapter/repository/IFeishuBotRepository.java`
- [x] 定义 `IFeishuBotConnectionPort`（`start`/`stop`/`isConnected`/`stateOf`/`lastErrorOf`，`stateOf` 缺省返回 `IDLE`）；验收：5 方法签名一致；依赖：3.1 —— `dsh-java-domain/.../domain/bot/adapter/port/IFeishuBotConnectionPort.java`
- [x] 定义 `IFeishuMessagingPort`（`sendText(appId,chatId,text):String`/`updateText(appId,messageId,text):boolean`/`sendMarkdownCard(appId,chatId,markdown):String`）；验收：3 方法签名一致；依赖：无 —— `dsh-java-domain/.../domain/bot/adapter/port/IFeishuMessagingPort.java`
- [x] 定义 `IFeishuPairingPort`（`begin(String,String):FeishuPairingBeginVO`/`poll(String,String):FeishuPairingPollVO`/`larkBaseUrl():String`，`begin` 为 **2 参**）；验收：3 方法签名一致；依赖：3.2 —— `dsh-java-domain/.../domain/bot/adapter/port/IFeishuPairingPort.java`
- [x] 定义 `IFeishuInboundHandler`（`handle(FeishuBotAggregate,FeishuInboundMessage)`）；验收：1 方法签名一致；依赖：3.1/3.2 —— `dsh-java-domain/.../domain/bot/adapter/port/IFeishuInboundHandler.java`

---

## 4. api 契约

> 依赖：无（可与 3 并行） ｜ 覆盖：§5.1、design §1.1.5.2/§2.2.2（2） ｜ 完成状态：待办

### 4.1 定义 IBotApi 与 4 个 DTO
- [x] 定义 `IBotApi`（10 方法：`startPairing`/`pairingStatus`/`cancelPairing`/`listBots`/`saveBot`/**`updateBot`**/`deleteBot`/`enableBot`/`disableBot`/`testBot`，均返回 `Response<T>`）；验收：`updateBot(String,FeishuBotSaveDTO)` 为本项目唯一契约扩展（§1.1.5.2 差异项）；依赖：无 —— `dsh-java-api/src/main/java/net/linlan/dsh/api/IBotApi.java`
- [x] 实现 `FeishuBotDTO`（record，13 字段：`botId`/`botName`/`appId`/`secretRef`/`secretConfigured`/`enabled`/`agentBaseId`/`channelCode`/`cwd`/`groupRequireMention`/`connectionState`/`lastError`/`updatedAt`；**不含 appSecret**）；验收：字段顺序类型与 design 表一致；依赖：无 —— `dsh-java-api/.../api/dto/FeishuBotDTO.java`
- [x] 实现 `FeishuBotSaveDTO`（record，8 字段：`botId`/`botName`/`appId`/`appSecret`/`agentBaseId`/`channelCode`/`cwd`/`groupRequireMention:Boolean`（可空，缺省不覆盖））；验收：`appSecret` 仅出现在入参；依赖：无 —— `dsh-java-api/.../api/dto/FeishuBotSaveDTO.java`
- [x] 实现 `FeishuPairingDTO`（record，8 字段：`pairingId`/`status`/`url`/`userCode`/`expireIn:Long`/`botId`/`appId`/`error`）；验收：字段与 design 表一致；依赖：无 —— `dsh-java-api/.../api/dto/FeishuPairingDTO.java`
- [x] 实现 `FeishuPairingStartDTO`（record，2 字段：`botName`/`agentBaseId`）；验收：字段与 design 表一致；依赖：无 —— `dsh-java-api/.../api/dto/FeishuPairingStartDTO.java`

---

## 5. infrastructure 适配器

> 依赖：1（SDK 依赖）、2（PO/DAO）、3（domain 端口） ｜ 覆盖：§5.2、§5.6、§5.3（客户端侧）、§5.1（持久化侧）、design §1.1.5.5/§2.6/§2.7 ｜ 完成状态：待办

### 5.1 实现 FeishuMessagingClient（出站消息适配器）
- [x] 实现 `FeishuMessagingClient implements IFeishuMessagingPort`，按 `appId` 缓存 `com.lark.oapi.Client`；内置常量 `MAX_STREAM_TEXT=20000`、`MAX_CARD_MARKDOWN=28000` 作端口层兜底截断；`sendText`/`updateText`（就地编辑）/`sendMarkdownCard`；私有 `send`/`jsonText`/`client`；另提供 `register(String appId,String secretRef)`/`unregister(String appId)` 预热与清理缓存；凭证不可解析时记"凭据 <ref> 未配置 app_secret"并返回空；验收：sendText/sendMarkdownCard 返回 `messageId`（失败返回空）（§5.6）；依赖：1.1/3.4/9.1 —— `dsh-java-infrastructure/.../adapter/feishu/FeishuMessagingClient.java`

### 5.2 实现 FeishuPairingClient（设备授权适配器）
- [x] 实现 `FeishuPairingClient implements IFeishuPairingPort`（`java.net.http.HttpClient`）；常量 `ENDPOINT=/oauth/v1/app/registration`、`FEISHU_BASE=https://accounts.feishu.cn`、`LARK_BASE=https://accounts.larksuite.com`；`begin(name,desc)` 请求体含 `action=begin&archetype=PersonalAgent&auth_method=client_secret&request_user_info=open_id`，追加 `source=dsh-java`/`name`/`desc`，缺 `verification_uri_complete` 抛 `IllegalStateException`；`poll(baseUrl,deviceCode)` 走 `doPoll`；`larkBaseUrl()` 返回 `LARK_BASE`；私有 `post`/`enc`；验收：Feishu/Lark 使用各自账号域（§5.3.2）；依赖：3.2/3.4 —— `dsh-java-infrastructure/.../adapter/feishu/FeishuPairingClient.java`

### 5.3 实现 FeishuWsGateway（长连接网关）
- [x] 实现 `FeishuWsGateway implements IFeishuBotConnectionPort, DisposableBean`，持有 `feishuBotDao`/`inboundHandler`/`messagingClient`/`credentialPort`/`objectMapper`/`deduplicator`/`connections`/`activeBots`/`connectExecutor`（`newCachedThreadPool`，线程名 `feishu-ws-connector`，`setDaemon(true)`）/`wsThreadSeq(AtomicInteger)`；`autoStartEnabledBots()` 遍历 `findAll()` 中 `enabled==1` 逐 Bot 异步 `start` 且**逐 Bot 隔离异常**；`stop`/`isConnected`/`stateOf`（缺省 `IDLE`）/`lastErrorOf`；`toAggregate(FeishuBotPO)`；验收：单 Bot 失败不阻断应用启动（§4.2.6）；依赖：2.2/5.1/9.1 —— `dsh-java-infrastructure/.../adapter/feishu/FeishuWsGateway.java`
- [x] 实现建连与事件编排：`start` 前 `resolve(secretRef)` 非空且 `messagingClient.register(appId,secretRef)==true`（否则 `onError("Bot <id> 缺少 app_secret（凭据 <ref>）")`/「注册飞书应用失败」不建连）；`new Client.Builder(appId,secret).eventHandler(dispatcher).autoReconnect(Boolean.TRUE).build()`，`client.start()` 后 `awaitReady(15000L)` 成功 `onConnected()`；`onMessage` 编排：去重 → @ 判定 → `extractText`/`flattenPost`/`replaceMentions`/`senderLabel` → `inboundHandler.handle`；幂等：已在线重复 `start` 直接返回；重连**交由 SDK `autoReconnect`，不叠加自研退避**；`destroy()` 逐个 `Client.close()` → `connectExecutor.shutdownNow()`（超时 0s）；验收：状态机迁移与 §2.6 数值表一致；依赖：5.3 —— 同上

### 5.4 实现 FeishuBotRepository（持久化适配器）
- [x] 实现 `FeishuBotRepository implements IFeishuBotRepository`，纯委托 `IFeishuBotDao`；`toAggregate(FeishuBotPO)` 完成 `enabled`/`groupRequireMention` 的 `0/1 ↔ boolean` 转换，运行态 `connectionState`/`lastError` **不映射入库**；验收：PO↔聚合双向映射正确；依赖：2.2/3.4 —— `dsh-java-infrastructure/.../adapter/repository/FeishuBotRepository.java`

---

## 6. case 用例

> 依赖：3（domain）、4（api）、5（适配器）、9（凭证） ｜ 覆盖：§5.1、§5.3、§5.4、§5.5、§5.6、design §2.7 ｜ 完成状态：待办

### 6.1 实现 FeishuBotUseCase（Bot 生命周期用例）
- [x] 实现 `FeishuBotUseCase implements IBotApi`（`@Service`），依赖 `IFeishuBotRepository`/`IFeishuBotConnectionPort`/`ICredentialProviderPort`/`FeishuPairingUseCase`；字段 `BOT_ID_PATTERN=^[a-z0-9][a-z0-9-]{1,62}$`；实现 `listBots`/`saveBot`/`updateBot`/`deleteBot`/`enableBot`/`disableBot`/`testBot`，配对三方法委托 `pairingUseCase`；私有 `resolveBotId`（空值生成 `fbot-` + `Long.toHexString(now & 0xFFFFL)`、非空校验）、`normalize`（`trim().toLowerCase()`）、`secretConfigured`、`toDTO`；错误文案「新建 Bot 必须提供 appId」「Bot 不存在: <id>」「保存 Bot 失败：<原因>」「删除 Bot 失败：<原因>」；验收：校验/编辑不改 `botId`/密钥可空更新（§5.1）；依赖：3.4/4.1/9.1 —— `dsh-java-case/src/main/java/net/linlan/dsh/cases/bot/FeishuBotUseCase.java`
- [x] 实现联动语义：`enableBot` 触发建连（状态进入 `CONNECTING`，缺失 `app_secret` 时置 `ERROR` 并记录「尚未配置 app_secret，请先在编辑中填写」）、`disableBot` 断连回 `IDLE`、`deleteBot` 三联动（记录删除 + 连接断开 + 凭据 `unset`）、编辑未提供 `appSecret` 保留原 `secret_ref`；验收：§5.1.5/§5.1.3.1；依赖：6.1 —— 同上

### 6.2 实现 FeishuPairingUseCase（一键配对用例）
- [x] 实现 `FeishuPairingUseCase`（`@Service`）：`startPairing`/`pairingStatus`/`cancelPairing`，`sessions: Map<String,PairingSession>`，常量 `BOT_ID_PATTERN`、`MAX_NETWORK_ERRORS=20`；缺省 `botName=DeepSeek Bot`、`agentBaseId=feishu-agent`（`toLowerCase()`）、`begin` 的 `desc` 固定「手机上对话电脑端 Harness Agent，支持工具调用与流式回复」；内部类 `PairingSession`（守护线程，`setDaemon(true)`；字段 `pairingId`/`botName`/`agentBaseId`/`url`/`userCode`/`deviceCode`/`expireAt`/`intervalMs(volatile)`/`baseUrl(volatile)`/`status`/`error`/`createdBotId`/`appId`/`cancelled`，方法 `run`/`complete`/`deriveBotId`/`fail`/`cancel`/`toDTO`）；轮询语义：`authorization_pending` 继续、网络异常按 pending 累计至 20 失败、`slow_down→intervalMs=5000`、`access_denied` 立即失败、`expired_token` 置 `EXPIRED`、缺 `verification_uri_complete` 立即失败；成功后创建 Bot + 写密钥 + 启用长连接；验收：配对状态机（design §2.1.3.3）与 §5.3 全规则；依赖：3.2/3.4/5.2/5.3/6.1/9.1 —— `dsh-java-case/.../cases/bot/FeishuPairingUseCase.java`

### 6.3 实现 FeishuBotInboundHandler（入站桥接与流式回发）
- [x] 实现 `FeishuBotInboundHandler implements IFeishuInboundHandler`，依赖 `IAgentUseCase`/`IFeishuMessagingPort`；`botExecutors: Map<String,ExecutorService>` 每 Bot 单线程（`newSingleThreadExecutor`，线程名 `feishu-bot-<botId>`，daemon）；常量 `FLUSH_INTERVAL_MS=1500L`/`STREAM_PREVIEW_LIMIT=4000`/`FINAL_IN_PLACE_LIMIT=4000`/`CARD_LIMIT=28000`；内部类 `StreamReply`（`appId`/`chatId`/`placeholder`/`buffer`/`lastFlushAt`/`toolLine`/`finished`，方法 `onDelta`/`onToolCall`/`onFinish`/`text`/`finalizeWith`/`maybeFlush`/`flush`）；`handle` 提交至 `botExecutors`，`process` 桥接主体，`buildUserText`（群聊回填 `senderName`），`safeReplyError`；流程：`deriveAgentId` → 占位 `⏳ 正在思考…` → `AgentMessageRequestDTO(agentId, channelCode=null, cwd=bot.cwd, approvalMode=AUTO_APPROVE)` 调 `sendMessageStreaming` → 节流就地编辑 → 定稿（超 4000 走卡片截 28000 + 占位改"回复已生成"，卡片失败退化为占位截断 + "…（内容过长已截断）"）；兜底：空响应「模型没有返回内容」、异常「⚠️ 处理失败：<原因>」、并发忙碌提示「上一条消息还在处理中，请等它完成后再发～」；**飞书 IM 渠道码不写入 `channelCode`**；验收：§5.5 全规则 + §5.6 分片/降级；依赖：3.1/3.2/3.4/5.1 —— `dsh-java-case/.../cases/bot/FeishuBotInboundHandler.java`

---

## 7. trigger 接口

> 依赖：4（api 契约） ｜ 覆盖：§5.1、§4.3.3、design §2.2.2（3） ｜ 完成状态：待办

### 7.1 实现 BotController（REST 触发）
- [x] 实现 `BotController`（`@RestController @RequestMapping("/api/harness/bots")`，构造注入 `IBotApi`），10 端点：`GET /`（list）、`POST /pairing`、`GET /pairing/{pairingId}`、`DELETE /pairing/{pairingId}`、`POST /`（save）、`PUT /{botId}`（update→`updateBot`，不在控制器内重组 DTO）、`DELETE /{botId}`、`POST /{botId}/enable`、`POST /{botId}/disable`、`POST /{botId}/test`，全部委托 `IBotApi`；验收：路径/动词/委托与 design §2.2.2（3）一致，自动纳入既有 `ApiKeyAuthInterceptor`（`/**`）鉴权范围，无需新增鉴权配置；依赖：4.1 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/http/BotController.java`

---

## 8. app 装配与配置

> 依赖：5（适配器）、6（用例） ｜ 覆盖：§5.2.1、§4.5.1、§5.8、design §2.8 ｜ 完成状态：待办

### 8.1 实现 FeishuBeanConfig 条件装配与启动拉起
- [x] 实现 `FeishuBeanConfig`（`@Configuration(proxyBeanMethods=false)` + `@ConditionalOnProperty(name="dsh.feishu.enabled", havingValue="true", matchIfMissing=true)`），手工装配 Bean：`IFeishuBotRepository`→`FeishuBotRepository(dao)`、`FeishuEventDeduplicator`（**无参构造**，容量固定 2000）、`IFeishuMessagingPort`→`FeishuMessagingClient(credentialPort,objectMapper)`、`IFeishuPairingPort`→`FeishuPairingClient(objectMapper)`、`IFeishuInboundHandler`→`FeishuBotInboundHandler(agentUseCase,messagingPort)`、`IFeishuBotConnectionPort`→`FeishuWsGateway(dao,inboundHandler,messagingClient,credentialPort,objectMapper)`；验收：`dsh.feishu.enabled=false` 时不产出任何飞书 Bean 且应用上下文正常启动（§2.8 开关语义）；依赖：5.1/5.2/5.3/5.4/6.1/6.2/6.3 —— `dsh-java-infrastructure/.../config/FeishuBeanConfig.java`
- [x] 注册启动拉起钩子：`@Bean ApplicationListener<ApplicationReadyEvent>`（或复用 `FeishuWsGateway` 作为监听者）在就绪后调用 `autoStartEnabledBots()`，避免阻塞 Servlet 容器就绪；验收：启动完成且存在 `enabled` Bot 时逐个发起长连接（§5.2.1.1）；依赖：8.1/5.3 —— 同上

### 8.2 配置项落位（harness.yml）
- [x] 在 `harness.yml` 增加 `dsh.feishu.*` 配置项：`enabled`(true)、`dedup.max-size`(2000，文档化对齐)、`stream.flush-interval-ms`(1500)、`stream.preview-limit`(4000)、`stream.final-in-place-limit`(4000)、`stream.card-limit`(28000)、`messaging.max-text`(20000)、`messaging.max-card-markdown`(28000)、`ws.await-ready-ms`(15000)、`ws.auto-reconnect`(true)、`pairing.max-network-errors`(20)、`pairing.default-bot-name`(DeepSeek Bot)、`pairing.default-agent-base-id`(feishu-agent)；验收：默认值即参考源常量值（design §2.8 数值表）；依赖：8.1 —— `dsh-java-app/src/main/resources/harness.yml`

---

## 9. 凭证管理

> 依赖：无（复用既有凭据 seam；横切，随 6 落地） ｜ 覆盖：§5.7、§6.6、§4.3.1/§4.3.2/§4.3.4、design §2.5 ｜ 完成状态：待办

### 9.1 实现凭证引用派生与密钥读写
- [x] 实现 `secretRef` 派生规则：`"FEISHU_BOT_" + botId 转大写、非 [A-Z0-9_] 字符替换为 "_" + "_APP_SECRET"`（例 `fbot-abc123 → FEISHU_BOT_FBOT_ABC123_APP_SECRET`），经 `CredentialRef.of(ref)` 复用既有 seam；写入/解析/清除统一走 `ICredentialProviderPort.set/resolve/unset`；`secretConfigured` 由 `describe`/`resolve().isPresent()` 投影，**不回显密钥**；验收：派生结果满足 `CredentialRef` 正则 `^[A-Za-z_][A-Za-z0-9_]*$`（§5.7.1/§6.6）；依赖：3.1（落位于 `FeishuBotUseCase`/`FeishuPairingUseCase`）—— `dsh-java-case/.../cases/bot/FeishuBotUseCase.java`、`.../FeishuPairingUseCase.java`
- [x] 校验密钥不落库与最小权限：`app_secret` 仅经凭据 seam 读写（落 `<harness.credentials.home:${user.home}/.dsh>/.credentials.yaml`，文件权限 `rw-------`/0600），数据库仅保存 `secret_ref`，日志一律以 `secretRef` 代替明文；验收：DB/响应/日志均只出现 `secret_ref` 或 `secretConfigured`（§4.3.1/§4.3.2/§4.3.4）；依赖：9.1（复用 `LocalCredentialProviderPort`/`CredentialsYamlStore`）—— `dsh-java-infrastructure/.../adapter/credentials/`

---

## 10. 控制台飞书 Bot 管理界面

> 依赖：7（trigger 接口） ｜ 覆盖：§5.9、§5.8.8、design §1.1.2/§2.4 ｜ 完成状态：待办

### 10.1 实现飞书 Bot 管理面板（原生 HTML，无前端框架）
- [x] 在控制台外壳新增"飞书 Bot 管理"面板：一键扫码绑定（发起配对 + 展示扫码链接/用户码 + 轮询进展）、手动配置（录入 `appId`/`appSecret` 创建）、列表管理（状态列 + 新增/编辑/删除）、启停与测试入口；验收：面板可与 `/api/harness/bots/**` 联通，密钥仅显示"已配置/未配置"（§5.9.2/§5.9.5）；依赖：7.1 —— `dsh-java-app/src/main/resources/static/index.html`
- [x] 在 `app.js` 实现面板请求逻辑：复用既有鉴权头（`X-API-Key`/`Bearer`）与 `Response<T>` 结构，覆盖 list/pairing/begin-poll/save/update/delete/enable/disable/test；验收：接口失败时面板展示可读中文错误；依赖：10.1 —— `dsh-java-app/src/main/resources/static/app.js`
- [x] 追加面板样式，保持轻量原生实现（**不新增前端框架依赖**）；验收：构建无新增 npm/前端框架；依赖：10.1 —— `dsh-java-app/src/main/resources/static/app.css`

---

## 11. 测试

> 依赖：3/5/6（被测对象） ｜ 覆盖：design §2.11 测试策略 ｜ 完成状态：待办

### 11.1 domain 单元测试
- [x] 编写 `FeishuBotAggregateTest`（`deriveAgentId` 末 16 位 / `anon` 回退、`shouldHandle` 的 P2P 恒处理与群聊 @ 判定、状态迁移）、`FeishuConnectionStateTest`（`isOnline`）、`FeishuEventDeduplicatorTest`（有界淘汰与重复判定）、`FeishuInboundMessageTest`（`isP2P`/`hasText`）、`FeishuPairingVOTest`（`succeeded()`）；验收：覆盖 design §2.11 单元-领域全部关键用例；依赖：3 —— `dsh-java-domain/src/test/.../domain/bot/`

### 11.2 case 单元测试
- [x] 编写 `FeishuBotUseCaseTest`（缺 `appId`、非法 `botId`、不存在提示、edit 不改 `botId`、密钥可空更新、`secretRef` 派生规则与相对/绝对用例）、`FeishuPairingUseCaseTest`（`slow_down`/网络容错 20/过期/取消）、`FeishuBotInboundHandlerTest`（mock `IAgentUseCase` 驱动 sink，校验占位 → 1500ms 节流更新 → 定稿/卡片降级、并发忙碌提示、空响应与异常兜底）；验收：覆盖 design §2.11 单元-用例；依赖：6 —— `dsh-java-case/src/test/.../cases/bot/`

### 11.3 infrastructure 适配器测试（mock SDK，不访问真实飞书网络）
- [x] 编写 `FeishuMessagingClientTest`（mock `com.lark.oapi.Client` 校验 sendText/updateText/sendMarkdownCard 与截断阈值）、`FeishuPairingClientTest`（mock `HttpClient` 校验 `begin`/`poll` 与缺扫码链接异常）、`FeishuWsGatewayTest`（mock `P2MessageReceiveV1` 触发 `onMessage`：去重、@ 判定、text/post 抽取、非文本兜底、状态迁移与启动幂等）、`FeishuBotRepositoryTest`（PO↔聚合映射）；验收：覆盖 design §2.11 单元-适配器与长连接 mock；依赖：5 —— `dsh-java-infrastructure/src/test/.../adapter/feishu/`

> 补充：`dsh-java-infrastructure/pom.xml` 新增 `org.mockito:mockito-core`（**仅 test 作用域**，版本由 Spring Boot BOM 管理），用于 mock 飞书 SDK 与 `HttpClient`；不进入运行期依赖，运行期新增仍只有 `oapi-sdk` + `shaded-protobuf` 两组（§6.7）。另为测试可见性保留了三处包内 seam：`FeishuMessagingClient#client`、`FeishuWsGateway#onMessage`、`FeishuPairingClient#begin(name, desc, baseUrl)`。
>
> 修正：`FeishuPairingPollVO#succeeded()` 原仅判 `clientId` 非空，已按 §6.5.10 修正为 `clientId` 与 `clientSecret` 均非空才为真。

### 11.4 集成测试
- [x] 编写集成测试：① `feishu_bot` 建表在 H2 `MODE=MySQL` 与 MySQL8 幂等执行（重复执行不报错）；② `IFeishuBotDao` CRUD；③ 上下文冒烟（`dsh.feishu.enabled=true` 时飞书 Bean 装配成功 / `=false` 时上下文无飞书 Bean 且启动正常）；④ `scripts/check-sql-parity.sh` 三方表集合一致且 README 表数为 31；验收：覆盖 design §2.11 集成-双库/上下文冒烟/SQL 一致性；依赖：2/8/12 —— `dsh-java-infrastructure`、`dsh-java-app`、`scripts/check-sql-parity.sh`

---

## 12. 文档与校验

> 依赖：1/2（版本与迁移） ｜ 覆盖：§5.8.6、§5.8.7、design §2.10 ｜ 完成状态：待办

### 12.1 开发说明与四件套归档
- [x] （**可选，本阶段另行处理**）产出 `release-v0.2.2-development-notes.md`，记录飞书接入设计、依赖体积增量、已知限制；验收：与四件套同目录归档；依赖：全部实现完成 —— `docs/spec/0.2.2/release-v0.2.2-development-notes.md`
- [x] 归档四件套（`spec.md`/`design.md`/`tasks.md`/`release-v0.2.2-development-notes.md`）于 `docs/spec/0.2.2/`，与 `contract/`、`dsh-java-architecture.drawio` 同目录；验收：四件套齐备（§5.8.6）；依赖：12.1 —— `docs/spec/0.2.2/`

### 12.2 SQL 三方口径一致性校验
- [x] 运行 `scripts/check-sql-parity.sh`：校验 `schema.sql` 与 `docs/dev-ops/mysql/sql/dsh_java.sql` 表集合差为 0、`db/migration/*.sql ⊆` 两者并集、`README.md`/`README.en.md` 声明表数 == `schema.sql` 表数（== 31）；验收：脚本退出码为 0；依赖：2.1 —— `scripts/check-sql-parity.sh`

---

## 13. 端到端验证与验收

> 依赖：全部任务完成 ｜ 覆盖：§4.1/§4.2、design §2.11/§2.12 ｜ 完成状态：待办

### 13.1 端到端链路验证
- [x] 双库迁移执行：分别在全新库与既有库执行 `schema.sql`/迁移，确认 `feishu_bot` 建表成功且重复执行不报错（无 Flyway）；验收：MySQL8（实测 `dsh_java` 库 `feishu_bot` 已建、总表 31）与 H2 `MODE=MySQL`（`FeishuBotSchemaIntegrationTest` 双跑通过）均通过；依赖：2/11.4 —— `dsh-java-app/src/main/resources/`
- [x] 长连接 mock 测试：以 mock `P2MessageReceiveV1` + mock `IAgentUseCase` 贯通入站编排（去重 → @ 判定 → 抽取 → 处理），校验占位消息先行、异步不阻塞长连接读线程；验收：处理链路无阻塞（§4.1.2）；依赖：11.3/6.3 —— `dsh-java-infrastructure` / `dsh-java-case`
- [x] 流式回发分段验证：占位 → 1500ms 节流就地编辑（预览 4000）→ 长文走卡片（28000）分片 → 卡片失败降级为占位截断（"…（内容过长已截断）"）；验收：分段/降级阈值与 §2.7 数值表一致；依赖：6.3/5.1 —— `dsh-java-case` / `dsh-java-infrastructure`
- [x] 控制台面板联通冒烟：静态面板加载（`GET /bots.html` 200）、列表/手动新增编辑/删除/测试请求联通（实测：`POST /api/harness/bots` 生成 `fbot-321d` 与 `FEISHU_BOT_FBOT_321D_APP_SECRET`、`PUT` 编辑不改 `botId`、`POST /{id}/test` 返回 DTO、`DELETE` 成功；密钥仅回显 `secretConfigured`），一键配对需真实扫码未端到端；无前端框架依赖；验收：§5.9 全规则；依赖：10 —— `dsh-java-app/src/main/resources/static/`

### 13.2 架构约束与范围边界核对
- [x] 核对：① 不新增 Maven 模块（根 pom `<modules>` 不变）；② 依赖方向"基础设施 → 领域"无反向依赖；③ 不改既有 GitHub Webhook 与 Agent 入口契约、不改 `domain/channel`/`harness_session.channel_code` 语义；④ 不引入 Flyway；⑤ `feishu_bot` 新表不影响既有表结构与数据（spec §1.4/§4.5）；验收：逐项核对通过；依赖：全部任务完成 —— `pom.xml`、`dsh-java-domain`、`dsh-java-app/src/main/resources/schema.sql`

---

## 14. 参考源 0.2.2-alpha.2 升级项落地（Java 对齐）

> 来源：`refer_source/deepseek-harness/docs/upgrade-guide/v0.2.2-alpha.2/`
> 覆盖：上游六条升级项中适用的三条 —— `llm-request-purpose-declarations`、`schedule-agent-tools`、`schedule-remote-host-tasks`
> 完成状态：已完成 ｜ 对照：`design.md` 第三章、`spec.md` §7（§7.1/§7.2/§7.3）与 §6.8/§6.9

### 14.1 `purpose` 声明式校验（对应 `llm-request-purpose-declarations`）

> 覆盖：spec §7.1 / §6.9 ｜ design §3.1

- [x] 新增 `LlmRequestPurposes`（`domain/llm/model/valobj`）：共享 LLM 核心不内置用途键，`declare/require/isDeclared/declared` 校验；`GenerateOptions` 构造时对 `purpose` 调 `require`（`null` 透传，未声明/空白抛 `IllegalArgumentException`）；验收：`LlmRequestPurposesTest` 通过 —— `LlmRequestPurposes.java`、`GenerateOptions.java`
- [x] 生产方声明：`BasicCompactionEngine` 声明 `compaction`、`PlannerService` 声明 `collaboration-planning`（常量 `LlmRequestPurposes.COMPACTION` / `COLLABORATION_PLANNING`）；`DeepSeekAdapter` 改用常量判定 `x-dsh-compact` 头；验收：压缩/规划调用构造通过 —— `BasicCompactionEngine.java`、`PlannerService.java`、`DeepSeekAdapter.java`
- [x] 受影响测试声明 `"test"` 用途后通过（`LlmStreamWaterfallTest` / `LlmRetryInterceptorTest` / `LlmGenerationServiceTest`）；验收：`mvn test` 全绿

### 14.2 Schedule 工具组（对应 `schedule-agent-tools`）

> 覆盖：spec §7.2 ｜ design §3.2

- [x] 四个工具 `schedule_create`/`schedule_list`/`schedule_view`/`schedule_edit`（替代旧 `Create`/`ListAll`/`Delete`）；创建只收 `title/prompt/rrule/target`，缺参返回 `MISSING_PARAM`；`view` 不存在返回 `NOT_FOUND`；`edit` 统一走 `action=update|delete`；验收：`ScheduleToolsTest` 工具名/参数校验/查看/删除用例通过 —— `ScheduleTools.java`、`AgentToolCatalog.java`
- [x] 子 agent 拦截：新增 `SubagentSessions.isSubagent(sessionId)`（`child-` 前缀），`SpawnInProcessProvider`/`ForkInProcessProvider` 改用 `SubagentSessions.newSessionId()`；调度工具对子 agent 返回 `subagent_session`；验收：`ScheduleToolsTest.subagentBlocked` 通过 —— `SubagentSessions.java`

### 14.3 Schedule 宿主全域（对应 `schedule-remote-host-tasks`）

> 覆盖：spec §7.3 / §6.8 ｜ design §3.3

- [x] `ScheduleRecord` 改为宿主范围（`id`/`target`/`targetSessionId`/`sourceSessionId`/`nextAt` 等）；`IScheduleService`/`IScheduleRepository`/`InMemoryScheduleRepository` 全部按 `id` 寻址；验收：`ScheduleServiceTest.hostWideVisibility`（跨会话可见）通过 —— `ScheduleRecord.java`、`IScheduleService.java`、`InMemoryScheduleRepository.java`
- [x] `ScheduleTiming` 带类型（AFTER/AT/EVERY/DAILY/WEEKLY/RRULE）+ `RRule` 解析器（DTSTART/TZID、INTERVAL/BYDAY/BYHOUR/BYMINUTE，显式拒绝 COUNT/UNTIL/BYMONTH）；`ScheduleKind` 由 RRULE 的 FREQ 派生；验收：`RRuleTest` 通过 —— `ScheduleTiming.java`、`RRule.java`、`ScheduleKind.java`
- [x] `edit` 支持乐观并发（`expectedUpdatedAt` 不一致抛 `ScheduleConflictException`，码 `schedule_conflict`）；`drive(now)` 推进重复任务 `nextAt`、一次性任务 `ended=true`；验收：`ScheduleServiceTest.editConflict` / `driveFiresDueTasks` 通过 —— `ScheduleService.java`