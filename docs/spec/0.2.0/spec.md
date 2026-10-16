# dsh-java 核心底座对齐需求规格

> 本文档基于 dsh-java（Java 17 + Spring Boot 3.5.16 + DDD 六边形架构，v0.1.8 开发者预览）与 deepseek-harness（TS/Python，pnpm monorepo，200+ 包）近 30 天核心底座变更的逐项对比分析，以 EARS（Easy Approach to Requirements Syntax）格式定义 dsh-java 需求。
>
> - **需求编号体系**：`REQ-{域字母}{差距编号}-{优先级}[-{序号}]`，如 `REQ-A1-P0-2`。
> - **EARS 类型**：Ubiquitous（普遍型）/ Event-driven（事件驱动型）/ State-driven（状态驱动型）/ Optional（可选型）/ Unwanted（非期望型）。
> - **优先级**：P0 核心底座补齐（架构对齐）/ P1 核心能力增强 / P2 生态扩展。

---

# 1. 组件定位

## 1.1 核心职责

本组件负责 [追踪并移植] [deepseek-harness 核心底座能力]，实现 [dsh-java 作为高度模块化 Agent 运行时底座的架构与功能对齐]。

## 1.2 核心输入

1. **上游系统调用请求**：SDK 客户端通过 JSON-RPC stdio 发起的会话、工具、子代理调用请求。
2. **用户操作指令**：通过 Web/CLI/SDK 入口提交的自然语言消息、slash 命令、@file 引用。
3. **定时任务触发信号**：Schedule 服务和 Webhook 触发的提醒与事件注入。
4. **消息队列订阅消息**：OTel OTLP 上报通道、Webhook 事件流。
5. **插件与 Bundle 装配信号**：Preset 声明、Bundle 清单、HMR 模块变更事件。

## 1.3 核心输出

1. **返回调用方的响应**：LLM 生成文本、工具执行结果、Session 投影数据、搜索结果。
2. **发送下游系统的请求**：LLM Provider API 调用、MCP 服务端调用、子进程/Shell/SSH 调用、存储后端读写。
3. **推送给用户的通知**：提问请求、反馈对话框、提醒触发、超时提示。
4. **发布到消息队列的事件**：Session 事件流（事件溯源）、OTel span/log、Webhook 事件、插件生命周期事件。

## 1.4 职责边界

1. **不负责** LLM 模型本身的训练与权重管理，仅负责请求编排与响应处理。
2. **不负责** MCP 服务端的实现，仅负责作为 MCP 客户端消费外部 MCP 服务。
3. **不负责** 前端 Web UI 的渲染实现，仅通过 API/SDK 协议供前端消费。
4. **不负责** deepseek-harness 的 TS/Python 源码维护，dsh-java 是独立 Java 移植版本。
5. **不负责** 操作系统内核级沙箱原语的实现（Landlock/Seatbelt/ACL 内核接口），仅负责调用与封装。

---

# 2. 领域术语

**Scope 路由（Scope Routing）**
: 系统按不透明 scope key 及其父链嵌套关系，将事件分发到匹配 scope 的订阅者的机制。

**Waterfall 拦截（Waterfall Interception）**
: 一种事件链模式，专家/拦截器按优先级依次处理事件，可在任意阶段终止传播或改写载荷。

**Session 格式版本（Session Format Version）**
: Session 事件流的 schema 版本号，当前目标版本为 V4，低于 V4 的流需经冻结迁移链升级。

**投影（Projection）**
: 从 Session 事件流（append-only）派生出的可查询物化视图，如对话历史、统计摘要。

**检查点（Checkpoint）**
: 在模型请求或工具副作用前保存的可恢复执行上下文快照，用于故障后恢复。

**PTC（Presentation）**
: Agent Tool Presentation 的缩写，指工具面向模型的呈现声明，包含 PTC、native 或两者选择器。

**Spill 溢出（Spill）**
: 当上下文或结果超出容量上界时，将溢出部分转存到外部存储并以引用替代的机制。

**Bundle 装配（Bundle Assembly）**
: 将一组插件、配置、入口组合为可发布运行单元的装配体系，如 headless/sdk-app/web-app。

**HMR（Hot Module Replacement）**
: 模块级热重载，在运行时替换已加载模块代码而不重启进程。

**重放感知（Replay-aware）**
: 在事件重放场景下能识别重放上下文，避免重复计入副作用（如 token 用量）的测量特性。

---

# 3. 角色与边界

## 3.1 核心角色

1. **终端用户（End User）**：通过 Web/CLI/SDK 与 Agent 会话交互，提交消息、回答提问、触发 slash 命令。
2. **插件开发者（Plugin Developer）**：开发 dsh-java 插件，注册工具、hook、waterfall 专家、Provider 适配器。
3. **Preset/Bundle 装配者（Preset Assembler）**：编写 YAML Preset 和 Bundle 清单，组合运行时配置。
4. **运维管理员（Operator）**：管理部署实例、监控 OTel 指标、配置沙箱策略与凭证。

## 3.2 外部系统

1. **LLM Provider（DeepSeek/Anthropic/OpenAI-Compatible/pi-ai）**：接收 LLM 生成请求，返回流式/非流式响应。
2. **MCP 服务端**：通过 stdio 或 StreamableHTTP 暴露工具与资源，供 dsh-java 消费。
3. **存储后端（JDBC/JSONL/SQLite）**：持久化 Session 事件流、投影缓存、附件、工作区记录。
4. **OTel Collector**：接收 OTLP span/log/metric 上报。
5. **Webhook 源（GitHub 等）**：向 dsh-java 推送外部事件。
6. **子代理 SDK 进程**：通过 stdio JSON-RPC 与主进程通信的进程外子代理。
7. **操作系统沙箱原语（Landlock/Seatbelt/ACL/Job Object）**：提供内核级隔离与进程控制。

## 3.3 交互上下文

```plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml/stdlib/main/cloudinsight/archimate.puml

actor "终端用户" as User
actor "插件开发者" as Dev
actor "Preset装配者" as Assembler
actor "运维管理员" as Operator

rectangle "dsh-java 核心底座" as DSH {
  port "SDK/API入口" as API
  port "插件注册口" as Plugin
  port "Preset/Bundle口" as Preset
  port "OTel/监控口" as Telemetry
}

cloud "LLM Provider" as LLM
cloud "MCP服务端" as MCP
cloud "存储后端" as Storage
cloud "OTel Collector" as Collector
cloud "Webhook源" as Webhook
cloud "子代理SDK" as Subagent
cloud "OS沙箱原语" as Sandbox

User --> API : 消息/命令/提问
Dev --> Plugin : 工具/hook/专家
Assembler --> Preset : YAML清单
Operator --> Telemetry : 策略/凭证

API --> LLM : 生成请求
API --> MCP : 工具/资源调用
DSH --> Storage : 事件流/投影/附件
DSH --> Collector : OTLP上报
Webhook --> API : 事件推送
DSH --> Subagent : JSON-RPC
DSH --> Sandbox : 隔离/控制
@enduml
```

---

# 4. DFX 约束

## 4.1 性能

