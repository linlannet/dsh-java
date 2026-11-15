# DeepSeek Harness Java v0.2.3 开发说明

> 版本：`v0.2.3` | 开发周期：2026-11-06 ~ 2026-11-12 | 基线：v0.2.2

## 1. 版本概览

v0.2.3 以**移动端接入**为主线，包含三块内容：

1. **移动设备访问开关 + 设备配对重构回归**：该功能在 v0.2.2 修订期（2026-11-05 ~ 11-06）整体下线，本版本按新架构回归——DTO 统一 `*DTO` 后缀、领域模型聚合化（`DeviceCredentialAggregate`）、新增 `mobile-access` 开关（默认关闭）与单元测试。
2. **微信小程序登录（全新）**：`wx.login` 临时 code 经微信 `jscode2session` 换取 `openid`，签发 2 小时会话令牌；未配置凭据时返回 `50300`，不降级匿名登录。
3. **AI 可发现性（全新）**：`AGENTS.md`、`llms.txt`、`docs/md/ai-discoverability.md` 三份机器可读入口。

另包含：全模块 pom 版本 `0.2.2 → 0.2.3`、`ApiKeyAuthInterceptor` 恢复 `devt_` 设备 token 校验、Web 控制台移动访问/设备管理适配。

## 2. 版本亮点

### 2.1 移动访问开关（默认关闭）

- `GET/PUT /api/devices/mobile-access`，`AtomicBoolean` 进程内开关；
- 关闭时拒绝配对码生成与配对（`40300`），并**清空全部未消费配对码**；
- 默认关闭杜绝静默开放，安全边界由桌面端显式控制。

### 2.2 设备配对与凭据生命周期

- 配对码：6 位数字、10 分钟有效、一次性消费；按设备指纹 60 秒 10 次限流（`42900`）；
- 凭据：`devt_<deviceId>_<uuid>` 仅签发时返回一次，服务端只存 SHA-256 摘要；
- 生命周期：吊销立即失效、轮换旧 token 立即失效、指纹唯一防重复配对（`40003`）；
- 鉴权闭环：`ApiKeyAuthInterceptor` 对 `devt_` 前缀无条件强校验（不依赖主 Key 是否配置），失败 401。

### 2.3 微信小程序登录

- 链路：`POST /api/miniapp/login {code}` → `jscode2session`（JDK HttpClient，8s 超时）→ `openid` → 签发 `mini_` 会话令牌（2 小时）；
- 错误码分级：未配置 `50300`、微信拒绝 `40101`、缺 openid `40102`、网络异常 `50200`；
- 凭据管理：`dsh.miniapp.wechat.appid/secret` 经环境变量 `DSH_MINIAPP_WECHAT_APPID/SECRET` 注入，secret 不落盘、不入日志。

### 2.4 AI 可发现性

- `llms.txt`：面向 LLM 的项目摘要（能力、技术栈、命令、模块地图、关联仓库）；
- `AGENTS.md`：AI 编码助手的构建/测试命令、模块布局、代码约定与禁止事项；
- `docs/md/ai-discoverability.md`：dsh-java 系列五仓库（dsh-java / plugins / desktop / mobile / release）的 About 描述与 topics 配置清单。

## 3. 任务映射（spec §5 → 实现）

| spec 能力 | 实现落位 | 状态 |
| --- | --- | --- |
| §5.1 移动访问开关 | `DevicePairingUseCase`（`AtomicBoolean`）+ `DeviceController` | 已完成 |
| §5.2 配对码与配对 | `DevicePairingUseCase`（配对码/限流/指纹查重/签发） | 已完成 |
| §5.3 设备凭据管理 | `DevicePairingUseCase` + `DeviceRepository` | 已完成 |
| §5.4 设备 Token 鉴权 | `ApiKeyAuthInterceptor` + `validateDeviceToken` | 已完成 |
| §5.5 小程序登录 | `MiniAppController` + `MiniAppLoginUseCase` | 已完成 |
| §5.6 版本治理 | 全部 pom 0.2.3、CHANGELOG、四件套 | 已完成 |
| §5.7 AI 可发现性 | `AGENTS.md` / `llms.txt` / `ai-discoverability.md` | 已完成 |

