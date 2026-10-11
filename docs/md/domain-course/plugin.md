# 第 6 章 · 插件三段

> 本章覆盖：`plugin`（顶级）、`plugin.registry`、`plugin.bridge`、`plugin.runtime`。
> 解决的核心问题：**插件怎么被发现、识别、启动、注册成工具？**

---

## 学习目标

读完本章你能：

1. 区分 Registry / Bridge / Runtime 三段的边界。
2. 说出 5 种插件接入方式识别（`JAVA_NATIVE` / `DSH_NODE_BRIDGE` / `DSH_PACKAGE` / `CODEX` / `CORDIS`）。
3. 跟踪插件从 `install → activate → run` 的完整生命周期。
4. 知道 `JAVA_NATIVE`（进程内 URLClassLoader）和 `DSH_NODE_BRIDGE`（Node sidecar）的隔离机制差异。

---

## 章节导读

插件域是项目"可扩展"的根基。三段边界对应三个独立阶段：

```
install / uninstall
   ↓
plugin.registry       元数据 + 安装计划
   ↓
plugin.bridge         接入方式识别 + 绑定计划（生成 PluginBridgePlanVO）
   ↓
plugin.runtime        实际启动 + 运行 + 停止插件实例
   ↓
tool.plugin           把插件工具注册进 ToolRegistry
```

三段都用"完整 7 节"模板（每段都是核心模块）。

> 注：`acp`（ACP 协议服务端）、`sdk`（SDK 层）和插件不同——它们是把 Agent 暴露出去给外部调用方，详见第 9 章。

---

## 6.1 `plugin`（顶级）：门面接口

### ① 解决什么问题

把插件域的三个独立服务（Registry / Bridge / Runtime）汇总成 3 个顶层接口，供 case/trigger 调用。

### ② 关键源码位置

- `plugin/IPluginRegistryService.java`（顶级接口）
- `plugin/IPluginBridgeService.java`
- `plugin/IPluginRuntimeService.java`

### ③ 关键签名

```java
public interface IPluginRegistryService {
    PluginInstallationResultVO install(PluginInstallRequest req);
    void uninstall(String pluginId);
    void activate(String pluginId);
    void deactivate(String pluginId);
    List<HarnessPluginEntity> list();
}

public interface IPluginBridgeService {
    PluginActivationResultVO inspect(String pluginId, Path installRoot);
    PluginBridgePlanVO plan(String pluginId);
}

public interface IPluginRuntimeService {
    String start(String pluginId);
    PluginRunResultVO invoke(String pluginId, String toolName, JsonNode args);
    void stop(String pluginId);
    PluginProcessStatusVO status(String pluginId);
}
```

### ④ 设计意图

> **三段解耦** —— Registry 只管"装不装"、"启不启"；Bridge 只管"识别后怎么绑定"；Runtime 只管"已绑定怎么跑起来"。三者不互相依赖（只 Registry ↔ DB / Bridge ↔ 文件系统 / Runtime ↔ 进程）。

---

## 6.2 `plugin.registry`：注册与安装

### ① 解决什么问题

把一个"可能是 jar / 可能是 node 包 / 可能是 DSH_PACKAGE"的工件，"安装"成数据库里的一条 `HarnessPluginEntity` 记录；并提供启停生命周期。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `plugin/registry/service/PluginRegistryService.java` | 注册 / 安装 / 启停实现 |
| `plugin/registry/model/entity/HarnessPluginEntity.java` | 插件实体 |
| `plugin/registry/model/valobj/PluginArtifactInstallPlanVO.java` | 安装计划 |
| `plugin/registry/model/valobj/PluginInstallationResultVO.java` | 安装结果 |
| `plugin/registry/model/valobj/PluginInstallModeEnumVO.java` | 安装模式（local / remote） |
| `plugin/registry/model/valobj/PluginInstallStatusEnumVO.java` | 安装状态 |
| `plugin/registry/model/valobj/PluginRuntimeTypeEnumVO.java` | 运行时类型 |
| `plugin/registry/adapter/port/IPluginArtifactInstallerPort.java` | 工件安装器端口 |
| `plugin/registry/adapter/repository/IHarnessPluginRepository.java` | 仓储端口 |

### ③ 关键签名