1. **核心接口响应时间**：Session 创建到首轮 LLM 响应开始流式返回，P99 ≤ 3s（不含 LLM 自身延迟）。
2. **Session list 吞吐量**：在单实例 10 万 Session 规模下，list 接口应支持工作时间分片查询，P99 ≤ 500ms。
3. **OTel 上界**：OTLP 上报缓冲区字节上界可配置，默认 4MB，超界按丢弃最旧事件策略裁剪。
4. **HMR 刷新延迟**：模块级热重载从检测变更到生效，P99 ≤ 2s。

## 4.2 可靠性

1. **系统可用性目标**：核心底座可用性 ≥ 99.9%（不含外部 Provider 不可用）。
2. **故障恢复**：从 failed-step 恢复继续执行，恢复操作幂等，RTO ≤ 5s。
3. **数据一致性**：Session 事件流为 append-only，投影通过写后合流保证最终一致性。
4. **重放正确性**：事件重放时 token 用量、副作用不重复计入。

## 4.3 安全性

1. **沙箱隔离**：每次工具调用按解析的策略执行隔离，禁止越界文件访问与网络访问。
2. **凭证安全**：PKCE 授权流程，凭证不落明文日志，对话式凭证获取流需用户显式确认。
3. **审计日志**：关键操作（沙箱拒绝、凭证获取、插件加载、子代理启停）必须记录审计日志。

## 4.4 可维护性

1. **监控指标**：必须接入 Session 事件计数、LLM 调用延迟/错误率、工具执行延迟/错误率、OTel 上报丢弃数。
2. **日志规范**：结构化 JSON 日志，包含 traceId、sessionId、scopeKey、stepIndex。
3. **链路追踪**：所有跨进程调用（LLM/MCP/子代理/存储）必须传播 trace 上下文。

## 4.5 兼容性

1. **Session 格式兼容**：支持加载 V0~V4 全部历史格式，通过冻结迁移链升级至 V4。
2. **接口兼容**：SDK wire 协议方法新增不破坏旧客户端；Provider 适配器新增不破坏旧配置。
3. **插件兼容**：插件 API 变更遵循 semver，deprecation 至少保留一个大版本周期。

---

# 5. 核心能力

> 以下需求按域（A-S）分组，每条需求标注：需求ID、优先级、EARS 类型、对应差距编号。

## 5.A 核心抽象层（core）

### REQ-A1-P0-1
- **差距编号**：A1（Scope 路由机制 — 缺失，核心架构特征）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当事件携带 scope key 时，dsh-java 系统应当按 scope 父链嵌套关系进行 scope-filtered 事件分发，仅通知 scope 匹配的订阅者。
- **验收条件**：[发布带 scope key 的事件] → [仅 scope 父链匹配的订阅者收到事件，不匹配者不收到]

### REQ-A1-P0-2
- **差距编号**：A1
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供不透明的 scope key 抽象，使 scope key 的内部结构对事件消费方不可见。
- **验收条件**：[任意消费方尝试解析 scope key 内部结构] → [无法获取内部字段，仅能比较相等与父子关系]

### REQ-A2-P0-1
- **差距编号**：A2（System Prompt waterfall 组装 — 缺 waterfall 拦截）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当组装 system prompt 时，dsh-java 系统应当通过 waterfall 事件链允许专家按优先级依次拦截和改写 prompt 片段。
- **验收条件**：[注册多个 prompt 专家并触发组装] → [专家按优先级依次执行，每位可读取前序输出并改写]

### REQ-A2-P0-2
- **差距编号**：A2
- **优先级**：P0
- **EARS 类型**：Unwanted
- **需求**：如果某专家在 waterfall 中终止事件传播，则 dsh-java 系统应当以该专家的输出作为最终片段，不再调用后续专家。
- **验收条件**：[专家 A 终止传播] → [排在 A 之后的专家不被调用，最终 prompt 使用 A 的输出]

### REQ-A3-P0-1
- **差距编号**：A3（Tool 执行管线 — 缺 guard/around/result 三阶段）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当工具被调用时，dsh-java 系统应当依次执行 pre、guard、around、post、result 五个阶段的拦截器。
- **验收条件**：[调用任意工具] → [五阶段拦截器按 pre→guard→around→post→result 顺序执行]

### REQ-A3-P0-2
- **差距编号**：A3
- **优先级**：P0
- **EARS 类型**：Unwanted
- **需求**：如果 guard 阶段拦截器拒绝调用，则 dsh-java 系统应当立即返回拒绝结果，不执行工具主体。
- **验收条件**：[guard 拦截器返回拒绝] → [工具主体不执行，调用方收到拒绝结果及原因]

### REQ-A3-P0-3
- **差距编号**：A3
- **优先级**：P0
- **EARS 类型**：State-driven
- **需求**：在 around 阶段执行期间，dsh-java 系统应当允许拦截器包裹工具主体调用并可在调用前后注入逻辑。
- **验收条件**：[around 拦截器执行中] → [拦截器可控制是否调用主体，并在主体调用前后注入前置/后置逻辑]

### REQ-A4-P1-1
- **差距编号**：A4（Agent Tool Presentation — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 agent-tool-presentation 特性的场合，dsh-java 系统应当为每个工具提供 PTC、native 或两者选择器的呈现声明。
- **验收条件**：[工具注册时声明 presentation] → [模型可见 PTC/native/选择器三种呈现形式之一]

### REQ-A5-P1-1
- **差距编号**：A5（Agent Default Model — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当入口未显式指定模型时，dsh-java 系统应当使用 agent-default-model 模块提供的共享默认模型选择。
- **验收条件**：[入口调用未传 model 参数] → [系统使用 agent-default-model 配置的默认模型]

## 5.B Session 域

### REQ-B1-P0-1
- **差距编号**：B1（Session 格式版本化与迁移 — 缺格式版本化和迁移框架）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当为 Session 事件流定义格式版本号常量，当前目标版本为 V4。
- **验收条件**：[检查系统常量] → [存在 SESSION_FORMAT_VERSION=4 且新写入事件流标注 V4]

### REQ-B1-P0-2
- **差距编号**：B1
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当加载格式版本低于当前版本的 Session 事件流时，dsh-java 系统应当依次执行 v0→v1→v2→v3→v4 的冻结迁移链。
- **验收条件**：[加载 V2 事件流] → [依次执行 v2→v3→v4 迁移，每步迁移函数不可变（冻结）]

### REQ-B1-P0-3
- **差距编号**：B1
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当 Session 事件流中存在 pending-free 的 tool/result 节点时，dsh-java 系统应当容忍并恢复该节点而非报错。
- **验收条件**：[事件流含无 pending 标记的孤立 tool/result] → [系统正常加载，不抛格式错误]

### REQ-B1-P0-4
- **差距编号**：B1
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当 Session 事件流中存在 failed-step 时，dsh-java 系统应当支持从 failed-step 恢复继续执行。
- **验收条件**：[加载含 failed-step 的 Session 并请求恢复] → [系统从 failed-step 之后继续执行，不重放已成功步骤]

### REQ-B2-P0-1
- **差距编号**：B2（Session 投影框架 — 缺正式投影框架和缓存）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 session-projection 投影框架，包含类型表和 provider 契约。
- **验收条件**：[注册投影 provider] → [provider 按契约声明投影类型，框架按类型表路由]