## 4. 验证结论

- `mvn clean install` 全模块构建通过；ArchUnit `triggerMustOnlyDependOnApiAndCase` 等架构规则回归通过；
- `DevicePairingUseCaseTest` 覆盖开关默认值、关闭拒绝、配对码一次性/过期、指纹重复、限流、轮换与吊销后失效等关键用例；
- 桌面端端到端链路验证通过：开开关 → 生成配对码 → 配对成功 → `devt_` 请求通过拦截器 → 吊销/轮换后 401。

## 5. 已知限制

1. **内存态存储**：设备凭据与小程序会话均为进程内存储，重启即丢、不支持多实例部署；数据库仓储为后续版本任务（见 tasks §10）。
2. **小程序真机链路待验证**：`MiniAppLoginUseCaseTest` 单测与真机 `wx.login` 端到端验证待补（依赖微信凭据配置）。
3. **openid 未绑定业务账号**：本版本仅完成登录换取会话令牌。
4. **设备 token 无细粒度权限**：通过校验即等价主 Key 放行，不做按设备裁剪。
5. **0.2.3 详情路径**：`CHANGELOG.md` 中 0.2.3 的详情链接最初误写为 `docs/spec/0.2.2/`，已随四件套归档至 `docs/spec/0.2.3/`。

## 6. 依赖与体积增量

- **新增第三方依赖：无**。HTTP 调用使用 JDK `java.net.http.HttpClient`，JSON 使用既有 Jackson；
- **新增数据库表：无**（进程内存储）；
- **新增 Maven 模块：无**（全部落在既有 7 模块 + 2 插件内）；
- 制品体积增量可忽略（新增类约 15 个 + 3 份文档）。

## 7. 迭代二范围（参考源平台能力对齐，规划中）

> 状态：**未实现**，仅完成需求、设计与任务拆解；代码落地与验证记录待本轮开发完成后补入本节。

| 能力域 | 参考源升级项 | 归属 |
| --- | --- | --- |
| 统一错误码与 `HarnessError` | `attachment-error-codes` 等 5 项 | spec §8.1 / design §3.2 / tasks §11.1 |
| Subagent 名称寻址 | `subagent-names`、`subagent-interrupt-default` | spec §8.2 / design §3.3 / tasks §11.2 |
| Office 能力工具化 | `office-kit-package` 等 3 项 | spec §8.3 / design §3.4 / tasks §11.3 |
| Web 计费与凭据来源 | `web-search-billing` | spec §8.4 / design §3.5 / tasks §11.4 |
| Worktree 归档清理 | `archive-worktrees` | spec §8.5 / design §3.6 / tasks §11.5 |
| 运行时上下文提醒 | `runtime-context-reminders` | spec §8.6 / design §3.7 / tasks §11.6 |
| Plugin Bundle 更新 | `plugin-update-method` | spec §8.7 / design §3.8 / tasks §11.7 |
| DeepSeek 文件上传超时 | `deepseek-files-timeout` | spec §8.8 / design §3.9 / tasks §11.8 |
| WebServer 默认鉴权 | `connection-page-peer`（部分） | spec §8.9 / design §3.10 / tasks §11.9 |
| 会话状态与事件写入 | `session-status-package`（部分）、`session-append-overloads` | spec §8.10 / design §3.11 / tasks §11.10 |
| CUA / PTC 部分对齐 | `cua-agents-without-preset`、`ptc-inspect-output` | spec §8.11–§8.12 / design §3.12 / tasks §11.11 |

**不纳入**：Cordis/包导出、Client/Desktop、macOS AX、Python/Node 运行时、Forge、账号、文档技能，原因见 spec §8.13。

**发布口径**：本节能力未完成前，0.2.3 不标记为"已发布"；完成后需补齐验证结论、已知限制与依赖/体积增量。
