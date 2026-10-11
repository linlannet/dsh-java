# 领域模块课程 · DeepSeek Harness Java

> 一份**教程风格**的领域模块讲解合集：9 章、37 个模块，逐个讲清楚"为什么有这个模块 / 关键源码长啥样 / 怎么走 / 怎么用"。

---

## 设计原则

| 原则 | 落地 |
| --- | --- |
| **文件名不带序号** | 章顺序由本 README 决定；新增 / 调序只需改一行 README，文件不动 |
| **两套模板对齐"模块体量"** | 核心模块用"完整 7 节"；工具族 / 平台小模块用"轻量 5 节" |
| **不重复既有文档** | 参考既有 `domain-design.md`（叙事）/ `domain-case-deep-dive.md`（深挖）/ `domain-modules-reference.md`（字典）三份文档 |

---

## 章节目录

> **顺序在本 README 里维护**。要调整一章顺序：把该行挪到新位置即可，文件不动；要加新章：放新文件 + 在 README 选位置插一行。

| # | 章节 | 文件 | 模块数 | 模板 |
| --- | --- | --- | --- | --- |
| 01 | Agent 执行核心 | [`./agent.md`](./agent.md) | 4 | 全部"完整 7 节" |
| 02 | 任务与目标 | [`./task-and-goal.md`](./task-and-goal.md) | 4 | 全部"完整 7 节" |
| 03 | 会话与事件日志 | [`./session-and-shared.md`](./session-and-shared.md) | 5 | 全部"完整 7 节" |
| 04 | LLM 与运行时 | [`./llm-and-runtime.md`](./llm-and-runtime.md) | 5 | `llm` 完整；`runtime.*` 轻量 |
| 05 | 工具与执行 | [`./tool-and-execution.md`](./tool-and-execution.md) | 13 | `tool.service` 完整；12 族轻量 |
| 06 | 插件三段 | [`./plugin.md`](./plugin.md) | 4 | 全部"完整 7 节" |
| 07 | 治理与后台 | [`./governance-and-jobs.md`](./governance-and-jobs.md) | 3 | 全部"完整 7 节" |
| 08 | 运行时平台能力 | [`./runtime-platform.md`](./runtime-platform.md) | 10 | 全部"轻量 5 节" |
| 09 | 协议与工作流 | [`./protocol-and-workflow.md`](./protocol-and-workflow.md) | 3 | 全部"完整 7 节" |

**合计 37 个模块 / 9 章**。

> 注：05 章的 "13 模块"包含 `tool.service` 一个核心 + 12 个工具族；12 个工具族本身按"内置三族 / 协议族 / 平台族"三组讲，避免 13 节 → 改组内表。

---

## 每章统一骨架

```text
学习目标（bullet 3-5 条）
    ↓
章节导读（这一章解决什么问题 / 关键依赖关系图）
    ↓
按模块分节
   每个模块：
   ├── 「完整 7 节」 或 「轻量 5 节」 模板
    ↓
小结（3-5 条把整章要点拉齐）
    ↓
思考题（2-4 题，难度递增 Easy/Medium/Hard）
```

---

## 模板规范

### A. 完整 7 节（核心模块用）

```text
① 解决什么问题（背景 / 动机）
② 关键源码位置（文件路径 + 行号区间）
③ 关键类与签名（表格）
④ 代码片段 / 流程图（≤ 30 行）
⑤ 设计意图（callout）
⑥ 与谁打交道（消费方 / 提供方）
⑦ 注意事项（1-3 条）
```

### B. 轻量 5 节（小模块 / 工具族用）

```text
① 一句话作用
② 关键源码位置（文件路径）
③ 关键类与签名（≤ 5 行）
④ 与谁打交道（消费方 / 提供方）
⑤ 注意事项（0-1 条）
```

---

## 阅读路径建议

| 你是谁 | 怎么读 |
| --- | --- |
| **第一次接触项目** | 先读 `domain-design.md`（叙事）→ 按 01→09 顺序读本合集 → 字典 `domain-modules-reference.md` 查细节 |
| **要改一个具体模块** | `domain-modules-reference.md` 定位模块 → 跳到对应章节读"源码位置 + 代码片段" |
| **要解决跨模块问题** | 例：要排查"为什么工具没执行"，看 05（工具）→ 01（Agent）→ 03（事件） |
| **要扩展能力** | 例：加新工具 → 05 + 06（插件三段）+ 07（Hook / 守卫） |

---

## 与既有 3 份文档的关系

| 文档 | 视角 | 本合集对应章节 |
| --- | --- | --- |
| [`../domain-design.md`](../domain-design.md) | 叙事 | 01-09 章节导读 + 总关系图的总来源 |
| [`../domain-case-deep-dive.md`](../domain-case-deep-dive.md) | 深挖 | 选看 01 / 02 / 04 / 05 这几章的实现细节 |
| [`../domain-modules-reference.md`](../domain-modules-reference.md) | 字典 | 任意章节的补充查询 |

---

## 文件清单

```
docs/md/domain-course/
├── README.md                       ← 本文件（索引 / 模板规范 / 路径）
├── agent.md                        ← 01
├── task-and-goal.md                ← 02
├── session-and-shared.md           ← 03
├── llm-and-runtime.md              ← 04
├── tool-and-execution.md           ← 05
├── plugin.md                       ← 06
├── governance-and-jobs.md          ← 07
├── runtime-platform.md             ← 08
└── protocol-and-workflow.md        ← 09
```

---

> **配套代码索引**：
> - Maven 模块：`dsh-java-domain/`
> - 根包：`net.linlan.dsh.domain`
> - 测试与触发器：`dsh-java-case/`、`dsh-java-trigger/`
> - 持久化与端口实现：`dsh-java-infrastructure/`
