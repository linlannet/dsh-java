# DeepSeek Harness Java v0.2.3 编码任务清单

> 版本：`v0.2.3` | 基线：v0.2.2 | 架构：DDD 六边形架构（Maven 多模块 / Spring Boot 3.5.16 / Java 17）
>
> 本清单将 `docs/spec/0.2.3/spec.md` 的 §5.1–§5.7 能力领域与 `design.md` 的分层落位、接口清单转化为可执行、可验收的编码任务。
>
> 主线为**移动端接入**：移动设备访问开关（默认关闭）+ 设备配对重构回归（DTO 规范化 + 聚合建模）+ 微信小程序登录（jscode2session）+ `devt_` 拦截校验恢复 + AI 可发现性文档。
>
> **范围边界（强制）**：
> 1. **不新增 Maven 模块**，依赖方向保持 `trigger → api ← case → domain ← infrastructure`。
> 2. **不新增第三方依赖**：HTTP 使用 JDK `HttpClient`，JSON 使用既有 Jackson。
> 3. **不新增数据库表**：设备凭据与小程序会话为进程内存储；数据库仓储为后续版本任务。
> 4. **DTO 命名规范**：对外 DTO 统一 `*DTO` 后缀。
>
> **任务统计**：共 **9 个任务组**、**24 个子任务节**、约 **55 条可勾选条目**。
>
> **完成状态图例**：`[x]` 已完成 ｜ `[ ]` 待办/后续优化

---

## 1. 版本治理

> 依赖：无（基座） ｜ 覆盖：spec §5.6 ｜ 完成状态：已完成

### 1.1 统一版本标识为 0.2.3
- [x] 根 pom 与 7 个模块 pom、2 个插件 pom 版本统一升级为 `0.2.3`；验收：10 个 pom 版本一致 —— `pom.xml`、`dsh-java-{app,trigger,api,case,domain,infrastructure,types}/pom.xml`、`plugins/{sample-tools-plugin,dsh-plugin-archetype}/pom.xml`
- [x] `CHANGELOG.md` 新增 0.2.3 分节，区分"新增"与"更新"；验收：分节存在且分类清晰；依赖：无 —— `CHANGELOG.md`

---

## 2. api 契约

> 依赖：无（可与 3 并行） ｜ 覆盖：spec §5.1–§5.5、design §2.2 ｜ 完成状态：已完成

### 2.1 定义 IDeviceApi 与 5 个 DTO
- [x] 定义 `IDeviceApi`（8 方法：`getMobileAccess`/`setMobileAccess`/`createPairingCode`/`pair`/`listDevices`/`revoke`/`rotate`/`validateDeviceToken`，均返回 `Response<T>`）；验收：方法签名与 design §2.2（1）一致 —— `dsh-java-api/src/main/java/net/linlan/dsh/api/IDeviceApi.java`
- [x] 实现 `MobileAccessDTO`（record，1 字段 `enabled`）；验收：JSON 序列化字段名稳定 —— `dsh-java-api/.../dto/MobileAccessDTO.java`
- [x] 实现 `DevicePairingCodeDTO`（record，3 字段：6 位码 / expireAt / expireInSec）；依赖：无
- [x] 实现 `DevicePairRequestDTO`（record，3 字段：`pairingCode`/`deviceName`/`fingerprint`）；验收：替代旧 `DevicePairRequest`（无后缀命名）—— `dsh-java-api/.../dto/DevicePairRequestDTO.java`
- [x] 实现 `DevicePairResponseDTO`（record，4 字段：`deviceId`/`deviceName`/`token`/`issuedAt`）；验收：替代旧 `DevicePairResult`；token 仅在响应出现一次 —— `dsh-java-api/.../dto/DevicePairResponseDTO.java`
- [x] 实现 `DeviceDTO`（record，5 字段：`deviceId`/`deviceName`/`fingerprint`/`createdAt`/`lastSeenAt`，**不含 token 任何形态**）—— `dsh-java-api/.../dto/DeviceDTO.java`

### 2.2 定义 IMiniAppApi 与 2 个 DTO
- [x] 定义 `IMiniAppApi`（2 方法：`login`/`validateSession`）—— `dsh-java-api/src/main/java/net/linlan/dsh/api/IMiniAppApi.java`
- [x] 实现 `MiniAppLoginRequestDTO`（record，1 字段 `code`）与 `MiniAppLoginResponseDTO`（record，2 字段：`token`/`expireAt`）；验收：替代旧 `MiniAppLoginRequest`/`MiniAppSessionDTO` 命名 —— `dsh-java-api/.../dto/`

---

## 3. domain 模型与端口

> 依赖：无（可与 2 并行） ｜ 覆盖：spec §2、§6 ｜ 完成状态：已完成

