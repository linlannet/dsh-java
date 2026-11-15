# 0.2.3 错误码总表与迁移映射

> 适用范围：`dsh-java` 全部工具与子系统错误。实现基线为 `HarnessError` + `ToolExecutionResult.error={name, code}`。
> 本文是 0.2.3 迭代二的码表基线，新增错误码必须先更新本表。

## 1. 命名契约

| 项 | 规则 |
| --- | --- |
| `name` | 具体错误类名（`HarnessError.errorName()`），如 `ToolArgsError` |
| `code` | 稳定大写下划线码，不随文案变化 |
| 参数错误 | 统一 `ToolArgsError` / `INVALID_ARGS`，消息前缀 `invalid arguments: ` 并点名字段 |
| 管线兜底 | 非 `HarnessError` 异常统一 `ToolInternalError` / `TOOL_ERROR` |
| 输出位置 | `ToolExecutionResult.Failure.error = {message, info:{name, code}}` |

## 2. 通用工具码（`ToolErrorCodes`）

| code | 触发场景 |
| --- | --- |
| `ABORTED` | 工具执行被取消 |
| `ABORTED_BEFORE_DISPATCH` | 派发前已取消 |
| `UNKNOWN_TOOL` | 工具名未注册 |
| `INVALID_ARGS` | 参数缺失/非法/不支持 |
| `INVALID_TOOL_OUTPUT` | 工具输出无法解析 |
| `TOOL_TIMEOUT` | 工具执行超时 |
| `TOOL_ERROR` | 非分类异常兜底 |
| `MODEL_DOES_NOT_SUPPORT_IMAGES` | 当前模型不支持图片输入 |

## 3. 子系统码表

### 3.1 附件与存储（`StorageException` / `AttachmentError`）

| code | 触发场景 |
| --- | --- |
| `MALFORMED_MEDIUM` | 存储介质格式非法 |
| `UNIT_NOT_FOUND` | 存储单元不存在 |
| `UNIT_ALREADY_OPEN` | 存储单元重复打开 |
| `BACKEND_NOT_FOUND` | 存储后端未注册 |
| `CLOSED` | 存储已关闭 |
| `ATTACHMENT_NOT_FOUND` | 附件哈希不存在 |
| `ATTACHMENT_READ_FAILED` | 附件读取失败 |
| `ATTACHMENT_MALFORMED` | 附件内容损坏 |

### 3.2 Web 抓取（`WebFetchError`）

| code | 触发场景 |
| --- | --- |
| `WEB_FETCH_DNS_FAILED` | 域名解析失败 |
| `WEB_FETCH_CONNECTION_REFUSED` | 连接被拒绝 |
| `WEB_FETCH_CONNECTION_TIMEOUT` | 连接/请求超时 |
| `WEB_FETCH_CERTIFICATE_INVALID` | TLS 证书校验失败 |
| `WEB_FETCH_CONNECTION_INTERRUPTED` | 连接中断 |
| `WEB_FETCH_NETWORK_UNREACHABLE` | 网络不可达 |
| `WEB_FETCH_NETWORK_ERROR` | 无法归类的传输错误 |
| `WEB_ABORTED` | 请求被取消（`CancellationException`） |

### 3.3 终端（`TerminalError`）

| code | 触发场景 |
| --- | --- |
| `TERMINAL_SESSION_NOT_FOUND` | 会话不存在 |
| `TERMINAL_SESSION_NOT_RUNNING` | 会话未运行却执行写入 |
| `CLEANUP_FAILED` | 进程终止/资源清理失败 |

`TerminalBackendCleanupError` 以 `cleanupFailure` 为 `cause`，同会话启动失败放入 suppressed，`isAggregate()` 表示双阶段失败。

### 3.4 桌面操作（`ComputerUseError`）

| code | 触发场景 |
| --- | --- |
| `CUA_EXECUTION_FAILED` | 截图/鼠标/键盘端口异常 |
| `CUA_EMPTY_RESULT` | 截图返回空 |

### 3.5 浏览器（`BrowserUseError`）

| code | 触发场景 |
| --- | --- |
| `BROWSER_EXECUTION_FAILED` | 导航/点击/输入端口异常 |
| `BROWSER_EMPTY_RESULT` | 截图返回空 |

### 3.6 Worktree

| code | 触发场景 |
| --- | --- |
| `WORKTREE_NOT_GIT_REPO` | 目标目录不是 Git 仓库 |

### 3.7 Ralph 循环（`RalphLoopError`）

| code | 触发场景 |
| --- | --- |
| `RALPH_LOOP_ALREADY_RUNNING` | 循环已运行仍启动 |
| `RALPH_LOOP_NOT_RUNNING` | 循环未运行仍推进 |

### 3.8 Hook（`HookError`）

| code | 触发场景 |
| --- | --- |
| `HOOK_BLOCKED` | PreToolUse Hook 阻止执行 |

### 3.9 MCP

