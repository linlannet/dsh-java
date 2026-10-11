# dsh-java-plugin-skills

这个技能让 AI 编程助手能够快速、正确地为 dsh-java 创建 Java Native 插件工程。

## 使用前提

- JDK 17+
- Maven 3.8+
- dsh-java 宿主已在本地构建（或 `dsh-java-types` 已安装到本地 Maven 仓库）

## 快速路径

### 1. 安装 types 到本地仓库

```bash
cd /path/to/dsh-java
mvn -q -pl dsh-java-types install -DskipTests
```

### 2. 生成插件工程骨架（Maven Archetype）

```bash
mvn archetype:generate \
  -DarchetypeGroupId=net.linlan \
  -DarchetypeArtifactId=dsh-plugin-archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=com.example \
  -DartifactId=my-plugin \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=com.example.myplugin \
  -DpluginId=my-plugin \
  -DpluginName="My Plugin" \
  -DpluginClass=MyPlugin \
  -DpluginDescription="A Java harness plugin." \
  -DpluginAuthor="Your Name" \
  -DinteractiveMode=false
```

生成后进入目录编译：

```bash
cd my-plugin
mvn package
```

JAR 产出在 `target/my-plugin-1.0.0-SNAPSHOT.jar`。

### 3. 一键安装到运行中的宿主

```bash
bash /path/to/dsh-java/scripts/install-plugin.sh \
  --jar target/my-plugin-1.0.0-SNAPSHOT.jar \
  --id my-plugin \
  --name "My Plugin" \
  --version 1.0.0-SNAPSHOT
```

脚本自动读取 plugin.yaml、调用 HTTP API 安装并运行。宿主默认地址 `http://localhost:8090`，可通过 `--host` 覆盖。

---

## 插件开发核心知识

### 架构概览

```
宿主（dsh-java-app）
  ├── JavaPluginLoader（URLClassLoader 隔离加载 JAR）
  ├── SpringPluginContext（注册工具/Hook/事件/提示词，逆序回收）
  └── ToolRegistry（暴露 plugin__<id>__<tool> 给 Agent）
        ↑
dsh-java-types（唯一契约包，scope=provided）
        ↑
插件 JAR（AbstractHarnessPlugin + AbstractTool 实现）
```

### 项目结构（archetype 生成的标准结构）

```
my-plugin/
├── pom.xml
└── src/main/
    ├── java/com/example/myplugin/
    │   ├── MyPlugin.java
    │   └── ExampleTool.java
    └── resources/META-INF/
        ├── plugin.yaml
        └── services/net.linlan.dsh.domain.spi.JavaHarnessPlugin
```

### 关键类和接口（全部来自 types 包）

#### AbstractHarnessPlugin（插件入口）

```java
public class MyPlugin extends AbstractHarnessPlugin {
    public MyPlugin() { super("my-plugin"); } // 必须与 plugin.yaml id 一致

    @Override public List<ToolDefinition> tools() { return List.of(new MyTool()); }

    // 可选：注册 Hook、事件、提示词、配置
    @Override public void configure(PluginContext context) {
        super.configure(context); // 这行会注册 tools() 返回的工具
        // context.registerSystemPrompt(name, order, text)
        // context.registerHook(hookPoint, hook)
        // context.subscribe(eventName, handler)
        // context.registerDisposer(disposer)
    }
}
```

#### AbstractTool（工具实现）

只需实现 4 个方法：

```java
public class MyTool extends AbstractTool {
    @Override public String name() { return "my_tool"; }
    @Override public String description() { return "工具描述，给模型看的"; }

    @Override public Map<String, Object> parameters() {
        return objectSchema()
                .prop("input", stringSchema("输入参数"))
                .required("input")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String input = str(args, "input");
        return ok("result");
    }
}
```

参数读取方法：`str/str(default)/intVal/intVal(default)/dbl/bool`
结果工厂：`ok(text)/ok(blocks)/fail(message,code)`
Schema：`objectSchema().prop().required().build()` + `stringSchema/integerSchema/numberSchema/booleanSchema/enumSchema`
可选覆写：`isConcurrencySafe(args)` 返回 true 启用并行，`timeoutMs()` 设置超时

### PluginContext 能力速查

| 方法 | 用途 |
|------|------|
| `getConfig(key)` / `getConfig(key, default)` | 读取插件配置（宿主配置文件） |
| `registerSystemPrompt(name, order, text)` | 注入系统提示词段落 |
| `registerHook(hookPoint, hook)` | 在 PRE_TOOL_USE / POST_TOOL_USE 等节点插入回调 |
| `subscribe(eventName, handler)` | 订阅事件 |
| `emit(eventName, payload)` | 发布事件 |
| `registerDisposer(disposer)` | 注册清理资源回调（stop 时逆序调用） |

### HookPoint 枚举

- `PRE_TOOL_USE` — 可返回 `PluginHookResult.block(reason)` 阻止工具执行
- `POST_TOOL_USE` — 观察工具结果
- `USER_PROMPT_SUBMIT` / `SESSION_START` / `STOP` / `NOTIFICATION`

### 工具命名规则

宿主自动加前缀：`plugin__<pluginId>__<toolName>`。Agent 只看到全限定名。

### 配置注入

在 `harness.yml` 或 `application.yml` 中给插件配键值对：

```yaml
harness:
  extensions:
    plugins:
      config:
        my-plugin:
          api-key: xxx
          base-url: https://api.example.com
```

插件内读取：`context.getConfig("api-key")`。

---

## 添加一个新工具（Step by Step）

在 archetype 生成的工程中添加工具：

1. 新建类继承 `AbstractTool`
2. 实现 `name()/description()/parameters()/run()`
3. 在 Plugin 的 `tools()` 方法中加 `new MyNewTool()`
4. `mvn package`
5. 用 `install-plugin.sh` 重新安装

## 编码规范

- 工具名用 snake_case，如 `get_product`
- description 用中文描述清楚用途，供模型决策
- 参数 schema 必须用 `objectSchema().prop()` 构建，不手写 Map
- 网络调用设置超时（连接 5s / 请求 15s）
- 业务规则不要写死在代码里，通过 `context.getConfig()` 注入
- JAR 不要打入 types 依赖（provided scope 保证）
- 每次 `mvn package` 后都要重新 install-plugin.sh

## 常见坑

| 问题 | 原因 | 解决 |
|------|------|------|
| `pluginId 不匹配` | plugin.yaml id ≠ super("...") 传入值 | 检查两处一致 |
| `ClassNotFoundException` | types scope 不是 provided | 改为 `<scope>provided</scope>` |
| `tools()` 返回 null | 忘了 override tools() | 加上并返回 List.of(...) |
| `工具名重复` | 多个工具同名 | 确保同插件内 name() 唯一 |
| `配置读不到` | 宿主没配对应 key | 检查 harness.yml 或 application.yml 中 harness.extensions.plugins.config.<id> |

## 安装方式汇总

| 方式 | 适用场景 |
|------|----------|
| `install-plugin.sh` | 开发调试，最快 |
| HTTP API（curl） | 脚本化部署 |
| harness.yml preset | 宿主启动时自动预装 |
| 控制台 UI | 运维管理 |