```java
public class PluginRegistryService {
    public PluginInstallationResultVO install(PluginInstallRequest req) {
        // 1. IPluginArtifactInstallerPort.install(req) 把工件落到 installRoot
        // 2. IHarnessPluginRepository.save(HarnessPluginEntity)
        // 3. 返回 PluginInstallationResultVO（pluginId, status, runtimeType 候选）
    }

    public void activate(String pluginId) {
        HarnessPluginEntity p = repo.findById(pluginId).orElseThrow();
        p.setStatus(PluginInstallStatusEnumVO.ACTIVATED);
        repo.save(p);
    }
}
```

### ④ 设计意图

> **Registry 不关心"插件怎么跑"，只关心"插件是什么 / 装没装 / 启没启"**。具体怎么跑留给 Bridge + Runtime。

### ⑤ 与谁打交道

- **依赖**：infra `IPluginArtifactInstallerPort`（落工件）+ `IHarnessPluginRepository`（MyBatis）。
- **被依赖**：`IPluginBridgeService.inspect(...)`（看 Registry 是否激活）；`/api/harness/plugins/*`（trigger 层）。

### ⑥ 注意事项

- `pluginId` 全局唯一，由 `IPluginArtifactInstallerPort` 在 `install` 时分配。
- `active=true` 才会进入 Bridge 检查；未激活的插件没 `bridge_plan`。

---

## 6.3 `plugin.bridge`：识别接入方式

### ① 解决什么问题

拿到一个已激活的插件（`installRoot` 目录），**识别它属于哪种接入方式**，并产出 `PluginBridgePlanVO`：到底用哪个 Runtime 来跑（Java URLClassLoader？Node sidecar？还是不可运行保留扩展点？）。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `plugin/bridge/service/PluginBridgeService.java` | 识别 + 生成绑定计划 |
| `plugin/bridge/model/valobj/PluginPackageTypeEnumVO.java` | 包类型枚举（见下） |
| `plugin/bridge/model/valobj/PluginBridgePlanVO.java` | 绑定计划 |
| `plugin/bridge/model/valobj/PluginActivationResultVO.java` | 激活结果 |
| `plugin/bridge/adapter/port/IPluginRuntimeBridgePort.java` | 桥接端口（infra 实现 IO 扫描） |
| `plugin/bridge/adapter/repository/IHarnessPluginRuntimeBindingRepository.java` | 绑定仓储端口 |

### ③ 五种接入方式

```java
public enum PluginPackageTypeEnumVO {
    JAVA_NATIVE,        // 进程内 URLClassLoader
    DSH_NODE_BRIDGE,    // Node sidecar JSON-RPC
    DSH_PACKAGE,        // 识别后 runnable=false（保留扩展点）
    CODEX,              // 同上
    CORDIS              // 同上
}
```

### ④ 识别规则（伪代码）

```java
public PluginBridgePlanVO plan(String pluginId, Path installRoot) {
    if (Files.exists(installRoot.resolve("META-INF/plugin.xml"))) return JAVA_NATIVE_PLAN;
    if (Files.exists(installRoot.resolve("package.json")))        return DSH_NODE_BRIDGE_PLAN;
    if (Files.exists(installRoot.resolve("dsh-package.yaml")))   return DSH_PACKAGE_PLAN;   // runnable=false
    if (Files.exists(installRoot.resolve("CODEX")))              return CODEX_PLAN;        // runnable=false
    return CORDIS_PLAN;                                                                  // runnable=false
}
```

### ⑤ 设计意图

> **"识别"和"启动"解耦** —— Bridge 只回答"是什么 / 应该用什么 Runtime"，Runtime 负责"真的去启动"。Bridge 的产出 `PluginBridgePlanVO` 在 `IHarnessPluginRuntimeBindingRepository` 落库，供 Runtime 读取。

### ⑥ 与谁打交道

- **依赖**：infra `IPluginRuntimeBridgePort`（IO 扫描 installRoot）；
- **被依赖**：`IPluginRuntimeService.start(pluginId)` 读 `PluginBridgePlanVO` 决定怎么启动。

### ⑦ 注意事项

1. **DSH_PACKAGE / CODEX / CORDIS 三种目前 runnable=false**（已知隐患 #6）——是预留扩展点。
2. 同一 installRoot 不能同时匹配多种类型，匹配顺序要保证稳定（按上述 if-else 链）。
3. Bridge 落库的 plan 是 plan-of-record：Runtime 必须按 plan 启动，不能跳过 Bridge 直接 spawn。

---

## 6.4 `plugin.runtime`：启动与运行

### ① 解决什么问题

根据 `PluginBridgePlanVO` 真正把插件启动起来；提供 invoke / stop / status 接口。

### ② 关键源码位置

