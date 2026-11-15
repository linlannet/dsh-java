# 0.2.3 冒烟记录

- 日期：2026-11-15
- 范围：迭代二（参考源平台能力对齐）
- 环境：macOS / Java 17 / Maven

## 全量回归

| 命令 | 结果 |
| --- | --- |
| `mvn test` | 通过（exit 0） |

## 能力链路

| 链路 | 命令 | 结果 |
| --- | --- | --- |
| Office 转换 | `mvn -q -pl dsh-java-domain -am test -Dtest='OfficeToolsTest,OfficeSkillTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| Worktree 归档 | `mvn -q -pl dsh-java-domain -am test -Dtest='WorktreeArchiveCleanerTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| WebServer 鉴权 | `mvn -q -pl dsh-java-trigger -am test -Dtest='ApiKeyAuthInterceptorTest,DefaultWebAuthProviderTest,RouteAuthResolverTest,ConnectionOriginGuardTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |
| Subagent 名称链路 | `mvn -q -pl dsh-java-domain -am test -Dtest='SubagentControlToolsTest,InProcessSubagentDriverTest' -Dsurefire.failIfNoSpecifiedTests=false` | 通过 |

## 结论

上述链路单元/集成测试与全量回归均通过，无阻塞项。
