# DeepSeek Harness Java v0.1.3 开发说明

> v0.1.3 的主线是把 Agent Harness 从“能力可运行”推进到“扩展可配置、运行可观测、体验更完整”。这一版重点完成了统一配置模型、MCP/Skills/插件扩展链路增强，以及对话 UI 的流式工具调用与会话管理完善。

## 1. 版本概览

| 项目 | 内容 |
| --- | --- |
| 版本 | `v0.1.3` |
| 主线 | 统一配置中心、MCP & Skills 扩展、插件能力增强、对话 UI 完善 |
| 变更规模 | `v0.1.2...v0.1.3`：84 个文件变更，新增约 3698 行，删除约 596 行 |
| 技术栈 | Java 17、Spring Boot 3.5、DDD 六边形架构 |
| 交付形态 | 单实例 Spring Boot 应用 + 原生 JS Web 控制台 |

## 2. 发布亮点

1. **统一 Harness 配置模型**：应用级配置与扩展配置收敛到 `harness.yml`，并提供有效配置查询、启动校验和敏感信息脱敏。
2. **扩展生态增强**：MCP 支持 `stdio`、`sse`、`streamable-http` 三种接入方式；Skills 支持多目录发现、命名冲突仲裁和运行时加载。
3. **插件能力增强**：插件 SPI 增加 `PluginContext` 配置入口，Java Native 与 Node Bridge 插件可统一通过 `harness.extensions.plugins` 预置。
4. **对话体验完善**：SSE 协议补齐推理、工具调用、工具结果和结束事件；前端展示独立工具卡片、推理过程、Markdown 与代码块渲染。
5. **会话管理增强**：会话列表支持分页、重命名、删除、批量清理和会话恢复；历史消息重建时保留工具调用轨迹。
6. **质量保障补齐**：新增应用集成 Smoke Test，以及工具目录、MCP、Skills、配置校验、任务审批等单元测试。

## 3. 架构总览

v0.1.3 继续保持清晰的六边形依赖方向：

```text
Web Console / REST / SSE
        ↓
Trigger API + API DTO
        ↓
Application Case 编排
        ↓
Domain：Agent Runtime / Tool / Skill / MCP / Session / Approval
        ↓
Infrastructure：LLM / MCP / Plugin / Skill / Persistence / Sandbox
```

这一版的关键变化发生在两个位置：

- **Domain 层**新增有效配置端口与工具目录装配能力，让 Agent 运行时可以感知 MCP、Skills、Plugins 等扩展；
- **Infrastructure 层**新增 HTTP MCP Client、统一扩展属性绑定、配置校验器和配置脱敏端口，把外部扩展能力安全地接入运行时。

## 4. 统一配置中心

### 4.1 配置收敛

此前散落在 `application.yml`、`application-standalone.yml` 和临时扩展配置中的 Harness 相关配置，已收敛到：

```text
dsh-java-app/src/main/resources/harness.yml
```

标准配置覆盖：

- LLM：基础地址、API Key、上下文窗口、默认模型；
- Approval：高风险工具、强制审批 Profile、审批超时；
- Sandbox：文件系统、Shell 和默认访问模式；
- Agent：默认工作目录、上下文压缩阈值；
- Web、Storage、Terminal、Workspace：控制台和本地运行边界；
- ACP / Subagent：外部协作进程命令；
- Extensions：Skills、MCP、Plugins。

### 4.2 有效配置查询

新增诊断接口：

```http
GET /api/harness/config/effective
```

调用链为：

```text
HarnessConfigQueryController
  → HarnessConfigQueryCase
  → IHarnessEffectiveConfigPort
  → HarnessEffectiveConfigPort
```

返回的是当前 Spring Environment 中解析后的有效配置。适配器会对 API Key、Token、Password、Secret、Authorization 等敏感字段做脱敏处理，便于控制台或运维诊断时安全展示。

### 4.3 启动校验

`HarnessConfigValidator` 在应用启动前检查关键配置，避免错误配置进入运行中：