### REQ-B2-P0-2
- **差距编号**：B2
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当事件写入持久化存储后，dsh-java 系统应当通过 projection-cache 实现写后合流，将增量事件合并到投影缓存。
- **验收条件**：[写入新事件] → [投影缓存增量合并该事件，不重算全量投影]

### REQ-B3-P1-1
- **差距编号**：B3（Session 检查点策略 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：在发起模型请求前，dsh-java 系统应当执行语义检查点策略，保存可恢复的执行上下文。
- **验收条件**：[发起 LLM 请求] → [请求前写入检查点，含当前上下文快照]

### REQ-B3-P1-2
- **差距编号**：B3
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：在工具产生副作用前，dsh-java 系统应当执行语义检查点策略，保存副作用前的状态快照。
- **验收条件**：[工具将产生副作用（如写文件）] → [副作用前写入检查点，可回滚至副作用前状态]

### REQ-B4-P1-1
- **差距编号**：B4（Session 遥测 OTel — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 session-telemetry 特性的场合，dsh-java 系统应当通过字节有界的 OTLP 上报 session log events。
- **验收条件**：[启用 session-telemetry] → [session log events 经 OTLP 上报，缓冲区受字节上界约束]

### REQ-B4-P1-2
- **差距编号**：B4
- **优先级**：P1
- **EARS 类型**：Unwanted
- **需求**：如果 OTLP 上报缓冲区超过字节上界，则 dsh-java 系统应当按丢弃策略裁剪最旧事件而非拒绝新事件。
- **验收条件**：[缓冲区超界] → [丢弃最旧事件，新事件继续入队，记录丢弃计数]

### REQ-B5-P1-1
- **差距编号**：B5（Session 标题/统计/轮次大纲 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当 Session 创建或首轮对话完成时，dsh-java 系统应当生成或更新 Session 标题。
- **验收条件**：[首轮对话完成] → [Session 标题被生成或更新，可在 list 中展示]

### REQ-B5-P1-2
- **差距编号**：B5
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当为每个 Session 维护统计信息，至少包含消息数、工具调用数、token 用量。
- **验收条件**：[查询 Session 统计] → [返回消息数、工具调用数、token 用量等聚合指标]

### REQ-B5-P1-3
- **差距编号**：B5
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当为每个 Session 生成轮次大纲，概括每轮的目标、动作和结果。
- **验收条件**：[查询 Session 轮次大纲] → [返回每轮的 goal/action/result 摘要列表]

### REQ-B6-P1-1
- **差距编号**：B6（Session 全文搜索 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当用户提交全文搜索查询时，dsh-java 系统应当对 Session 事件流内容执行全文本检索并返回匹配片段。
- **验收条件**：[提交搜索关键词] → [返回匹配的 Session 片段及定位信息，支持高亮]

### REQ-B7-P0-1
- **差距编号**：B7（Session 持久化多后端 — 已有，需补抽象 seam）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 session-persistence 抽象 seam，使 JsonlSessionEventStore 和 JdbcSessionEventStore 均通过统一契约实现。
- **验收条件**：[检查两种 Store 实现] → [二者实现同一抽象接口，无后端特有方法泄漏到上层]

### REQ-B7-P0-2
- **差距编号**：B7
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当切换持久化后端时，dsh-java 系统应当不改变上层 Session 域服务的调用方式。
- **验收条件**：[配置从 JDBC 切换为 JSONL] → [Session 域服务代码无变更，仅配置改变]

## 5.C LLM 域

### REQ-C1-P0-1
- **差距编号**：C1（LLM waterfall 拦截 — 缺 waterfall 拦截）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当发起 LLM 流式生成请求时，dsh-java 系统应当通过 llm/stream waterfall 事件链允许 retry、replay、routing 拦截器介入。
- **验收条件**：[发起流式 LLM 请求] → [retry/replay/routing 拦截器按 waterfall 链依次获得介入机会]

### REQ-C1-P0-2
- **差距编号**：C1
- **优先级**：P0
- **EARS 类型**：Unwanted
- **需求**：如果 routing 拦截器重定向请求到另一 Provider，则 dsh-java 系统应当以新 Provider 执行请求并保留原始请求上下文。
- **验收条件**：[routing 重定向到 Provider B] → [请求由 B 执行，原始请求参数与 trace 上下文保留]

### REQ-C2-P1-1
- **差距编号**：C2（LLM 重试策略 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当 LLM Provider 返回可重试错误时，dsh-java 系统应当按 llm-retry 策略进行 Provider 路由重试。
- **验收条件**：[Provider 返回 429/5xx 等可重试错误] → [按策略重试，可路由到备用 Provider]

### REQ-C2-P1-2
- **差距编号**：C2
- **优先级**：P1
- **EARS 类型**：Unwanted
- **需求**：如果重试次数超过策略上限，则 dsh-java 系统应当向调用方返回最终错误而非无限重试。
- **验收条件**：[重试次数达上限] → [停止重试，返回最后一次错误及重试摘要]

### REQ-C3-P0-1
- **差距编号**：C3（Token Meter — 缺重放感知测量服务）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供重放感知的 token 测量服务，在事件重放时不重复计入 token 用量。
- **验收条件**：[重放已含 token 用量的事件流] → [token 总量等于持久化快照值，不翻倍]

### REQ-C3-P0-2
- **差距编号**：C3
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当 Session 事件流被重放时，dsh-java 系统应当从已持久化的 token 用量快照恢复而非重新估算。
- **验收条件**：[重放事件流] → [token 用量从快照恢复，不触发重新估算逻辑]

### REQ-C4-P1-1
- **差距编号**：C4（LLM 请求扩展 — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 llm-api-extensions 特性的场合，dsh-java 系统应当提供加法请求字段注册表，允许插件注册额外的 LLM 请求字段。
- **验收条件**：[插件注册扩展字段 X] → [后续 LLM 请求包含字段 X，不影响已有字段]

### REQ-C5-P1-1
- **差距编号**：C5（LLM 适配器 — 已有，缺 pi-ai 孪生）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 pi-ai Provider 的场合，dsh-java 系统应当提供 llm-pi-ai 适配器，与 DeepSeek 适配器共享核心协议实现。
- **验收条件**：[配置 pi-ai Provider] → [请求经 pi-ai 适配器处理，核心协议逻辑与 DeepSeek 共享]

## 5.D MCP 域

### REQ-D1-P0-1
- **差距编号**：D1（MCP 客户端 — 已有，需补重连策略和命名规范）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当 MCP 服务端连接断开时，dsh-java 系统应当按重连策略自动重连而非立即失败。
- **验收条件**：[MCP 服务端断连] → [按退避策略自动重连，重连成功后恢复工具可用]

### REQ-D1-P0-2
- **差距编号**：D1
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当将 MCP 工具按 `mcp__{server}__{raw}` 命名规范暴露，以避免跨 server 工具名冲突。
- **验收条件**：[两个 MCP server 暴露同名工具] → [工具名带 server 前缀，无冲突]

