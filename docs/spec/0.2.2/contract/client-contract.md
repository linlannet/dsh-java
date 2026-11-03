# DSH 客户端契约 v1（desktop / mobile 共用）

> 状态：v1 草案（P0）。本文件与其同级 `fixtures/*.json` 是 **desktop 与 mobile 的唯一事实源**。
> 覆盖：会话列表、消息时间线、流式事件、审批/提问、数字人、协作房间事件、飞书 Bot 管理。

## 1. 决策记录

| 决策项 | 结论 |
| --- | --- |
| mobile 接入方式 | **B**：mobile 新增 `DshJavaHostRepository`（REST + SSE）直连 dsh-java，替换 `DshRemoteHostRepository`（DeepSeek Harness JSON-RPC + `events.mux`） |
| 契约维护方式 | **手写类型 + 契约测试**，不引入 OpenAPI 代码生成 |
| 单一事实源 | 本目录 `fixtures/*.json`（每份带 `contractVersion`） |

现状事实（2026-10-29 核对）：

- desktop 连 dsh-java：`/api/agent/*`、`/api/harness/*`、`/api/digital-humans`、`/api/collaboration`。
- mobile 连 DeepSeek Harness（`:3080`）：`POST /api/{method}` + WS `/api/events.mux`。
- dsh-java 全仓无 `session.prompt` / `events.mux` / `client-request`（grep 0 命中）→ mobile 当前连不上 dsh-java，这正是 B 方案要解决的。

## 2. 单一事实源与同步方式

```text
dsh-java/docs/spec/contract/fixtures/*.json      ← 唯一事实源
        │
        ├── desktop: tests/unit/contract.test.ts 直接读本目录
        │            （相对路径 ../../../dsh-java/...；可用 DSH_CONTRACT_DIR 覆盖；目录缺失则跳过）
        │
        └── mobile:  shared/src/commonTest/.../DshContractTest.kt 内嵌同内容
                     （KMP commonTest 无通用文件 IO；升级 contractVersion 时必须同步）
```

**变更规则**：改契约 ⇒ 先改 fixture 并升 `contractVersion` ⇒ 再改两端测试与模型。
两端测试都会断言 `contractVersion === 1`，版本不一致即红灯。

## 3. 传输与鉴权

| 项 | desktop 现状 | mobile 目标（B 方案） | 统一目标 |
| --- | --- | --- | --- |
| baseUrl | `http://127.0.0.1:<壳分配端口>` | 扫码/SSH 后的可达地址 | 一致 |
| 鉴权 | `harness.auth.api-keys`（壳注入，本地回环） | `Authorization: Bearer <token>` | Bearer token + 设备配对 |
| 下行 | SSE（`POST /api/agent/stream`） | SSE（同上） | SSE 为唯一事件通道 |
| 取消 | `POST /api/agent/{agentId}/cancel` | 同 | 一致 |
| 空闲保护 | 120s 看门狗 + 5s 对账轮询 | 待补 | 5s 对账 / 120s 看门狗 |

## 4. 接口映射表（Harness 方法 → dsh-java 接口）

| mobile 现用（Harness） | dsh-java 接口 | 备注 |
| --- | --- | --- |
| `host.describe` | `GET /api/harness/config/effective` | desktop 健康检查即用此接口 |
| `workspace.list` | `GET /api/agent/workspaces` | |
| `session.list` | `GET /api/harness/console/sessions?limit=&offset=0` | |
| `session.history` | `GET /api/harness/console/sessions/{sessionId}/messages` | |
| `session.prompt` | `POST /api/agent/message`；流式 `POST /api/agent/stream`（SSE） | |
| `session.cancel` | `POST /api/agent/{agentId}/cancel` | |
| `session.rename` / `session.fork` / `workspace.archiveSession` | **服务端暂缺** | 待补接口 |
| `settings.describe` / `credentials.*` | `GET /api/harness/config/effective`、`/api/harness/settings/models` | |
| `llm.providers` | `GET /api/harness/channels/presets` | |
| `skill.list` | `GET /api/harness/extensions/skills` | |
| `goal.*` | `POST /api/harness/tasks/submit` | 语义待对齐 |
| `POST /api/respond`（审批/提问） | `POST /api/harness/approvals/runtime/{approvalId}/resolve`、`POST /api/harness/questions/runtime/{questionId}/answer` | |
| `GET /api/harness/approvals/runtime/pending` | 同左 | 审批拉取 |
| `GET /api/harness/questions/runtime/pending` | 同左 | 提问拉取 |
| WS `events.mux` / `events.host` | SSE `POST /api/agent/stream`；房间事件 `GET /api/collaboration/rooms/{roomId}/events/stream?afterSeq=` | WS → SSE |
| — | `GET/POST /api/digital-humans`、`POST /api/digital-humans/discover` | mobile 后续可选接入 |
| — | `GET /api/harness/bots`（列表）、`POST /api/harness/bots`（新增）、`PUT /api/harness/bots/{botId}`（编辑）、`DELETE /api/harness/bots/{botId}`、`POST /api/harness/bots/{botId}/{enable,disable,test}` | 飞书 Bot 管理（v0.2.2） |
| — | `POST /api/harness/bots/pairing`、`GET /api/harness/bots/pairing/{pairingId}`、`DELETE /api/harness/bots/pairing/{pairingId}` | 飞书一键配对（设备授权），mobile 按需接入 |

## 5. 事件流规范

**统一事件名**（desktop 现状 ∪ mobile 需求）：

`meta` · `reasoning` · `chunk` · `tool` · `tool_result` · `approval` · `question` · `finish` · `done` · `step_break` · `error`

