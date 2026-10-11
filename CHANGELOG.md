# 版本

> 各版本详情见 `docs/md/release-vX.Y.Z-development-notes.md`；以下按 7 个模块记录包级功能更新。

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