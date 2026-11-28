# 0.2.3 冒烟记录

- 全量回归日期：2026-11-15
- 连接器增量日期：2026-11-28
- 范围：迭代二（参考源平台能力对齐）+ 多渠道连接器 Provider
- 环境：macOS / Java 17 / Maven

## 全量回归（2026-11-15）

| 命令 | 结果 |
| --- | --- |
| `mvn test` | 通过（exit 0） |
| `mvn clean package` | BUILD SUCCESS，产物 `dsh-java-app/target/dsh-java-app-0.2.3-exec.jar` |

## 连接器增量（2026-11-28）

| 链路 | 命令 | 结果 |
| --- | --- | --- |
| Provider 取号（飞书/企微/钉钉） | `mvn -q -pl plugins/dsh-connector-providers -am test -Dtest='FeishuAccountProviderTest,WeComAccountProviderTest,DingTalkAccountProviderTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| 领域状态机与注册表 | `mvn -q -pl dsh-java-domain,dsh-java-trigger -am test -Dtest='ConnectorAccountRegistryTest,ConnectorAccountServiceTest,ConnectorApiTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |

覆盖点：

1. 缺凭据返回 `*_CREDENTIALS_REQUIRED`，不发起外部请求。
2. 上游拒绝/响应缺字段返回 `*_TOKEN_REJECTED`；网络/5xx 返回 `*_TOKEN_FETCH_FAILED` 并标记可重试。
3. 注册表重复 `connectorId` 拒绝；`unregister` 幂等并清状态。
4. OAuth `state` 10 分钟 TTL、一次性；回调缺 code/未发起/超时/不匹配分别返回对应码。
5. 过期授予被动刷新；刷新失败进入 `ERROR` 并保留诊断。
6. DTO 仅含状态/诊断/授权 URL，不含 accessToken/refreshToken/授权码。

## 能力链路（2026-11-15）

| 链路 | 命令 | 结果 |
| --- | --- | --- |
| Office 转换 | `mvn -q -pl dsh-java-domain -am test -Dtest='OfficeToolsTest,OfficeSkillTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| Worktree 归档 | `mvn -q -pl dsh-java-domain -am test -Dtest='WorktreeArchiveCleanerTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| WebServer 鉴权 | `mvn -q -pl dsh-java-trigger -am test -Dtest='ApiKeyAuthInterceptorTest,DefaultWebAuthProviderTest,RouteAuthResolverTest,ConnectionOriginGuardTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| Subagent 名称链路 | `mvn -q -pl dsh-java-domain -am test -Dtest='SubagentControlToolsTest,InProcessSubagentDriverTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |

## 未覆盖

1. 真实飞书/企微/钉钉租户凭据取号未联调（需企业应用凭据与网络）。
2. 插件热重载/停止时的 Provider 回收仅由单测覆盖，未做运行期重启验证。
3. 多实例共享令牌缓存未验证（当前为进程内实现）。

## 结论

全量回归与连接器增量单测均通过；真实厂商凭据与运行期热重载待环境验证，无代码阻塞项。