### 3.1 设备聚合与仓储端口
- [x] 实现 `DeviceCredentialAggregate`（record，6 字段：`deviceId`/`deviceName`/`fingerprint`/`tokenHash`/`createdAt`/`lastSeenAt`，仅存摘要不存明文）；验收：包路径 `domain/device/model/aggregate`，替代旧 `DeviceCredential` —— `dsh-java-domain/src/main/java/net/linlan/dsh/domain/device/model/aggregate/DeviceCredentialAggregate.java`
- [x] 定义 `IDeviceRepository`（`findByDeviceId`/`findAll`/`save`/`delete`）—— `dsh-java-domain/src/main/java/net/linlan/dsh/domain/device/adapter/repository/IDeviceRepository.java`

---

## 4. case 用例

> 依赖：2（api）、3（domain） ｜ 覆盖：spec §5.1–§5.3、§5.5、design §2.1.2/§2.3/§2.4 ｜ 完成状态：已完成

### 4.1 实现 DevicePairingUseCase（设备配对用例）
- [x] 实现 `DevicePairingUseCase implements IDeviceApi`（`@Service`），常量 `PAIRING_TTL_MS=10min`、`MAX_ATTEMPTS_PER_WINDOW=10`、`ATTEMPT_WINDOW_MS=60s`；`mobileAccess: AtomicBoolean`（默认 false）；验收：与 design §2.1.2 状态模型一致 —— `dsh-java-case/src/main/java/net/linlan/dsh/cases/device/DevicePairingUseCase.java`
- [x] 开关联动：`setMobileAccess(false)` 清空 `pairingCodes`；关闭状态下 `createPairingCode`/`pair` 返回 `40300`；验收：spec §5.1 全规则
- [x] 配对码生命周期：`createPairingCode` 生成 6 位码并惰性清理过期条目；`pair` 中 `remove(code)` 一次性消费；无效 `40001`、过期 `40002`；验收：spec §5.2.1.5
- [x] 限流：按指纹摘要的 60s 滑动窗口，超 10 次返回 `42900`；验收：spec §5.2.1.4
- [x] 指纹唯一：配对前按摘要查重，重复返回 `40003`；验收：spec §5.2.1.6
- [x] 凭据签发与轮换：`dev-` deviceId、`devt_` token、SHA-256 摘要存储；`rotate` 保留设备信息仅换 tokenHash 并更新 `lastSeenAt`，设备不存在返回 `40004`；验收：spec §5.2.1.7/§5.3
- [x] token 校验：`validateDeviceToken` 摘要比对，空白返回 false；验收：spec §5.4

### 4.2 实现 MiniAppLoginUseCase（小程序登录用例）
- [x] 实现 `MiniAppLoginUseCase implements IMiniAppApi`（`@Service`），配置读取 `dsh.miniapp.wechat.appid/secret`（缺省空串）；未配置返回 `50300` 不降级；验收：spec §5.5.1.1 —— `dsh-java-case/src/main/java/net/linlan/dsh/cases/miniapp/MiniAppLoginUseCase.java`
- [x] jscode2session 调用：JDK `HttpClient`（连接 5s/请求 8s），URL 参数编码；`errcode` 非零 `40101`、缺 openid `40102`、异常 `50200`（warn 日志）；验收：spec §5.5.3
- [x] 会话签发与校验：`mini_` token、TTL 2h、`ConcurrentHashMap` 存储、校验时惰性清除过期 —— 同上

---

## 5. infrastructure 仓储

> 依赖：3（domain 端口） ｜ 覆盖：design §2.5 ｜ 完成状态：已完成

### 5.1 实现 DeviceRepository（内存仓储）
- [x] 实现 `DeviceRepository implements IDeviceRepository`（`@Repository`，`ConcurrentHashMap` 存储），javadoc 注明"最小闭环实现，后续可替换为数据库仓储"；验收：包路径 `infrastructure/adapter/repository`，替代旧 `InMemoryDeviceRepository` —— `dsh-java-infrastructure/src/main/java/net/linlan/dsh/infrastructure/adapter/repository/DeviceRepository.java`

---

## 6. trigger 触发层

> 依赖：2（api）、4（case 实现契约） ｜ 覆盖：spec §5.1–§5.4、design §2.2（3） ｜ 完成状态：已完成

### 6.1 实现 DeviceController 与 MiniAppController
- [x] 实现 `DeviceController`（`/api/devices`，7 端点：`GET/PUT mobile-access`、`POST pairing-codes`、`POST pair`、`GET /`、`DELETE {deviceId}`、`POST {deviceId}/rotate`），全部委托 `IDeviceApi`；验收：端点与 design §2.2（3）一致 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/http/DeviceController.java`
- [x] 实现 `MiniAppController`（`/api/miniapp`，1 端点：`POST /login`），请求体缺失按 `code=null` 处理走用例校验 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/http/MiniAppController.java`