### REQ-D2-P1-1
- **差距编号**：D2（MCP 资源 — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 mcp-resources 特性的场合，dsh-java 系统应当支持 scoped 资源发现与读取，按 scope 过滤可见资源。
- **验收条件**：[启用 mcp-resources 并按 scope 查询] → [仅返回 scope 匹配的资源列表及内容]

## 5.E Context 域

### REQ-E1-P1-1
- **差距编号**：E1（AGENTS.md 指令加载 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当 Session 初始化时，dsh-java 系统应当从工作区层级加载 AGENTS.md 指令文件并合并为有效指令集。
- **验收条件**：[工作区含多层级 AGENTS.md] → [按层级合并，子级覆盖父级同键指令]

### REQ-E2-P1-1
- **差距编号**：E2（文件引用 @file 语法 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当用户消息中包含 @file 路径引用时，dsh-java 系统应当将引用文件内容内联展开到消息上下文。
- **验收条件**：[消息含 @path/to/file] → [文件内容内联展开，模型可见文件内容]

### REQ-E3-P1-1
- **差距编号**：E3（跨 session 快照引用 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当用户消息中包含跨 session 快照引用时，dsh-java 系统应当解析引用并加载目标 Session 的只读快照。
- **验收条件**：[消息含 session 引用] → [目标 Session 以只读快照注入上下文，不可修改]

### REQ-E4-P1-1
- **差距编号**：E4（时间上下文 — 缺失）
- **优先级**：P1
- **EARS 类型**：State-driven
- **需求**：在每一步执行期间，dsh-java 系统应当向上下文注入当前时间与自 Session 起始的流逝时间。
- **验收条件**：[任意步骤执行中] → [上下文含当前时间戳和 elapsed 时间]

### REQ-E5-P1-1
- **差距编号**：E5（tmux 上下文 — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 tmux 集成的场合，dsh-java 系统应当在每一步向上下文注入当前 pane 和 window 位置信息。
- **验收条件**：[启用 tmux 集成] → [每步上下文含 pane/window 标识]

## 5.F Compaction 域

### REQ-F1-P1-1
- **差距编号**：F1（图像卸载 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当上下文压缩触发时，dsh-java 系统应当将图像内容替换为占位符并支持后续重试恢复。
- **验收条件**：[压缩触发且上下文含图像] → [图像替换为占位符，记录可恢复引用]

### REQ-F2-P1-1
- **差距编号**：F2（工具结果剪枝 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当工具结果超过压缩阈值时，dsh-java 系统应当按 head/middle/tail 策略剪枝，保留首尾片段并摘要中间部分。
- **验收条件**：[工具结果超阈值] → [结果被剪枝为首部+摘要+尾部，总长度低于阈值]

### REQ-F3-P1-1
- **差距编号**：F3（显式压缩命令 — 缺失）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当用户执行 /compact slash 命令时，dsh-java 系统应当立即触发上下文压缩。
- **验收条件**：[用户输入 /compact] → [立即执行压缩，返回压缩后上下文摘要]

## 5.G Sandbox 域

### REQ-G1-P2-1
- **差距编号**：G1（Linux Landlock — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在 Linux 平台且包含 sandbox-local 特性的场合，dsh-java 系统应当通过 Landlock 或 bwrap 实现文件系统隔离沙箱。
- **验收条件**：[Linux + 启用 sandbox-local] → [工具执行被限制在允许的文件系统路径内]

### REQ-G2-P2-1
- **差距编号**：G2（macOS Seatbelt — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在 macOS 平台且包含 sandbox-local 特性的场合，dsh-java 系统应当通过 Seatbelt (sandbox-exec) 实现沙箱隔离。
- **验收条件**：[macOS + 启用 sandbox-local] → [工具执行被 Seatbelt 策略约束]

### REQ-G3-P2-1
- **差距编号**：G3（Windows ACL — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在 Windows 平台且包含 sandbox-windows-acl 特性的场合，dsh-java 系统应当通过受限 token 和 capability-SID 写白名单实现 ACL 沙箱。
- **验收条件**：[Windows + 启用 ACL 沙箱] → [进程以受限 token 运行，仅白名单路径可写]

### REQ-G3-P2-2
- **差距编号**：G3
- **优先级**：P2
- **EARS 类型**：Event-driven
- **需求**：当 ACL 沙箱拒绝写入时，dsh-java 系统应当提供诊断信息标识被拒绝的路径和原因。
- **验收条件**：[写入被 ACL 拒绝] → [返回诊断信息含路径、拒绝原因、建议修复]

### REQ-G4-P1-1
- **差距编号**：G4（沙箱策略解析 — 已有，需增强）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当每次工具调用发起时，dsh-java 系统应当解析该调用的沙箱策略并向模型上下文注入策略摘要。
- **验收条件**：[发起工具调用] → [策略被解析，模型上下文含该调用的策略摘要]

## 5.H Subagent 域

### REQ-H1-P2-1
- **差距编号**：H1（SDK 子代理 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 subagent-dsh-sdk 特性的场合，dsh-java 系统应当支持进程外 SDK 子代理，通过 stdio JSON-RPC 通信。
- **验收条件**：[启用 SDK 子代理] → [子代理以独立进程运行，经 JSON-RPC stdio 通信]

### REQ-H2-P1-1
- **差距编号**：H2（进程内驱动 — 基本对齐）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供进程内子代理驱动，与 DSH subagent-in-process-driver 行为对齐。
- **验收条件**：[启动进程内子代理] → [行为与 DSH in-process-driver 一致，含生命周期管理]

### REQ-H3-P2-1
- **差距编号**：H3（子代理控制工具 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-subagent-control 特性的场合，dsh-java 系统应当提供 send_message、interrupt、list_agents 控制工具。
- **验收条件**：[启用子代理控制] → [三个工具可用，可向子代理发消息/中断/列举]

## 5.I Workflow/Plan/Todo/Goal 域

### REQ-I1-P2-1
- **差距编号**：I1（PTC 运行时编排 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 workflow-ptc 特性的场合，dsh-java 系统应当提供沙箱 Node PTC 运行时编排能力。
- **验收条件**：[启用 workflow-ptc] → [可在沙箱 Node 中编排 PTC 工作流]

### REQ-I2-P2-1
- **差距编号**：I2（Ralph loop — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-ralph 特性的场合，dsh-java 系统应当提供 fresh-agent Ralph loop 工具。
- **验收条件**：[启用 tool-ralph] → [可启动 fresh-agent Ralph 循环]

### REQ-I3-P2-1
- **差距编号**：I3（Goal-round 驱动 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 goal-round-driver 特性的场合，dsh-java 系统应当提供竞争隔离的 goal-round 驱动。
- **验收条件**：[启用 goal-round-driver] → [多 goal 竞争时按隔离策略调度]

### REQ-I4-P2-1
- **差距编号**：I4（Goal slash 命令 — 缺失）
- **优先级**：P2
- **EARS 类型**：Event-driven
- **需求**：当用户执行 /goal slash 命令时，dsh-java 系统应当启动 goal 驱动流程。
- **验收条件**：[用户输入 /goal] → [启动 goal 驱动流程，进入 goal 模式]

## 5.J Skill/Hooks/Guard 域

