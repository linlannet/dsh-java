# dsh-java 工程知识图谱分析报告

> 由 graphify 自动生成 · 2026-10-15
> 扫描根：`/Users/mac/liubai/linlannet/dsh-java`
> Git commit：`3b9f729`

---

## 1. 总览

| 指标 | 数值 |
|------|------|
| 代码文件 | 1,271 |
| 节点总数 | 10,867 |
| 边总数 | 34,179 |
| 社区数 | 385 |
| 置信度 EXTRACTED | 30,845（90.3%） |
| 置信度 INFERRED | 3,334（9.7%） |

---

## 2. 模块分布（DDD 分层架构）

```
dsh-java-domain          5,581 节点  ████████████████████████████  (核心领域层)
dsh-java-infrastructure  2,208 节点  ███████████                   (基础设施层)
dsh-java-case              769 节点  ████                          (用例层)
dsh-java-app               511 节点  ██▌                           (应用启动层)
dsh-java-trigger           486 节点  ██▌                           (触发器/API控制器)
dsh-java-api               263 节点  █▎                            (API响应层)
dsh-java-types             171 节点  █                             (类型定义层)
plugins                     54 节点  ▎                             (插件)
```

**架构特征**：经典 DDD 六边形分层，domain 层占 51.4%，核心业务逻辑内聚，infrastructure 层提供适配器实现。

---

## 3. 关系类型分布

| 关系 | 数量 | 说明 |
|------|:----:|------|
| references | 10,139 | 引用（类/注解引用） |
| imports | 7,650 | 导入（import 语句） |
| calls | 7,060 | 方法调用 |
| method | 6,599 | 方法定义归属 |
| contains | 1,972 | 包含（类含方法/字段） |
| implements | 415 | 接口实现 |
| case_of | 194 | 用例关联 |
| inherits | 77 | 类继承 |
| indirect_call | 54 | 间接调用（推断） |
| depends_on | 13 | 模块依赖 |

---

## 4. 核心概念节点（连接度 Top 20）

| 节点 | 连接度 | 角色 |
|------|:------:|------|
| org.junit.jupiter.api.Test | 1,741 | 测试注解 |
| org.junit.jupiter.api.DisplayName | 1,194 | 测试显示名 |
| list | 486 | List 类型 |
| org.springframework.stereotype.Service | 362 | Service 注解 |
| **Response** | **284** | **API 统一响应** |
| map | 241 | Map 类型 |
| **SessionEvent** | **192** | **会话事件（核心实体）** |
| NoContext | 183 | 无上下文标记 |
| org.slf4j.Logger | 175 | 日志器 |
| optional | 170 | Optional 类型 |
| app.js | 159 | 前端入口 |
| **TextBlock** | **149** | **文本块（消息内容）** |
| **SessionLog** | **147** | **会话日志（核心实体）** |
| instant | 145 | Instant 类型 |
| **ToolExecutionResult** | **132** | **工具执行结果** |
| assertions | 126 | 断言 |
| **Message** | **116** | **消息（核心实体）** |
| **ToolDefinition** | **116** | **工具定义** |
| ObjectMapper | 110 | JSON 序列化 |
| arraylist | 110 | ArrayList 类型 |

---

## 5. 社区分析（Top 20 模块聚类）

| 社区 | 节点数 | 主要模块 | 核心概念 | 语义 |
|:----:|:------:|----------|----------|------|
| 0 | 273 | domain | list, map, optional, instant | 基础类型工具 |
| 1 | 251 | trigger, api | Response, RestController, RequestMapping | **REST API 层** |
| 2 | 221 | domain | DisplayName, 各种 Test | 单元测试集群 |
| 3 | 191 | domain | **SessionEvent, SessionLog, SessionHeader** | **会话事件日志** |
| 4 | 164 | domain | Test, ContractTest, ComputerUseToolTest | 核心契约测试 |
| 5 | 157 | domain, types | **TextBlock, ToolDefinition, AgentToolCatalog** | **工具目录与文本块** |
| 6 | 145 | case | Service, StrategyHandler, Logger | **策略处理器** |
| 7 | 134 | domain | **StreamChunk, GenerateOptions, ILlmRuntimePort** | **LLM 运行时抽象** |
| 8 | 127 | infrastructure | HttpMcpClient, SearchResult | **HTTP MCP 客户端** |
| 9 | 113 | domain | **AgentOptions, AgentRun, AgentRunFactory** | **Agent 运行引擎** |
| 10 | 111 | case, trigger | TerminalSession, TerminalPipeline | **终端会话管道** |
| 11 | 111 | domain | **Message, ReactLoopAgent** | **ReAct 循环 Agent** |
| 12 | 102 | infrastructure | HarnessApplicationConfig, Bean | **Spring Bean 配置** |
| 13 | 99 | infrastructure | ExtensionManagementService | **扩展管理** |
| 14 | 99 | case | **CasePipeline, SessionPipeline** | **用例管道** |
| 15 | 93 | domain | ProjectionRegistry, ProjectionCache | **投影注册** |
| 16 | 92 | case, trigger | ModelSetting, RuntimePipeline | **模型设置** |
| 17 | 89 | infrastructure | CollaborationService, ICollaborationDao | **协作服务** |
| 18 | 78 | domain, types | **ToolExecutionResult, ToolRunContext** | **工具执行** |
| 19 | 75 | domain | ScopeEventDispatcher, ScopeKey | **作用域事件** |