| 文件 | 角色 |
| --- | --- |
| `plugin/runtime/service/PluginRuntimeService.java` | 启动 / 运行 / 停止实现 |
| `plugin/runtime/model/valobj/PluginRunResultVO.java` | 运行结果 |
| `plugin/runtime/model/valobj/PluginProcessStatusVO.java` | 进程状态 |
| `plugin/runtime/adapter/port/IPluginProcessPort.java` | 进程端口（infra 提供 Java / Node 不同实现） |

### ③ 隔离机制

| 接入方式 | 隔离机制（infra `IPluginProcessPort` 实现） |
| --- | --- |
| `JAVA_NATIVE` | 进程内 `URLClassLoader`，每个插件独立 classloader |
| `DSH_NODE_BRIDGE` | Node sidecar（`node <entrypoint>`），JSON-RPC over stdio |
| `DSH_PACKAGE` / `CODEX` / `CORDIS` | 不可运行，process 永远 start 失败（已知隐患 #6） |

### ④ 关键签名

```java
public class PluginRuntimeService {
    public String start(String pluginId) {
        var plan  = bindingRepo.findByPluginId(pluginId);
        var port  = processPortFactory.pick(plan.runtimeType());   // Java or Node
        return port.spawn(plan.entrypoint(), plan.args());         // 返回 instanceId
    }

    public PluginRunResultVO invoke(String pluginId, String toolName, JsonNode args) {
        // 1. 查 bindingRepo 找 instanceId
        // 2. 转 JSON-RPC / 直接 invoke
        // 3. 包装返回 PluginRunResultVO
    }

    public void stop(String pluginId) {
        // destroy instance
    }
}
```

### ⑤ 设计意图

> **每个 IPluginProcessPort 实现 = 一种隔离模型**。要加新接入方式，加一个新的 `IPluginProcessPort` 实现即可，业务侧不需改动。

### ⑥ 与谁打交道

- **依赖**：infra `IPluginProcessPort`（Java / Node 两种实现）；
- **被依赖**：`tool.plugin.PluginToolBridgeService`（把插件工具注册进 `ToolRegistry` 后，运行时由 `ToolCallExecutor` 派发到 `IPluginProcessPort.invoke()`）。

### ⑦ 注意事项

1. **入口文件约束**：Node 插件 entrypoint 必须是 `installRoot` 内 `.js` / `.mjs` / `.cjs` 之一；超范围直接拒。
2. **进程管理**：Java 进程内进程泄漏风险低；Node sidecar 是真进程，stop 必须保证回收，避免僵尸。
3. **超时控制**：`IPluginProcessPort.invoke()` 默认接受 `ToolCallExecutor` 传入的 `orTimeout`。
4. **DSH_PACKAGE / CODEX / CORDIS 三种 runnable=false** 是真的失败，不会假装成功。

---

## 小结

1. **三段解耦**：Registry（装/启停）/ Bridge（识别）/ Runtime（启动/调用）。
2. **5 种接入方式**：其中 3 种（`DSH_PACKAGE` / `CODEX` / `CORDIS`）目前 runnable=false。
3. **真正能跑的两种**：`JAVA_NATIVE`（进程内 URLClassLoader）、`DSH_NODE_BRIDGE`（Node sidecar JSON-RPC）。
4. **桥接到工具**：`tool.plugin.PluginToolBridgeService` 把 `PluginToolDefinition` 注册进 `ToolRegistry`，命名空间 `plugin__<pluginId>__<tool>`。
5. **MCP 和 Bridge 互不打通**（已知隐患）——未来期望统一在 Bridge 层。

---

## 思考题

1. **【Easy】** 假如要让 `PluginRegistryService` 在 install 时校验 `pluginId` 唯一且符合命名规则（`^[a-z][a-z0-9_-]{2,40}$`），你会改哪个文件？要改 schema 吗？
2. **【Medium】** 想把 `DSH_PACKAGE`（一个 yaml 包描述）真正跑起来：写一个 `DshPackageProcessPort` 应该放在哪？看 `IPluginProcessPort` 接口设计。
3. **【Hard】** 当前 JAVA_NATIVE 用 `URLClassLoader` 隔离，Node sidecar 用 JSON-RPC 隔离；二者**对外 invoke 接口不一致**（Java 直接调 method，Node 走 JSON-RPC）。如果要统一这两套调用模型，怎么改 `PluginRuntimeService.invoke`？参考 `tool.plugin.PluginToolBridgeService` 现状。