- desktop `agent-client.ts` 现有 `StreamEvent` 仅含 `meta/chunk/reasoning/finish/done/tool_result/step_break/error`，**缺 `tool` / `approval` / `question`**，需在 P1 补齐。
- `seq`：同一会话内严格单调递增；重连用 `afterSeq` 续传，服务端重放缺失区间。
- **对账**：运行期 5s 轮询服务端快照；连续 120s 无字节则主动断开并回落对账（desktop 已有，mobile 需补）。
- **generation**：desktop `sessionRuns`/generation ≡ mobile `connectionGeneration`；重连 `generation += 1`，在途请求以 `generation-cancelled` 结束。
- **退避**：统一指数退避 1s → 15s + 抖动（desktop 现为 1s→15s，mobile 现为固定 1s）。

## 6. 领域模型映射（差异与统一名）

| 概念 | desktop | mobile | 统一动作 |
| --- | --- | --- | --- |
| 会话 | `SessionSummary{agentId, sessionId, title, lastMessage, modelCode, updatedAt, createdAt, workspaceId}` | `DshSession{id, title, …}` | 统一主键语义：`id` ← `sessionId` |
| 消息 | `ConversationMessage{role, content, reasoning, toolName, callId, arguments, result, status, durationMs, createdAt, attribution}` | `DshMessage{id, role, …}` | desktop **补 `id`** |
| 审批 | `RuntimeApproval{approvalId, sessionId, toolName, displayCommand, arguments, createdAt}` | `DshPendingApproval{rpcId, sessionId, …}` | 统一为 `approvalId`（mobile 去 `rpcId`） |
| 提问 | `RuntimeQuestion{questionId, agentId, questions[], createdAt}` / `RuntimeQuestionItem{id, question, header, options[{label,description}], multiSelect}` | `DshPendingQuestion{rpcId, …}` / `DshPendingQuestionItem{id, question, …}` / `DshPendingQuestionOption{label, description}` | 统一为 `questionId`；字段已基本同构 |
| 工作区 | `WorkspaceEntry{name, path, local, parentPath}` | `DshWorkspaceGroup{workspaceId, title}` / `DshDirectoryEntry{name, path}` | 统一以 `path` 为主键 |
| 模型 | `AvailableModel{channelCode, providerCode, modelCode}` | `DshModelOption{provider, providerName, …}` | 统一 `channelCode` |
| 数字人 | `DigitalHuman{id, displayName, avatarRef, purpose, roleTags, themeColor, approvalPolicy, concurrencyLimit, endpoint, createdAt}` | 暂无 | mobile 按需接入 |
| 房间事件 | `RoomEvent{seq, id, roomId, type, payload, digitalHumanId}` | `DshRawSessionEvent{seq, type}` | 采用 desktop 形状 |
| 飞书 Bot | `FeishuBot{botId, botName, appId, secretRef, secretConfigured, enabled, agentBaseId, channelCode, cwd, groupRequireMention, connectionState, lastError, updatedAt}` | 暂无 | 采用服务端形状；**不含 `appSecret`**，只以 `secretConfigured` 标记 |
| 飞书配对 | `FeishuPairing{pairingId, status, url, userCode, expireIn, botId, appId, error}` | 暂无 | `status ∈ {WAITING,SAVED,FAILED,CANCELLED,EXPIRED}`；`connectionState ∈ {IDLE,CONNECTING,CONNECTED,ERROR}` |

## 7. 待办清单

**P0（契约）**
- [x] 建立 fixtures 与本文档
- [x] desktop 契约测试读 canonical fixtures
- [x] mobile 契约测试内嵌同内容并断言 `contractVersion`
- [x] console 契约校验：`console-contract.check.mjs`（信封剥离 + 键集覆盖 + seq 单调）
- [x] console 对齐：SSE 增加 `tool` / `approval` / `question` 事件解析；补提问 pending + answer
- [x] 飞书 Bot 管理纳入契约：`fixtures/feishu-bots.json`（Bot 列表 + 配对状态 + 状态枚举白名单）
- [x] 控制台侧校验：`console-contract.check.mjs` 覆盖 `/api/harness/bots` 字段契约

**P1（mobile 接入）**
- [ ] mobile 新增 `DshJavaHostRepository`（REST + SSE），保留 `DshRemoteHostRepository` 做开关
- [ ] mobile 审批/提问字段改 `approvalId` / `questionId`
- [ ] mobile 补 5s 对账 + 120s 空闲看门狗 + 1s→15s 退避
- [ ] desktop 补 `StreamEvent`：`tool` / `approval` / `question`

**P2（模型对齐）**
- [ ] desktop `ConversationMessage` 补 `id`
- [ ] 服务端补 `session.rename` / `session.fork` / 归档接口
- [ ] 认证统一：Bearer token + 设备配对

## 8. Fixtures

| 文件 | 内容 |
| --- | --- |
| `session-list.json` | 会话列表（`GET /api/harness/console/sessions`） |
| `session-timeline.json` | 消息时间线（含 assistant / tool / attribution） |
| `stream-events.json` | SSE 事件序列（seq 递增） |
| `approvals-questions.json` | 待处理审批 + 待回答提问 |
| `digital-humans.json` | 数字人档案（含 endpoint） |
| `room-events.json` | 协作房间事件（seq 递增） |
| `feishu-bots.json` | 飞书 Bot 列表 + 一键配对状态（含状态枚举白名单） |

fixture 均为**归一化后的领域对象**（服务端 `code/info/data` 信封由各端统一剥离后再消费）。

配套校验脚本：`console-contract.check.mjs`（控制台）。需后端已启动：

```bash
node dsh-java/docs/spec/0.2.2/contract/console-contract.check.mjs
# BASE_URL=http://127.0.0.1:8090 ROOM_ID=room_xxx node console-contract.check.mjs
```