### REQ-J1-P2-1
- **差距编号**：J1（Office skill — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 skill-office 特性的场合，dsh-java 系统应当提供 Office 工作流技能。
- **验收条件**：[启用 skill-office] → [Office 文档工作流技能可用]

### REQ-J2-P2-1
- **差距编号**：J2（Badge skill — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 skill-badge 特性的场合，dsh-java 系统应当提供 badge 技能。
- **验收条件**：[启用 skill-badge] → [badge 技能可用]

### REQ-J3-P1-1
- **差距编号**：J3（Hook wire protocol — 缺 CC/Codex 协议桥接）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 Claude Code 或 Codex hook 集成的场合，dsh-java 系统应当实现 hook-protocol 桥接，支持 CC/Codex hook 协议。
- **验收条件**：[配置 CC/Codex hook] → [hook 按 CC/Codex 协议格式收发]

### REQ-J4-P1-1
- **差距编号**：J4（重复工具防护 — 基本对齐）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当同一工具被连续重复调用且参数未变化时，dsh-java 系统应当触发重复工具提醒。
- **验收条件**：[连续两次同工具同参数调用] → [触发重复提醒，提示用户/模型]

### REQ-J5-P1-1
- **差距编号**：J5（超时策略 — 基本对齐）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当为每个工具提供 per-tool deadline 超时策略。
- **验收条件**：[配置工具 deadline] → [该工具按独立 deadline 执行超时控制]

### REQ-J5-P1-2
- **差距编号**：J5
- **优先级**：P1
- **EARS 类型**：Unwanted
- **需求**：如果工具执行超过 deadline，则 dsh-java 系统应当中断执行并返回超时错误。
- **验收条件**：[工具执行超 deadline] → [中断执行，返回超时错误码及已耗时]

## 5.K Telemetry/Identity/Credentials 域

### REQ-K1-P1-1
- **差距编号**：K1（OTel 服务 — 缺失）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 otel 特性的场合，dsh-java 系统应当提供 Cordis OTel 服务，支持字节有界 OTLP 上报。
- **验收条件**：[启用 otel] → [OTel 服务启动，span/log/metric 经 OTLP 字节有界上报]

### REQ-K2-P2-1
- **差距编号**：K2（匿名用户身份 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 anonymous-user-id 特性的场合，dsh-java 系统应当为未认证用户生成稳定的匿名身份标识。
- **验收条件**：[未认证用户首次访问] → [生成稳定匿名 ID，后续访问复用同一 ID]

### REQ-K3-P2-1
- **差距编号**：K3（授权 seam — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 authorization 特性的场合，dsh-java 系统应当提供对话式凭证获取流 seam。
- **验收条件**：[启用 authorization seam] → [凭证缺失时以对话流获取，用户显式确认]

### REQ-K4-P2-1
- **差距编号**：K4（PKCE 浏览器授权 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 deepseek-account 特性的场合，dsh-java 系统应当支持 PKCE 浏览器授权流程。
- **验收条件**：[启用 PKCE 授权] → [经浏览器 PKCE 流获取 token，凭证不落明文]

## 5.L Storage/FS/Shell/Terminal/SSH/Subprocess 域

### REQ-L1-P2-1
- **差距编号**：L1（Storage 域数据形式 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 storage-domain 特性的场合，dsh-java 系统应当提供 schema 校验和事件发射的 KV 域数据形式。
- **验收条件**：[启用 storage-domain] → [KV 数据经 schema 校验后以事件发射持久化]

### REQ-L2-P2-1
- **差距编号**：L2（SQLite 后端 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 storage-sqlite 特性的场合，dsh-java 系统应当提供 SQLite 存储后端。
- **验收条件**：[配置 SQLite 后端] → [事件流/投影/附件可经 SQLite 持久化]

### REQ-L3-P1-1
- **差距编号**：L3（FS 搜索工具 — 需确认 glob/grep）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 glob 和 grep 文件搜索工具，支持 glob 模式匹配和正则内容检索。
- **验收条件**：[调用 glob/grep 工具] → [返回匹配文件路径/匹配行，行为与 DSH tool-fs-search 对齐]

### REQ-L4-P2-1
- **差距编号**：L4（str-replace-editor — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-str-replace-editor 特性的场合，dsh-java 系统应当提供 view/create/replace/insert 四种字符串替换编辑操作。
- **验收条件**：[启用 str-replace-editor] → [四种操作可用，含精确匹配与冲突检测]

### REQ-L5-P2-1
- **差距编号**：L5（FS 观察策略 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 fs-observation-policy 特性的场合，dsh-java 系统应当提供文件系统观察策略。
- **验收条件**：[启用 fs-observation-policy] → [文件变更按策略观察并通知]

### REQ-L6-P2-1
- **差距编号**：L6（PowerShell — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在 Windows 平台且包含 pwsh 特性的场合，dsh-java 系统应当提供 PowerShell 本地和沙箱执行能力。
- **验收条件**：[Windows + 启用 pwsh] → [PowerShell 命令可在本地和沙箱中执行]

### REQ-L7-P2-1
- **差距编号**：L7（持久 Shell — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-bash-persistent 特性的场合，dsh-java 系统应当提供持久化 Shell 工具，跨调用保持 Shell 状态。
- **验收条件**：[启用持久 Shell] → [多次工具调用复用同一 Shell 进程，环境变量/工作目录保持]

### REQ-L8-P2-1
- **差距编号**：L8（受管环境变量 — 缺失）
- **优先级**：P2
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 `DSH_*` 前缀的受管环境变量注册表。
- **验收条件**：[注册 DSH_ 前缀变量] → [变量经注册表管理，可枚举/校验/注入子进程]

### REQ-L9-P2-1
- **差距编号**：L9（SSH — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 ssh 特性的场合，dsh-java 系统应当提供 SSH 连接、FS over SSH、沙箱 SSH 和子进程 SSH 能力。
- **验收条件**：[启用 ssh] → [可经 SSH 执行远程命令/文件操作/沙箱隔离/子进程]

### REQ-L10-P2-1
- **差距编号**：L10（Win32 进程原语 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在 Windows 平台且包含 win32-process 特性的场合，dsh-java 系统应当通过 Job Object 提供进程组控制原语。
- **验收条件**：[Windows + 启用 win32-process] → [可创建 Job Object 管理子进程组生命周期]

## 5.M Jobs/Schedule/Webhook/Workspace 域

### REQ-M1-P2-1
- **差距编号**：M1（Job 控制工具 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-jobs 特性的场合，dsh-java 系统应当提供 Job 控制工具。
- **验收条件**：[启用 tool-jobs] → [可创建/查询/取消/列举 Job]

### REQ-M2-P1-1
- **差距编号**：M2（Schedule tool — 缺模型面向 tool）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供模型面向的 schedule tool，允许模型管理提醒任务。
- **验收条件**：[模型调用 schedule tool] → [可创建/修改/取消提醒任务，提醒按计划触发]

### REQ-M3-P2-1
- **差距编号**：M3（Webhook — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 webhook 特性的场合，dsh-java 系统应当提供 Webhook 接收和 GitHub Webhook 处理能力。
- **验收条件**：[启用 webhook] → [可接收 Webhook 事件，GitHub 事件经签名校验后处理]