- Sandbox 模式必须是 `READ_ONLY`、`WORKSPACE_WRITE` 或 `DANGER_FULL_ACCESS`；
- MCP Server 名称必须唯一，且匹配 `[A-Za-z0-9_-]{1,32}`；
- `stdio` MCP 必须提供 `command`；
- `sse` / `streamable-http` MCP 必须提供合法的 `http` 或 `https` URL。

## 5. MCP 接入增强

### 5.1 三种传输方式

| 传输方式 | 适用场景 | 关键配置 |
| --- | --- | --- |
| `stdio` | 本地进程型 MCP Server | `command`、`args`、`env`、`cwd` |
| `sse` | 远程 HTTP 事件流服务 | `url`、`headers` |
| `streamable-http` | 流式 HTTP MCP 服务 | `url`、`headers` |

统一配置结构示例：

```yaml
harness:
  extensions:
    mcp:
      enabled: true
      servers:
        - name: remote-tools
          transport: streamable-http
          url: http://127.0.0.1:3000/mcp
        - name: filesystem
          transport: stdio
          command: npx
          args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
```

### 5.2 运行时链路

MCP Server 启动后由 `McpBootstrap` 建立连接，远端工具通过 `McpToolAdapter` 注册为 Agent 工具：

```text
harness.extensions.mcp.servers
  → McpClient（stdio / HTTP）
  → McpToolAdapter
  → Agent Tool Catalog
  → ToolCallExecutor
```

MCP 工具命名保持统一格式：

```text
mcp__<serverName>__<toolName>
```

这使 MCP 工具与内置工具、插件工具使用同一套执行、审批、事件记录链路。

## 6. Skills 系统增强

### 6.1 多目录发现

`FilesystemSkillProviderPort` 支持从以下位置发现技能：

| 配置项 | 说明 |
| --- | --- |
| `roots` | 项目或外部技能根目录 |
| `home` | 用户级技能目录 |
| `bundled-dir` | 随 Harness 分发的内置技能目录 |
| `project-enabled` | 是否启用当前项目技能 |

支持两种文件形态：

```text
<root>/<skill-name>/SKILL.md
<root>/<skill-name>.md
```

技能文件可使用 front matter 声明元数据：

```markdown
---
description: 用于执行 AI 搜索
when_to_use: 当用户需要联网搜索或获取最新资料时
---
```

### 6.2 冲突仲裁与加载

同名技能会进入 `SkillService` 合并，规则是 **rank 较小者优先**。加载技能时使用统一的 `skill` 工具：

```text
Agent 调用 skill(name)
  → SkillService 查找胜出技能
  → FilesystemSkillProviderPort 加载内容
  → 技能正文注入当前会话上下文
```

技能名必须满足 kebab-case 格式，减少不同来源技能在注册时的命名歧义。

## 7. 插件能力增强

### 7.1 插件 SPI 增加 PluginContext

`JavaHarnessPlugin` 继续保持只依赖 `dsh-java-types`，不向插件作者暴露 Spring 类型。v0.1.3 增加了：

```java
default void configure(PluginContext context) {
    for (ToolDefinition tool : tools()) {
        context.registerTool(tool);
    }
}
```

这带来两个好处：

- 保留旧的 `tools()` 能力，向下兼容；
- 插件可以在 `configure()` 中按确定性顺序注册工具或非工具能力。

### 7.2 预置插件配置

Java Native 与 Node Bridge 插件统一使用扩展配置声明：

```yaml
harness:
  extensions:
    plugins:
      install-root: ./plugins
      node-bridge-enabled: true
      preset:
        - plugin-id: sample-tools
          display-name: Sample Tools Plugin
          plugin-version: 1.0.0
          runtime-type: JAVA_NATIVE
          source-path: ./plugins/sample-tools-plugin-1.0.0.jar
          entrypoint: sample-tools-plugin-1.0.0.jar
          auto-start: true
```

插件加载后的工具名称格式为：

