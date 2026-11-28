# AI 可发现性配置清单

> 目的：让 AI 编程助手、代码检索与搜索引擎更容易发现并正确理解 DSH Java 系列工程。
> 本文档给出各仓库的 GitHub About 描述、topics 与已落地的机器可读入口，供在仓库后台粘贴/核对。

## 已落地的仓库内文件

| 仓库 | `llms.txt` | `AGENTS.md` | README TL;DR | package.json 元数据 |
| --- | --- | --- | --- | --- |
| dsh-java | ✅ | ✅ | ✅ | — |
| dsh-java-plugins | ✅ | ✅ | ✅ | — |
| dsh-desktop | ✅ | ✅ | ✅ | ✅ |
| dsh-mobile | ✅ | ✅ | ✅ | ✅（含 miniapp） |
| dsh-java-release | ✅ | ✅ | ✅ | — |

`llms.txt`：面向 LLM 的项目摘要（定位、能力、技术栈、命令、目录、文档链接）。
`AGENTS.md`：面向 AI 编码助手的构建/测试命令、目录约束与禁止事项。

## GitHub About 描述（建议粘贴）

| 仓库 | Description |
| --- | --- |
| dsh-java | A self-contained AI Agent Harness on Java 17 + Spring Boot: ReAct, tools, approvals, plugins (Java/Node/MCP), event sourcing, web console. |
| dsh-java-plugins | Java native plugins for dsh-java: MySQL, OceanBase, Dameng and Mall, with plugin development contract and reference implementations. |
| dsh-desktop | DSH Java desktop client built with Tauri 2 + React 19: bundled agent runtime, digital-human collaboration and A2A protocol. |
| dsh-mobile | DSH Java multi-platform shell: Android/iOS (Tauri 2 Mobile), HarmonyOS (ArkWeb) and WeChat mini-program share one React H5 artifact. |
| dsh-java-release | Ready-to-run release bundle for DSH Java 0.2.3: artifacts + four database plugins + built-in skills, start with one command. |

## Topics（各仓库统一粘贴）

```text
agent
agent-harness
react-loop
ai-agent
llm
mcp
spring-boot
java
tauri
react
plugin
digital-human
harmonyos
arkweb
wechat-miniapp
taro
```

按仓库裁剪：
- dsh-java：`agent`、`agent-harness`、`react-loop`、`ai-agent`、`llm`、`mcp`、`spring-boot`、`java`、`plugin`
- dsh-java-plugins：`plugin`、`java`、`spring-boot`、`mysql`、`oceanbase`、`dameng`
- dsh-desktop：`tauri`、`react`、`desktop`、`ai-agent`、`digital-human`、`a2a`
- dsh-mobile：`tauri`、`mobile`、`harmonyos`、`arkweb`、`wechat-miniapp`、`taro`
- dsh-java-release：`release`、`agent-harness`、`spring-boot`、`mysql`、`plugin`、`skills`

## 仍需在 GitHub 后台完成

1. 为每个仓库设置 About 描述与 topics（上方表格）。
2. 设置 Homepage 指向文档站或主仓库。
3. 上传 Social preview 图（建议用主界面截图）。
4. 打版本 tag（如 `v0.2.2`）并创建 GitHub Release，附 CHANGELOG。
5. 为 5 个仓库添加统一组织级 `.github` profile README，串起全部工程。

## 后续增强（可选）

- 文档站 + `sitemap.xml` + `robots.txt` + `llms-full.txt`
- OpenAPI 导出（`/v3/api-docs`）供 AI 生成调用代码
- 提交到 awesome-agent / MCP 目录 / Docker Hub / Maven Central
- 官网加 Schema.org JSON-LD（`SoftwareApplication`）