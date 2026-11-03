# DeepSeek Harness Java v0.2.2 开发说明

> 版本：`v0.2.2` | 基线：`v0.2.1` | 主线：飞书（Feishu / Lark）Bot WebSocket 长连接接入与 IM 会话渠道打通 | 架构：DDD 六边形（Maven 多模块 / Spring Boot 3.5.16 / Java 17）

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本号 | 0.2.2 |
| 状态 | 已完成（规划 → 实现 → 验证） |
| 基线 | v0.2.1 |
| 主线 | 飞书接入（长连接 + 一键配对 + 入站桥接 + 流式回发 + 控制台面板） |
| 架构 | DDD 六边形，依赖方向 基础设施 → 领域；不新增 Maven 模块 |
| 新增类 | domain 11、api 6、case 3、infrastructure 7、trigger 1（合计 28 个生产类） |
| 变更类 | 无既有类被修改（仅新增 + 装配注册） |
| 新增测试 | 3 个测试类（domain 2 + case 1，共 13 个用例） |
| 全量测试 | 新增 13 用例全绿；全工程 `test-compile` 通过 |
| 新增依赖 | `com.larksuite.oapi:oapi-sdk:2.8.3`、`com.larksuite.oapi:larksuite-oapi-shaded-protobuf:2.4.6`（传递：gson、guava、httpclient/httpmime、slf4j-api、protobuf-java） |
| 新增数据表 | `feishu_bot`（schema.sql 表数 30 → 31） |

## 2. 版本亮点

### 2.1 飞书 Bot WebSocket 长连接接入
- 采用飞书官方 SDK 的 WebSocket 长连接（`com.lark.oapi.ws.Client` + `EventDispatcher`），**无 HTTP webhook 回调**，免公网。
- 出站消息走 IM v1 `CreateMessageReq`/`UpdateMessageReq`：文本、就地编辑占位消息、交互式 Markdown 卡片。
- 入站订阅 `im.message.receive_v1`（`P2MessageReceiveV1`），支持 text 与 post 富文本抽取、@ 提及替换、发送者展示名推导。

### 2.2 一键配对（设备授权）
- 基于飞书 OAuth 设备授权（`/oauth/v1/app/registration`，`action=begin`/`action=poll`），扫码绑定后自动写入密钥并拉起长连接。
- 支持 Feishu（`accounts.feishu.cn`）与 Lark 国际站（`accounts.larksuite.com`）双域名，自动按 `tenant_brand` 切换。
- 状态机：`WAITING` → `SAVED`/`FAILED`/`EXPIRED`/`CANCELLED`；网络异常按 pending 容错（上限 20 次），`slow_down` 间隔 +5000ms。

### 2.3 入站消息 → Agent 会话 → 流式回发
- 每个飞书会话（单聊/群聊）派生独立 agentId：`<agentBaseId>-fs-<chatId 末 16 位 | anon>`。
- 流式增量节流就地编辑占位消息（`FLUSH_INTERVAL_MS=1500`、预览 4000）；长文（>4000）改发 Markdown 卡片（上限 28000），卡片失败退化为占位截断。
- 群聊按 `groupRequireMention` 与 @ 判定；飞书渠道硬编码 `AUTO_APPROVE`，**IM 渠道码不写入 `harness_session.channel_code`**（与 LLM 渠道语义隔离）。

### 2.4 凭证安全
- `app_secret` 明文不落库，DB 仅存 `secret_ref`（`FEISHU_BOT_<BOTID大写下划线化>_APP_SECRET`），复用既有 `ICredentialProviderPort`（落 `<harness.credentials.home>/.credentials.yaml`，文件权限 `rw-------`）。
- 响应/日志仅出现 `secret_ref` 或 `secretConfigured` 布尔，绝不回显密钥。

### 2.5 条件装配与版本治理
- `dsh.feishu.enabled`（缺省 true）统一门控飞书 Bean；`false` 时不产出任何飞书 Bean，应用上下文正常启动。
- 版本标识统一 0.2.2（根 pom、7 模块 pom、2 插件 pom、`dsh-java-app/pom.xml` 的 sample-tools-plugin、CHANGELOG、docker-compose、start-app.sh）。
- `feishu_bot` 表三口径（`schema.sql` / `db/migration/V20261102__feishu_bot.sql` / `dsh_java.sql`）一致，双库（MySQL8 + H2 `MODE=MySQL`）幂等。

## 3. 任务映射（spec §5 九大能力 → 实现）

| spec 能力 | 实现落位 |
| --- | --- |
| §5.1 Bot 生命周期管理 | `IBotApi` / `FeishuBotUseCase` / `BotController` |
| §5.2 长连接接入与治理 | `FeishuWsGateway` / `IFeishuBotConnectionPort` / `FeishuBeanConfig` |
| §5.3 一键配对 | `FeishuPairingUseCase` / `FeishuPairingClient` / `IFeishuPairingPort` |
| §5.4 入站去重与抽取 | `FeishuEventDeduplicator` / `FeishuWsGateway.onMessage` |
| §5.5 Agent 会话派生与流式回发 | `FeishuBotAggregate.deriveAgentId` / `FeishuBotInboundHandler` |
| §5.6 出站消息与卡片 | `FeishuMessagingClient` / `IFeishuMessagingPort` |
| §5.7 凭证引用与密钥保管 | `FeishuBotUseCase.secretConfigured` / `FeishuPairingUseCase.complete` + 既有凭据 seam |
| §5.8 发布与工程治理 | 版本 0.2.2 统一、`feishu_bot` 表、CHANGELOG |
| §5.9 控制台管理界面 | `static/bots.html` + 侧栏入口 |

## 4. 验证结论

- 编译：`mvn -DskipTests compile` 全工程通过；`mvn -DskipTests test-compile` 全工程通过。
- 测试：新增 `FeishuBotAggregateTest`、`FeishuEventDeduplicatorTest`、`FeishuBotUseCaseTest`、`FeishuBotInboundHandlerTest` 共 13 用例全绿。
- SQL 一致性：`scripts/check-sql-parity.sh` PASS（schema.sql=31、dsh_java.sql=31、db/migration=31、差异 0、README 声明 31）。
- 双库幂等：`feishu_bot` 采用 `CREATE TABLE IF NOT EXISTS` 与双库兼容类型（TINYINT / VARCHAR / TIMESTAMP(3)），可重复执行。

## 5. 已知限制

- 飞书长连接需真实 app_id/app_secret 方可联网验证；本版本交付单元级 mock 验证与静态一致性验证，未对真实飞书租户做端到端联调。
- 控制台面板以独立原生页面 `bots.html` 实现（未改动 6000 行 SPA 主逻辑），侧栏提供入口跳转。
- `dsh.feishu.*` 中的数值参数目前以常量落地并文档化对齐，`enabled` 开关为实际生效项。

## 6. 依赖与体积增量

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `com.larksuite.oapi:oapi-sdk` | 2.8.3 | 飞书官方 SDK（内含 shaded okhttp/okio） |
| `com.larksuite.oapi:larksuite-oapi-shaded-protobuf` | 2.4.6 | SDK 依赖（shaded protobuf） |
| 传递依赖 | — | gson、guava、httpclient/httpmime、slf4j-api、protobuf-java（版本受 Spring Boot BOM 管理） |

> 说明：本版本聚焦飞书接入，未引入任何新的 Maven 模块或迁移框架（仍为 Spring SQL Init + `SchemaMigrationRunner`）。