```text
plugin__<pluginId>__<toolName>
```

### 7.3 开发与示例

v0.1.3 同步完善了 Java 插件开发文档和智能客服插件示例，并把插件 SPI 注册文件与示例插件调整到统一的 `net.linlan.dsh.domain.spi.JavaHarnessPlugin` 契约。

## 8. 对话 UI 与 SSE 协议完善

### 8.1 SSE 事件结构

v0.1.3 的流式对话协议更完整：

| 事件 | 说明 |
| --- | --- |
| `meta` | 会话、模型、时间等起始信息 |
| `reasoning` | 模型推理文本增量 |
| `chunk` | 助手回答文本增量 |
| `step_break` | 工具调用开始，携带工具名、参数、调用 ID 和状态 |
| `tool_result` | 工具执行结果，携带结果、耗时和状态 |
| `finish` | 本轮 Agent 运行结束信号 |
| `done` | 终态事件，携带全量消息 |
| `error` | 错误帧 |

前端可以把一次回答拆分为多个气泡，并在工具发生的位置插入工具卡片。

### 8.2 工具卡片

工具卡片展示：

- 工具名与调用状态；
- 展开后的输入参数；
- 执行结果；
- 耗时；
- 成功、失败、运行中状态标识。

工具调用不再依赖拼接特殊文本，而是使用独立 SSE 事件驱动渲染，避免多步工具调用时内容错位。

### 8.3 会话管理

Web 控制台增强：

- 会话列表分页加载；
- 会话重命名；
- 单个会话删除；
- 清空当前工作区会话；
- 历史会话选择与消息懒加载；
- 历史消息恢复时重建工具轨迹。

新增恢复接口：

```http
POST /api/harness/sessions/{sessionId}/restore
```

## 9. 快速体验

构建：

```bash
mvn clean package -DskipTests
```

使用 H2 启动：

```bash
export DEEPSEEK_API_KEY='your-model-api-key'

java -jar dsh-java-app/target/dsh-java-app-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=standalone
```

打开控制台：

```text
http://localhost:8090/
```

发送流式请求：

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId": "local-agent", "message": "介绍这个项目的架构"}'
```

如启用了 API Key 鉴权，还需携带：

```text
X-API-Key: your-api-key
```

## 10. 架构图

当前版本的总体架构图见：

```text
docs/md/draw.io/release-v0.1.3-architecture.drawio
```

图中突出四个重点：

1. Web 控制台到 Agent Runtime 的主链路；
2. 统一配置中心到扩展系统的装配关系；
3. MCP、Skills、Plugin 三类扩展的接入位置；
4. Session Event Log、审批与持久化共同构成可观测、可治理的运行边界。

## 11. 升级与兼容性说明

升级到 v0.1.3 时建议检查以下内容：

1. 如果旧配置中已有 MCP 或插件配置，迁移到 `harness.extensions.*`；
2. 自研 Java 插件确认 SPI 注册文件指向 `net.linlan.dsh.domain.spi.JavaHarnessPlugin`；
3. 如果插件只实现了 `tools()`，可以继续使用；如需注册非工具能力或控制注册顺序，可改用 `configure(PluginContext)`；
4. 启动前检查 MCP 名称、传输协议、URL 或进程命令；
5. 前端如直接消费 SSE，需要处理 `reasoning`、`tool_result`、`finish` 新事件。

## 12. 版本结论

v0.1.3 不是一个纯 UI 修补版本。它补上了 Agent Harness 面向二次开发最关键的三块地基：

1. **配置统一**：扩展能力有了标准装配入口；
2. **生态开放**：MCP、Skills、Java Plugin、Node Plugin 进入同一条运行时链路；
3. **过程可视**：模型推理、工具调用、工具结果、会话状态都能被用户直接观察。

后续演进方向自然是围绕这些地基继续增强：更细粒度的权限策略、插件市场或配置导入导出、更完整的运行指标，以及多实例环境下的协调与隔离能力。