| code | 触发场景 |
| --- | --- |
| `MCP_RECONNECT_EXHAUSTED` | 重连次数耗尽 |
| `MCP_CONNECT_FAILED` | 扩展连接失败 |
| `MCP_CONFIG_FAILED` | 扩展配置失败 |
| `MCP_ERROR` | MCP 适配器兜底 |
| `MCP_CALL_ERROR` | MCP 工具调用失败 |

### 3.10 Subagent

| code | 触发场景 |
| --- | --- |
| `BAD_ARGS` | 参数类型错误 |
| `MISSING_PROMPT` | 缺少 prompt |
| `SUBAGENT_ERROR` | 子代理执行失败 |

### 3.11 后台任务

| code | 触发场景 |
| --- | --- |
| `JOB_NOT_FOUND` | 任务不存在 |
| `JOB_NOT_RUNNING` | 任务未运行 |

### 3.12 文件系统

| code | 触发场景 |
| --- | --- |
| `NOT_FOUND` | 文件不存在 |
| `EMPTY_FILE` | 文件为空 |
| `NO_OP_EDIT` | old_string 与 new_string 相同 |
| `STRING_NOT_FOUND` | 待替换字符串不存在 |

### 3.13 Shell

| code | 触发场景 |
| --- | --- |
| `COMMAND_BLOCKED` | 沙箱/守卫拦截命令 |

### 3.14 其他工具

| code | 触发场景 | 状态 |
| --- | --- | --- |
| `MISSING_PARAM` | 缺少参数 | 遗留码，仅存量工具使用；新代码用 `INVALID_ARGS` |
| `EXECUTION_ERROR` | 工具执行异常 | 遗留码，仅存量工具使用；新代码用 `HarnessError` 子类 |
| `INVALID_TODO` | todo 条目非法 | 保留 |
| `NO_CHANNEL` | 无用户问答通道 | 保留 |

### 3.15 Office Kit（`OfficeKitError`）

| code | 触发场景 |
| --- | --- |
| `OFFICE_KIT_UNAVAILABLE` | LibreOffice/soffice 未安装或不可执行 |
| `OFFICE_CONVERSION_FAILED` | 转换超时、非零退出、输出缺失或目录不可建 |
| `OFFICE_UNSUPPORTED_FORMAT` | 输入或目标格式不在支持列表 |
| `OFFICE_INPUT_NOT_FOUND` | 输入文件不存在 |

### 3.16 Web 计费与凭据（`WebBillingError`）

| code | 触发场景 |
| --- | --- |
| `WEB_CREDENTIAL_MISSING` | 计费模式没有可用凭据 |
| `WEB_ACCOUNT_UNAVAILABLE` | `account` 模式缺少账户凭据 |

### 3.17 插件管理（`PluginManagementError`）

| code | 触发场景 |
| --- | --- |
| `unexpected-package` | 带 expected 的安装命中其他包或版本 |

## 4. 旧 → 新映射

| 旧 | 新 | 范围 | 说明 |
| --- | --- | --- | --- |
| `MISSING_PARAM` | `INVALID_ARGS`（`ToolArgsError`） | Shell/FS 全部参数入口 | 消息统一 `invalid arguments: <field> is required` |
| `<field> parameter is required` | `invalid arguments: <field> is required` | Shell/FS | 文案规范 |
| `IMAGE_UNSUPPORTED_MODEL` | `MODEL_DOES_NOT_SUPPORT_IMAGES` | 图片输入 | 仓库内无旧码调用点，仅统一常量 |
| `JOB_BACKGROUND_DISABLED` | 删除 | 后台执行 | 仓库内无调用点 |
| `StorageException(UNIT_NOT_FOUND, "attachment not found")` | `AttachmentError(ATTACHMENT_NOT_FOUND)` | 附件读取 | 保持 `StorageException` 父类兼容 |
| `IllegalArgumentException("terminal session not found")` | `TerminalError(TERMINAL_SESSION_NOT_FOUND)` | 终端 | 统一分类 |
| `IllegalStateException("terminal session is not running")` | `TerminalError(TERMINAL_SESSION_NOT_RUNNING)` | 终端 | 统一分类 |
| 清理失败 `cause=null` | `TerminalBackendCleanupError(CLEANUP_FAILED)` | 终端关闭 | `cause` + suppressed 聚合启动/清理失败 |
| 未知异常 `name=code` | `ToolInternalError` / `TOOL_ERROR` | 工具管线 | 保留原始 message 与 cause |
| Hook 失败 `name=HOOK_BLOCKED` | `HookError` / `HOOK_BLOCKED` | 工具管线 | name 与 code 分离 |
| Web 抓取返回成功文本 `Fetch error: ...` | `WebFetchError(WEB_FETCH_*)` | 本地抓取 | 失败不再伪装为成功结果 |

## 5. 约束

1. 新增子系统错误必须继承 `HarnessError`，错误码以常量形式定义在错误类上。
2. 新增参数校验必须抛 `ToolArgsError`，不得新增 `MISSING_PARAM`、`BAD_ARGS` 等同类码。
3. 新增执行失败不得新增 `EXECUTION_ERROR`，应落到具体子系统错误类或 `ToolInternalError`。
4. 码表变更须同步本文件与 `CHANGELOG.md`。