### REQ-M4-P1-1
- **差距编号**：M4（Workspace 实体注册表 — 需增强）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当维护 Workspace 实体注册表，持久化记录工作区并校验 Session 附着关系。
- **验收条件**：[创建/查询 Workspace] → [记录持久化，Session 附着关系经校验，孤立 Session 被识别]

## 5.N Preset/Boot/Host/Bundle 域

### REQ-N1-P2-1
- **差距编号**：N1（YAML 声明式 Preset — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 agent-preset 特性的场合，dsh-java 系统应当支持 YAML 声明式 Preset 和 persona 定义。
- **验收条件**：[提供 YAML Preset] → [系统按声明装配插件/配置/persona]

### REQ-N2-P1-1
- **差距编号**：N2（Bundle 装配 — 需增强为 bundle 体系）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当将 ExecutionProfile 增强为 bundle 装配体系，支持 base/headless/sdk-app/sdk-minimal/acp-app/web-app 等 bundle 组合。
- **验收条件**：[选择 bundle 组合] → [按 bundle 定义装配对应插件集与配置]

### REQ-N3-P0-1
- **差距编号**：N3（HMR 模块级热重载 — 需增强为模块级 HMR）
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当插件模块源码变更时，dsh-java 系统应当执行模块级热重载，刷新包清单和已加载入口标识。
- **验收条件**：[插件模块文件变更] → [HMR 检测并刷新该模块，包清单和入口标识更新，不重启进程]

### REQ-N3-P0-2
- **差距编号**：N3
- **优先级**：P0
- **EARS 类型**：Event-driven
- **需求**：当 CommonJS request 需要刷新时，dsh-java 系统应当刷新 manifest 并以 Office resolution 解析新清单。
- **验收条件**：[CommonJS request 失效] → [manifest 刷新，Office resolution 重新解析]

### REQ-N4-P0-1
- **差距编号**：N4（Plugin Manager — 需增强 runtime resolution）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统的 Plugin Manager 应当在运行时维护 resolution 映射并支持 runtime package resolution 刷新。
- **验收条件**：[运行时新增/更新插件] → [resolution 映射更新，新插件可被发现和加载]

### REQ-N5-P2-1
- **差距编号**：N5（Plugin Inventory Remote — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 plugin-inventory 特性的场合，dsh-java 系统应当提供远程插件清单发现能力。
- **验收条件**：[启用 plugin-inventory] → [可从远程清单发现并列举可用插件]

### REQ-N6-P2-1
- **差距编号**：N6（Directory Picker — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 directory-picker 特性的场合，dsh-java 系统应当提供目录选择器能力。
- **验收条件**：[启用 directory-picker] → [可交互式选择目录并返回路径]

## 5.O SDK/Feedback/Interaction/Spill 域

### REQ-O1-P0-1
- **差距编号**：O1（SDK wire 协议 — 需确认完整性）
- **优先级**：P0
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供完整的 SDK JSON-RPC stdio wire 协议，覆盖 SdkClientService 和 SdkServerService 的全部方法。
- **验收条件**：[对照 DSH sdk/protocol 方法清单] → [dsh-java 全部方法可经 JSON-RPC stdio 调用，签名对齐]

### REQ-O2-P2-1
- **差距编号**：O2（消息反馈 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 message-feedback 特性的场合，dsh-java 系统应当提供消息评分和笔记反馈能力，以及 /feedback slash 命令。
- **验收条件**：[启用 message-feedback] → [可对消息评分/记笔记，/feedback 命令打开反馈对话框]

### REQ-O3-P2-1
- **差距编号**：O3（命令注册表 — 缺失）
- **优先级**：P2
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供人类命令注册表，统一管理 slash 命令的注册与分发。
- **验收条件**：[注册 slash 命令] → [命令经注册表统一分发，可列举/帮助/禁用]

### REQ-O4-P2-1
- **差距编号**：O4（权限预设 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 permission-presets 特性的场合，dsh-java 系统应当提供权限预设能力。
- **验收条件**：[启用 permission-presets] → [可按预设批量配置工具/资源权限]

### REQ-O5-P1-1
- **差距编号**：O5（User questions timed waits — 缺 timed waits）
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当 AskUserTool 发起提问时，dsh-java 系统应当支持 timed waits，在超时后以默认值或超时结果继续。
- **验收条件**：[提问带 timeout] → [超时后以默认值/超时结果继续，不阻塞流程]

### REQ-O5-P1-2
- **差距编号**：O5
- **优先级**：P1
- **EARS 类型**：Event-driven
- **需求**：当用户在提问超时后提交 late reply 时，dsh-java 系统应当接受并处理该延迟回复。
- **验收条件**：[提问已超时后用户提交回复] → [系统接受 late reply 并处理]

### REQ-O6-P2-1
- **差距编号**：O6（Spill 溢出存储 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 spill 特性的场合，dsh-java 系统应当提供溢出存储和溢出策略能力。
- **验收条件**：[启用 spill] → [超界内容按策略转存外部存储，以引用替代]

## 5.P Document/Attachment/Deliverables 域

### REQ-P1-P2-1
- **差距编号**：P1（Office-to-PDF — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 office-to-pdf 特性的场合，dsh-java 系统应当提供 Office 文档转 PDF 能力。
- **验收条件**：[启用 office-to-pdf] → [Office 文档可转为 PDF 并返回]

### REQ-P2-P1-1
- **差距编号**：P2（内容寻址附件存储 — 需增强为内容寻址）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当将附件存储增强为内容寻址存储，以内容哈希作为存储键。
- **验收条件**：[上传相同内容附件] → [存储键为内容哈希，相同内容去重不重复存储]

### REQ-P3-P2-1
- **差距编号**：P3（交付声明 — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 tool-present 特性的场合，dsh-java 系统应当提供交付声明工具和 git 工作树快照能力。
- **验收条件**：[启用 tool-present] → [可声明交付物并生成 git 工作树快照]

## 5.Q PTC/Typert/LSP 域

### REQ-Q1-P1-1
- **差距编号**：Q1（PTC 沙箱 Node — 缺沙箱 Node 进程）
- **优先级**：P1
- **EARS 类型**：Optional
- **需求**：在包含 ptc-runtime-node 特性的场合，dsh-java 系统应当提供沙箱 Node PTC 运行时进程。
- **验收条件**：[启用 ptc-runtime-node] → [PTC 在沙箱 Node 进程中执行，与主进程隔离]

### REQ-Q2-P1-1
- **差距编号**：Q2（Typert generator — 缺 generator）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 Typert generator，能够分析 TS 项目并生成类型协议。
- **验收条件**：[对 TS 项目运行 generator] → [输出类型协议定义，可被 TypertProtocolLoader 加载]

### REQ-Q3-P1-1
- **差距编号**：Q3（LSP stdio provider — 缺 stdio provider 和模型 tool）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当提供 LSP stdio provider 和面向模型的 LSP tool。
- **验收条件**：[启用 LSP] → [LSP 经 stdio provider 通信，模型可经 LSP tool 查询定义/引用/诊断]

## 5.R Computer-use/Browser-use/ACP/Web 域