---

## 6. 架构洞察

### 6.1 DDD 六边形分层

```
                    ┌─────────────────┐
                    │  dsh-java-app   │  Spring Boot 启动 + Bean 装配
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────┴───┐  ┌──────┴─────┐  ┌────┴──────┐
     │ dsh-java-  │  │ dsh-java-  │  │ dsh-java- │
     │  trigger   │  │    api     │  │   case    │
     │ (控制器)   │  │ (响应封装) │  │ (用例管道) │
     └────────┬───┘  └──────┬─────┘  └────┬──────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────┴────────┐
                    │ dsh-java-domain │  核心领域模型 + 服务接口
                    │   (5581 节点)   │  SessionEvent / Agent / Tool / Message
                    └────────┬────────┘
                             │
                    ┌────────┴────────────┐
                    │ dsh-java-           │  适配器实现
                    │ infrastructure      │  LLM Runtime / MCP Client / DAO
                    └────────────────────┘
```

### 6.2 核心域概念图谱

```
AgentRun ──uses──→ ToolDefinition ──produces──→ ToolExecutionResult
   │                    │                          │
   │                    │                          │
  AgentOptions    AgentToolCatalog            ToolRunContext
   │                    │
   │                    │
ReactLoopAgent ──sends──→ Message ──contains──→ TextBlock
   │                    │
   │                    │
   └── logs ──→ SessionEvent ──→ SessionLog
```

### 6.3 关键发现

1. **领域层内聚度高**：domain 占 51.4%，SessionEvent/SessionLog/Message/Agent 等核心实体高度连接，表明领域模型设计良好
2. **测试覆盖广泛**：JUnit Test 连接度 1741（全图最高），DisplayName 1194，测试节点分布在多个社区中
3. **工具系统完整**：ToolDefinition → ToolExecutionResult → ToolRunContext 形成完整工具执行链路，AgentToolCatalog 管理工具目录
4. **Agent 引擎成熟**：AgentRun/AgentRunFactory/ReactLoopAgent 构成 Agent 运行引擎，支持 ReAct 循环模式
5. **LLM 抽象规范**：ILlmRuntimePort / StreamChunk / GenerateOptions 定义了 LLM 运行时抽象接口
6. **MCP 协议支持**：HttpMcpClient 社区表明工程支持 Model Context Protocol
7. **管道模式贯穿**：CasePipeline / SessionPipeline / TerminalPipeline / RuntimePipeline 统一使用管道模式
8. **扩展性设计**：ExtensionManagementService + plugins 模块支持插件扩展

---

## 7. 输出文件

| 文件 | 大小 | 说明 |
|------|------|------|
| `graphify-out/graph.json` | 21 MB | 完整知识图谱（节点+边+社区） |
| `graphify-out/manifest.json` | 351 KB | 提取清单 |
| `graphify-out/.graphify_analysis.json` | 1.4 MB | 分析元数据 |
| `graphify-out/cache/` | — | AST 提取缓存 |

---

## 8. 查询指南

已生成的 `graph.json` 支持以下查询：

```bash
# BFS 查询（广度优先）
uv tool run --from graphifyy graphify query "Agent 运行流程" 

# DFS 查询（深度优先，追踪路径）
uv tool run --from graphifyy graphify query "SessionEvent 如何持久化" --dfs

# 最短路径
uv tool run --from graphifyy graphify path "AgentRun" "SessionLog"

# 节点解释
uv tool run --from graphifyy graphify explain "ReactLoopAgent"
```
