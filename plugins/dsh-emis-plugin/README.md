# dsh-emis-plugin — 企业身份鉴权插件

> 状态：P0（只做鉴权工具）。身份事实来源为 **linlan-emis**；插件不直连数据库、不复制用户体系、不处理 HS256 平台令牌。

## 能力

| 工具 | 说明 |
| --- | --- |
| `whoami` | 校验 RS256 OIDC access token，返回 `subject/userId`、`tenantId`、`roleLevel`、`userName`、`scopes`、`expiresAt` |
| `scope_check` | 校验 token 是否包含指定 scope（如 `binding:write`、`user:read`） |

- 完整工具名由宿主角标加前缀：`plugin__dsh-emis-plugin__whoami`、`plugin__dsh-emis-plugin__scope_check`。
- 只接受 **RS256**（emis OIDC）；**HS256 平台令牌拒绝**，错误码 `EMIS_TOKEN_INVALID`。
- 验签使用 emis `/oauth2/jwks`，按 `kid` 缓存（默认 10 分钟）；JWKS 不可达时 fail-closed。
- 不记录 token、secret、PEM；错误信息只含稳定错误码与安全文案。

## 配置

在宿主 preset / plugin.yaml 中提供（均非敏感）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `emis.base-url` | `http://localhost:8080` | emis 基地址（生产 HTTPS） |
| `emis.issuer` | 同 `emis.base-url` | 校验 token `iss` |
| `emis.jwks-path` | `/oauth2/jwks` | JWKS 路径 |
| `emis.timeout-ms` | `3000` | HTTP 超时 |
| `emis.jwks-cache-ms` | `600000` | JWKS 缓存时长 |

> 插件不需要 client_secret；鉴权基于令牌签名与 iss/exp，不调用 introspect。

## 构建与使用

```bash
cd dsh-java
mvn -pl plugins/dsh-emis-plugin -am -DskipTests package
# 产物：plugins/dsh-emis-plugin/target/dsh-emis-plugin-0.2.3.jar
```

- 将 JAR 放入 harness `plugins/` 目录，通过 preset 或插件加载 API 安装。
- `META-INF/plugin.yaml`：`id=dsh-emis-plugin`、`entrypoint=net.linlan.dsh.plugin.emis.EmisIdentityPlugin`。
- `META-INF/services/net.linlan.dsh.domain.spi.JavaHarnessPlugin` 已声明入口。

## 错误码

| 码 | 含义 |
| --- | --- |
| `EMIS_TOKEN_MISSING` | 未提供 token |
| `EMIS_TOKEN_INVALID` | 格式/算法/签名/issuer/kid 非法 |
| `EMIS_TOKEN_EXPIRED` | token 已过期 |
| `EMIS_JWKS_UNAVAILABLE` | JWKS 拉取失败或为空 |
| `EMIS_SCOPE_MISSING` | 未提供待检查的 scope |

## 限制（P0）

- 不提供 `login_url`（单点登录跳转）、绑定查询、introspect；这些属于 P1。
- 不做工具级 PRE_TOOL_USE 自动拦截（需要宿主把会话令牌注入 Hook 上下文，待后续设计）。
- HS256 平台 JWT 不能验签；dsh-java 必须统一使用 RS256 OIDC token。
- 不做登录页、用户同步、SCIM、组织树、provider 管理。