### REQ-R1-P2-1
- **差距编号**：R1（Computer-use — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 computer-use 特性的场合，dsh-java 系统应当提供计算机使用能力。
- **验收条件**：[启用 computer-use] → [可执行屏幕截图/鼠标/键盘等计算机操作]

### REQ-R2-P2-1
- **差距编号**：R2（Browser-use — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 browser-use 特性的场合，dsh-java 系统应当提供浏览器使用能力。
- **验收条件**：[启用 browser-use] → [可执行导航/点击/输入/截图等浏览器操作]

### REQ-R3-P1-1
- **差距编号**：R3（Web 搜索多 provider — 缺 Exa/Perplexity）
- **优先级**：P1
- **EARS 类型**：Ubiquitous
- **需求**：dsh-java 系统应当为 web_search 工具支持多 Provider 后端，至少包含 DeepSeek、Exa 和 Perplexity。
- **验收条件**：[配置 Exa/Perplexity Provider] → [web_search 可路由到对应 Provider 并返回结果]

## 5.S Native 系统原语 域

### REQ-S1-P2-1
- **差距编号**：S1（Landlock + flock — 缺失）
- **优先级**：P2
- **EARS 类型**：Optional
- **需求**：在包含 native-system 特性的场合，dsh-java 系统应当通过 JNA 或等价机制提供 Landlock 和 flock 系统原语。
- **验收条件**：[启用 native-system] → [可调用 Landlock 设置文件访问限制，可调用 flock 加文件锁]

---

# 6. 数据约束

## 6.1 Session 事件流

1. **formatVersion**：整数，取值范围 0~4，当前写入必须为 4。
2. **sessionId**：UUID 字符串，全局唯一，创建后不可变。
3. **events**：append-only 有序列表，每个事件含 stepIndex（单调递增整数）、type、payload、timestamp。
4. **scopeKey**：不透明字符串，消费方仅可比较相等与父子关系，不可解析内部结构。
5. **checkpoint**：可选，含 stepIndex、contextSnapshot、reason（model-request / tool-side-effect）。

## 6.2 Scope Key

1. **key**：不透明字符串，格式由系统内部定义，对外不可见内部字段。
2. **parent**：可选，指向父 scope key，构成无环父链。
3. **匹配语义**：事件 scope 为 S 时，订阅 scope 为 T 的订阅者收到事件当且仅当 S == T 或 S 是 T 的后代。

## 6.3 Tool 执行管线拦截器

1. **phase**：枚举值，取值 pre / guard / around / post / result，执行顺序固定。
2. **priority**：整数，同 phase 内按 priority 升序执行。
3. **canTerminate**：布尔，标识该拦截器是否可终止 waterfall 传播（仅 guard 和 around 阶段允许终止）。

## 6.4 投影（Projection）

1. **projectionType**：字符串，在类型表中注册，唯一。
2. **cacheKey**：由 sessionId + projectionType 派生，唯一。
3. **lastEventStepIndex**：整数，标识投影已合流到的事件 stepIndex，增量合流时从该值之后开始。

## 6.5 Token 用量快照

1. **sessionId**：关联 Session，唯一。
2. **totalTokens**：非负整数，累计 token 用量。
3. **lastEventStepIndex**：整数，标识快照覆盖到的事件 stepIndex。
4. **重放约束**：重放时若事件 stepIndex ≤ lastEventStepIndex，则不重复计入。

## 6.6 OTel 上报缓冲区

1. **byteLimit**：正整数，字节上界，可配置，默认 4MB。
2. **currentBytes**：非负整数，当前缓冲区字节数。
3. **dropPolicy**：枚举值，取值 drop-oldest（默认）/ drop-newest / block。
4. **droppedCount**：非负整数，累计丢弃事件数，必须接入监控。

## 6.7 附件（Attachment）

1. **contentHash**：字符串，内容寻址哈希（如 SHA-256），作为存储键，相同内容去重。
2. **size**：正整数，附件字节数。
3. **mimeType**：字符串，MIME 类型。
4. **唯一性**：contentHash 唯一，相同 contentHash 不重复存储。

## 6.8 Workspace 实体

1. **workspaceId**：UUID，全局唯一。
2. **rootPath**：字符串，工作区根目录绝对路径。
3. **attachedSessionIds**：Session ID 列表，每个 Session 必须校验附着关系有效。
4. **持久化**：记录必须持久化，重启后可恢复。

---

# 7. 分阶段实施计划

## Phase 1 — P0 核心底座补齐（架构对齐）

**目标**：补齐 dsh-java 缺失的核心架构特征，使其与 deepseek-harness 核心底座在架构层面对齐。本阶段完成后，dsh-java 具备 Scope 路由、waterfall 拦截、五阶段工具管线、Session 格式版本化与投影框架、LLM waterfall、重放感知 token、MCP 重连、模块级 HMR、runtime resolution、完整 SDK 协议等核心底座能力。

**涉及需求**：
- 架构抽象层：REQ-A1-P0-1, REQ-A1-P0-2, REQ-A2-P0-1, REQ-A2-P0-2, REQ-A3-P0-1, REQ-A3-P0-2, REQ-A3-P0-3
- Session 基座：REQ-B1-P0-1, REQ-B1-P0-2, REQ-B1-P0-3, REQ-B1-P0-4, REQ-B2-P0-1, REQ-B2-P0-2, REQ-B7-P0-1, REQ-B7-P0-2
- LLM 基座：REQ-C1-P0-1, REQ-C1-P0-2, REQ-C3-P0-1, REQ-C3-P0-2
- MCP 基座：REQ-D1-P0-1, REQ-D1-P0-2
- 插件/SDK 基座：REQ-N3-P0-1, REQ-N3-P0-2, REQ-N4-P0-1, REQ-O1-P0-1

**需求数**：25 条

**子阶段与工作量**：

| 子阶段 | 内容 | 工作量 |
|--------|------|--------|
| 1a 架构抽象层 | Scope 路由机制、System Prompt waterfall、Tool 五阶段管线（A1-A3） | XL |
| 1b Session 基座 | 格式版本化与 V0→V4 迁移链、投影框架与缓存、持久化抽象 seam（B1-B2, B7） | XL |
| 1c LLM 基座 | LLM stream waterfall 拦截、重放感知 token meter（C1, C3） | L |
| 1d MCP 基座 | 重连策略、mcp__server__raw 命名规范（D1） | M |
| 1e 插件/SDK 基座 | 模块级 HMR、Plugin Manager runtime resolution、SDK wire 协议完整性（N3-N4, O1） | XL |

**阶段总工作量**：XL

---

## Phase 2 — P1 核心能力增强

**目标**：在 Phase 1 架构对齐基础上，补齐重要功能域并增强现有能力。本阶段完成后，dsh-java 具备 Session 检查点/遥测/标题统计/全文搜索、LLM 重试/请求扩展/pi-ai 适配器、Context 域（AGENTS.md/@file/session 引用/时间/tmux）、Compaction 域、沙箱策略增强、子代理进程内驱动对齐、Hook CC/Codex 桥接、OTel 服务、FS 搜索工具、Schedule tool、Workspace 注册表、Bundle 体系、timed waits、内容寻址附件、PTC 沙箱 Node、Typert generator、LSP stdio provider、Web 多 Provider 等能力。