### 6.2 恢复拦截器设备 token 校验
- [x] `ApiKeyAuthInterceptor` 重新注入 `IDeviceApi`，恢复 `devt_` 前缀分支（位于主 Key 校验之前、无论主 Key 是否配置均强校验），失败 401"Device credential revoked or invalid"；验收：spec §5.4 全规则 —— `dsh-java-trigger/src/main/java/net/linlan/dsh/trigger/filter/ApiKeyAuthInterceptor.java`
- [x] 核对 `WebMvcConfig` 拦截器注册不受构造签名变化影响；验收：全路径拦截 `/**` 生效 —— `dsh-java-trigger/.../filter/WebMvcConfig.java`

---

## 7. app 装配与控制台

> 依赖：4（用例）、6（触发层） ｜ 覆盖：spec §5.5、design §2.4 ｜ 完成状态：已完成

### 7.1 配置项落位
- [x] `application.yml` 新增 `dsh.miniapp.wechat.appid/secret`，占位符 `${DSH_MINIAPP_WECHAT_APPID:}` / `${DSH_MINIAPP_WECHAT_SECRET:}`，注释说明取值来源与"未配置返回 50300、secret 仅服务端"；验收：环境变量注入生效 —— `dsh-java-app/src/main/resources/application.yml`

### 7.2 控制台适配（原生 JS，无前端框架）
- [x] `app.js` 增加移动访问开关与设备管理（列表/吊销/轮换/生成配对码）请求逻辑，复用既有鉴权头与 `Response<T>` 结构 —— `dsh-java-app/src/main/resources/static/app.js`
- [x] `app.css` 面板样式，不引入前端框架 —— `dsh-java-app/src/main/resources/static/app.css`

---

## 8. AI 可发现性文档

> 依赖：无（横切） ｜ 覆盖：spec §5.7 ｜ 完成状态：已完成

### 8.1 仓库级机器可读入口
- [x] 新增 `AGENTS.md`：项目定位、模块布局与依赖方向、构建/测试命令（含单模块与离线模式）、代码约定、禁止事项、关键文档索引 —— `AGENTS.md`
- [x] 新增 `llms.txt`：面向 LLM 的项目摘要（能力清单、技术栈、版本、构建/运行命令、模块地图、文档链接、关联仓库）—— `llms.txt`

### 8.2 系列仓库配置清单
- [x] 新增 `docs/md/ai-discoverability.md`：dsh-java 系列五个仓库（dsh-java / plugins / desktop / mobile / release）的 `llms.txt`/`AGENTS.md` 落地状态、GitHub About 描述建议与统一 topics —— `docs/md/ai-discoverability.md`

---

## 9. 测试与文档归档

> 依赖：全部实现 ｜ 覆盖：design §2.6 ｜ 完成状态：进行中

### 9.1 单元测试
- [x] 编写 `DevicePairingUseCaseTest`：开关默认关闭、关闭拒绝（`40300`）、参数校验、配对码一次性/过期（`40001`/`40002`）、指纹重复（`40003`）、限流（`42900`）、轮换与吊销后 token 失效、`validateDeviceToken` 摘要比对 —— `dsh-java-case/src/test/java/net/linlan/dsh/cases/device/DevicePairingUseCaseTest.java`
- [ ] 补充 `MiniAppLoginUseCaseTest`：mock 微信接口（成功/errcode/缺 openid/网络异常/未配置 50300）—— `dsh-java-case/src/test/java/net/linlan/dsh/cases/miniapp/`（待办）
- [x] 全模块 `mvn test` 回归，ArchUnit 架构规则通过

### 9.2 端到端验证
- [x] 桌面端链路：开开关 → 生成配对码 → 配对成功 → `devt_` 请求通过拦截器 → 吊销后 401 → 轮换后旧 token 401
- [ ] 小程序真机链路：`wx.login` → `/api/miniapp/login` → 会话令牌使用（依赖真机与微信凭据配置）

### 9.3 四件套归档
- [x] 归档四件套（`spec.md`/`design.md`/`tasks.md`/`release-v0.2.3-development-notes.md`）于 `docs/spec/0.2.3/`；验收：四件套齐备
- [x] `CHANGELOG.md` 0.2.3 分节补充完成（新增/更新分类）
- [ ] 0.2.3 开发说明中"验证结论"章节随真机验证补齐

---

## 10. 已知边界与后续版本任务（本版本不做）

- [ ] 设备凭据数据库仓储（`device_credential` 表 + 双库幂等迁移 + SQL 三方口径校验）
- [ ] 小程序会话持久化与 openid ↔ 用户档案绑定
- [ ] 设备 token 细粒度权限（按设备裁剪工具/审批画像）
- [ ] 多实例部署下的配对码/会话共享（外部存储）