**涉及需求**：
- 核心抽象增强：REQ-A4-P1-1, REQ-A5-P1-1
- Session 增强：REQ-B3-P1-1, REQ-B3-P1-2, REQ-B4-P1-1, REQ-B4-P1-2, REQ-B5-P1-1, REQ-B5-P1-2, REQ-B5-P1-3, REQ-B6-P1-1
- LLM 增强：REQ-C2-P1-1, REQ-C2-P1-2, REQ-C4-P1-1, REQ-C5-P1-1
- MCP 增强：REQ-D2-P1-1
- Context 域新建：REQ-E1-P1-1, REQ-E2-P1-1, REQ-E3-P1-1, REQ-E4-P1-1, REQ-E5-P1-1
- Compaction 域新建：REQ-F1-P1-1, REQ-F2-P1-1, REQ-F3-P1-1
- Sandbox 增强：REQ-G4-P1-1
- Subagent 增强：REQ-H2-P1-1
- Skill/Hook 增强：REQ-J3-P1-1, REQ-J4-P1-1, REQ-J5-P1-1, REQ-J5-P1-2
- Telemetry 新建：REQ-K1-P1-1
- FS 工具增强：REQ-L3-P1-1
- Jobs/Schedule/Workspace 增强：REQ-M2-P1-1, REQ-M4-P1-1
- Preset/Bundle 增强：REQ-N2-P1-1
- SDK/Interaction 增强：REQ-O5-P1-1, REQ-O5-P1-2
- Attachment 增强：REQ-P2-P1-1
- PTC/Typert/LSP 增强：REQ-Q1-P1-1, REQ-Q2-P1-1, REQ-Q3-P1-1
- Web 搜索增强：REQ-R3-P1-1

**需求数**：41 条

**子阶段与工作量**：

| 子阶段 | 内容 | 工作量 |
|--------|------|--------|
| 2a Session 增强 | 检查点策略、OTel 遥测、标题/统计/轮次大纲、全文搜索（B3-B6） | XL |
| 2b LLM 增强 | 重试策略、请求扩展注册表、pi-ai 适配器（C2, C4-C5） | L |
| 2c Context 域新建 | AGENTS.md 加载、@file 引用、session 快照引用、时间上下文、tmux 上下文（E1-E5） | L |
| 2d Compaction 域新建 | 图像卸载、工具结果剪枝、显式压缩命令（F1-F3） | M |
| 2e 能力散点增强 | A4-A5, D2, G4, H2, J3-J5, K1, L3, M2, M4, N2, O5, P2, Q1-Q3, R3 | XL |

**阶段总工作量**：XL

---

## Phase 3 — P2 生态扩展

**目标**：扩展平台覆盖（Linux/macOS/Windows 沙箱）、生态能力（Shell/SSH/子代理/工作流/存储/技能/凭证/Webhook/Computer-use/Browser-use 等）。本阶段为可选扩展，按需启用，不影响 Phase 1/2 核心底座稳定性。

**涉及需求**：
- 跨平台沙箱：REQ-G1-P2-1, REQ-G2-P2-1, REQ-G3-P2-1, REQ-G3-P2-2
- Subagent 扩展：REQ-H1-P2-1, REQ-H3-P2-1
- Workflow/Goal：REQ-I1-P2-1, REQ-I2-P2-1, REQ-I3-P2-1, REQ-I4-P2-1
- Skill 扩展：REQ-J1-P2-1, REQ-J2-P2-1
- Identity/Credentials：REQ-K2-P2-1, REQ-K3-P2-1, REQ-K4-P2-1
- Storage/Shell/SSH/进程：REQ-L1-P2-1, REQ-L2-P2-1, REQ-L4-P2-1, REQ-L5-P2-1, REQ-L6-P2-1, REQ-L7-P2-1, REQ-L8-P2-1, REQ-L9-P2-1, REQ-L10-P2-1
- Jobs/Webhook：REQ-M1-P2-1, REQ-M3-P2-1
- Preset/Plugin 扩展：REQ-N1-P2-1, REQ-N5-P2-1, REQ-N6-P2-1
- SDK/Feedback/Spill 扩展：REQ-O2-P2-1, REQ-O3-P2-1, REQ-O4-P2-1, REQ-O6-P2-1
- Document/Deliverables：REQ-P1-P2-1, REQ-P3-P2-1
- Computer/Browser：REQ-R1-P2-1, REQ-R2-P2-1
- Native 原语：REQ-S1-P2-1

**需求数**：38 条

**子阶段与工作量**：

| 子阶段 | 内容 | 工作量 |
|--------|------|--------|
| 3a 跨平台沙箱 | Linux Landlock、macOS Seatbelt、Windows ACL + 诊断（G1-G3） | XL |
| 3b Shell/SSH/进程 | str-replace-editor、PowerShell、持久 Shell、受管环境变量、SSH、Win32 Job Object（L4-L10） | XL |
| 3c 工作流/子代理 | SDK 子代理、控制工具、PTC 编排、Ralph loop、goal-round、/goal（H1, H3, I1-I4） | XL |
| 3d 存储/技能/凭证 | Storage 域、SQLite、Office/Badge skill、匿名身份、授权 seam、PKCE（L1-L2, J1-J2, K2-K4） | L |
| 3e 其他扩展 | Native 原语、Job 工具、Webhook、Preset、Plugin Inventory、Directory Picker、Feedback、命令注册表、权限预设、Spill、Office-to-PDF、交付声明、Computer-use、Browser-use（S1, M1, M3, N1, N5-N6, O2-O4, O6, P1, P3, R1-R2） | XL |

**阶段总工作量**：XL

---

## 实施计划总览

| 阶段 | 优先级 | 需求数 | 目标 | 总工作量 |
|------|--------|--------|------|----------|
| Phase 1 | P0 | 25 | 核心底座补齐，架构对齐 | XL |
| Phase 2 | P1 | 41 | 核心能力增强 | XL |
| Phase 3 | P2 | 38 | 生态扩展 | XL |
| **合计** | — | **104** | — | **3×XL** |

### 依赖关系

- **Phase 2 依赖 Phase 1**：P1 能力增强依赖 P0 架构基座（如 Session 遥测依赖投影框架、LLM 重试依赖 waterfall）。
- **Phase 3 依赖 Phase 1**：P2 生态扩展依赖 P0 架构基座（如跨平台沙箱依赖策略解析、子代理依赖 SDK 协议）。
- **Phase 3 与 Phase 2 部分可并行**：无直接依赖的 P2 需求（如 Computer-use、Browser-use）可与 Phase 2 并行开发。

### 验收策略

1. **每阶段交付可运行版本**：Phase 1 完成发布 v0.2.0-alpha，Phase 2 完成发布 v0.2.0-beta，Phase 3 完成发布 v0.2.0。
2. **对照验证**：每个子阶段完成后，对照 DSH 对应包的行为契约编写回归测试。
3. **格式版本冻结**：Phase 1 完成后 SESSION_FORMAT_VERSION 冻结为 4，后续阶段不再变更迁移链。
