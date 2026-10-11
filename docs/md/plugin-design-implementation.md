# DSH Java 插件设计理论：从 ClassLoader 隔离到 Agent 工具调用

本文以 `dsh-java-plugin-integration.drawio` 为主线，说明 DSH Java 插件为什么这样设计，以及一个插件如何从 JAR 变成 Agent 可以调用的业务能力。文章重点回答以下问题：

1. 一个外部应用如何接入 `dsh-java`？
2. Harness 如何发现、加载和管理插件？
3. 用户的一次对话如何最终执行到插件？
4. 插件如何访问外部业务数据，并把结果返回给 Agent？

配套架构图：

```text
docs/md/draw.io/dsh-java-plugin-integration.drawio
```

配图文件：[dsh-java-plugin-integration.drawio](draw.io/dsh-java-plugin-integration.drawio)

打开后包含三页。下文在对应位置使用截图即可，不需要把 XML 或实现代码作为正文的一部分：

1. `01-整体架构`：系统边界和依赖关系。
2. `02-对话时序`：一次对话的完整调用链。
3. `03-插件开发与安装`：插件从开发到运行的生命周期。

本文中的“宿主”指运行 `dsh-java` 的 JVM，“插件”指由业务方编译出的 Java 插件 JAR，“业务系统”指真正保存业务资源的应用服务、数据库或第三方系统。

---

## 1. 插件系统要解决什么问题

Agent 本身只会理解自然语言、调用模型和执行工具，但业务系统掌握的是业务资源、数据源和内部流程。

如果把所有业务代码直接写进 Harness，会带来几个问题：

- Harness 和业务系统强耦合。
- 每接入一个业务，都需要修改并重新发布 Harness。
- 不同业务的依赖可能互相冲突。
- 业务系统的权限、数据格式和生命周期难以独立管理。
- Agent 核心、业务代码和基础设施代码混在一起。

插件系统的目标，是在 Agent 和业务系统之间建立一个稳定的扩展边界：

```text
Agent Runtime
    ↓ 稳定的 Plugin SPI
Plugin Runtime
    ↓ 业务适配协议
External Business System
```

插件不是另一个 Agent，也不是数据库本身。插件是一个把 Agent 工具调用转换为业务调用的适配器。

## 2. 总体架构模型

### 配图一：整体架构

> **截图位置：** 此处放置 `dsh-java-plugin-integration.drawio` 的 `01-整体架构` 页面。

打开 draw.io 文件的 `01-整体架构` 页面，可以看到完整的系统分层。

对应页面：`01-整体架构`

```text
┌─────────────────────────────────────────────────────────────┐
│ 用户 / 外部应用                                               │
│ 聊天页面、客服系统、业务后台                                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST / SSE
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ dsh-java                                        │
│ HTTP API → Agent → ReAct Loop → Tool Registry → Plugin Runtime│
└──────────────────────────┬──────────────────────────────────┘
                           │ Plugin Tool
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ 外部业务插件                                                   │
│ Plugin Entry → Tool → Business Client                         │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP / SDK / JDBC / RPC
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ 业务系统                                                       │
│ 业务 API、数据库、内部服务、第三方服务                         │
└─────────────────────────────────────────────────────────────┘
```

这张图表达的不是简单的“应用调用一个工具”，而是四个运行边界：

```text
用户 / 你的应用
        ↓ REST / SSE
Harness Agent Runtime
        ↓ Plugin SPI / Tool Registry
Java Plugin Runtime
        ↓ HTTP / SDK / JDBC / RPC
业务 API、数据库和内部服务
```

其中最重要的设计原则是：**Harness 负责智能编排，插件负责业务适配，业务系统负责真实数据和最终权限。** 这使得三类变化可以分别进行：模型和 Agent 可以升级，插件可以独立发布，业务系统也可以继续演进自己的数据模型。

### 2.1 外部应用层

外部应用负责：

- 接收用户输入。
- 保存自己的业务上下文。
- 调用 Harness 的对话 API。
- 接收流式事件。
- 展示最终答案。

外部应用不需要自己实现 ReAct 循环，也不需要自己判断调用哪个插件工具。

### 2.2 Harness 层

`dsh-java` 负责：

- 接收对话请求。
- 管理 Agent 和 Session。
- 调用模型。
- 暴露工具目录。
- 选择并执行 Tool。
- 运行 Hook 和审批门禁。
- 记录 ToolCall、ToolResult 和会话事件。
- 将工具结果回填给模型。
- 通过 SSE 返回过程和最终结果。

### 2.3 插件层

插件负责：

- 把业务能力描述为 Tool。
- 校验工具参数。
- 调用外部业务系统。
- 处理业务 API 的错误。
- 对返回数据进行必要的转换和脱敏。
- 注册插件提示词、Hook 和资源释放逻辑。

### 2.4 业务系统层

业务系统负责：

- 真实数据。
- 用户权限。
- 业务规则。
- 数据库访问。
- 业务审计。
- 业务 API 的稳定性。

业务系统不能把权限控制完全交给模型或插件描述。最终权限校验必须发生在业务系统边界内。

### 2.5 一次调用的责任分工

以“一次业务资源查询”为抽象过程，各层只做自己应该做的事：

| 层次 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| 外部应用 | 接收用户输入、传递会话和身份、展示 SSE | 不实现 ReAct 循环，不直接选择插件实现类 |
| Harness | 调用模型、提供工具目录、执行 Tool、回填上下文 | 不把业务数据库表直接变成模型工具 |
| 插件 | 把结构化参数转换为业务请求，整理业务结果 | 不替代业务系统的最终鉴权 |
| 业务系统 | 查询真实数据、执行业务规则和权限校验 | 不依赖模型提示词来保证安全 |

所以，插件不是数据库本身，而是一个“会说 Agent 语言的业务适配器”：输入是模型可以理解的 Tool 参数，输出是模型可以继续推理的结构化 Tool 结果。

## 3. 依赖方向与边界设计

插件系统的依赖方向应该是：

```text
插件实现
    ↓ 依赖
dsh-java-types
    ↑ 由宿主实现和提供
dsh-java
```

插件只依赖稳定的契约包，不应该依赖 Harness 的内部实现模块。

### 3.1 稳定契约

插件和宿主之间的稳定契约包括：

- `JavaHarnessPlugin`：插件入口接口。
- `AbstractHarnessPlugin`：插件入口基类。
- `ToolDefinition`：工具定义接口。
- `AbstractTool`：工具实现基类。
- `ToolExecutionResult`：工具结果。
- `PluginContext`：插件运行时上下文。
- `plugin.yaml`：插件元数据格式。
- ServiceLoader 文件：入口发现约定。

插件工程建议只依赖 `dsh-java-types`，并使用 `provided` scope，让宿主提供这份契约包：

```xml
<dependency>
    <groupId>net.linlan.dsh</groupId>
    <artifactId>dsh-java-types</artifactId>
    <version>${dsh.version}</version>
    <scope>provided</scope>
</dependency>
```

这样做的目的不是减少一个 JAR 文件，而是保证宿主和插件使用同一份 SPI 类型。插件可以把自己的业务依赖打入插件包，但不应把 `JavaHarnessPlugin`、`ToolDefinition` 等共享契约复制成另一份。

### 3.2 宿主内部实现

以下内容属于 Harness 内部实现，不应该被插件直接依赖：

- Agent 内部节点。
- 数据库 DAO 和 Repository。
- Spring Bean。
- Harness 内部领域实体。
- Tool Registry 的具体实现。
- 插件 ClassLoader 管理器。
- SSE Controller 的内部实现。

这样可以使宿主替换内部实现，而不破坏已有插件。

### 3.3 业务系统边界

插件可以调用业务 API，但不应该把业务系统内部的所有模型直接暴露给 Agent。推荐通过插件定义一个面向 Agent 的窄接口：

```text
业务内部能力很多
        ↓ 筛选、鉴权、裁剪
插件 Tool 能力较少且清晰
        ↓
模型可理解、可调用的工具目录
```

Tool 是业务能力的产品化接口，而不是数据库表的直接映射。

## 4. 插件的核心抽象

### 4.1 插件入口

插件入口负责提供工具和生命周期扩展：

```java
public class MyPlugin extends AbstractHarnessPlugin {

    public MyPlugin() {
        super("my-plugin");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new QueryTool());
    }
}
```

入口类是插件的根对象。宿主通过它获得插件提供的 Tool，并在启动、配置和停止阶段调用对应生命周期。

一个插件至少包含三类内容：

```text
Plugin Entry       生命周期根对象，例如 MyPlugin
Tool               面向模型的最小业务动作，例如 query_order
Business Client    面向业务系统的调用客户端，例如 HTTP Client / SDK / JDBC
```

入口类不应该把所有业务逻辑都写在自己内部。更适合的方式是让入口类负责装配 Tool，让 Tool 负责参数和结果转换，让 Client 负责与业务系统通信。

### 4.2 Tool

Tool 是模型可以选择的最小业务动作，至少包含：

```text
name          工具名
description   给模型看的用途说明
parameters    输入参数 JSON Schema
execute       执行入口
```

一个好的 Tool 应该只承担一个清晰动作：

- 查询一个资源。
- 搜索一组资源。
- 获取一个状态。
- 执行一个经过授权的操作。

不建议设计一个同时完成查询、修改、审批和删除的“万能工具”。

### 4.3 PluginContext

`PluginContext` 是插件访问宿主扩展能力的唯一入口，主要提供：

- 注册 Tool。
- 读取插件配置。
- 注册系统提示词。
- 注册 PRE / POST Hook。
- 订阅和发布插件事件。
- 注册资源释放器。

宿主持有这些注册项，并在插件停止时统一回收。

这使 `PluginContext` 成为一个**贡献项事务边界**。插件在 `configure` 中注册了工具、提示词、Hook 或事件订阅，只要启动失败，宿主就可以关闭上下文并逆序撤销已经成功的注册；插件停止时也可以用同一个上下文完成清理，而不必让插件直接操作宿主内部的 Registry。

## 5. ClassLoader 的作用

Java Native Plugin 运行在宿主 JVM 进程内。为了避免不同插件之间、插件与宿主之间的依赖相互污染，Harness 为每个插件创建独立的 `URLClassLoader`。

### 5.1 为什么需要 ClassLoader

如果所有插件都使用宿主的同一个 ClassLoader，会出现：

- 插件依赖的第三方库版本互相覆盖。
- 一个插件升级依赖，影响其他插件。
- 插件静态状态容易相互污染。
- 插件卸载后类仍被引用，无法回收。
- 宿主内部类可能被插件直接访问，边界变得模糊。

独立 ClassLoader 的目标是：

```text
宿主 ClassLoader
    ├── 插件 A ClassLoader
    ├── 插件 B ClassLoader
    └── 插件 C ClassLoader
```

每个插件拥有自己的类加载命名空间。

### 5.2 当前加载方式

Java 插件加载器的核心逻辑可以抽象为：

```java
URLClassLoader classLoader = new URLClassLoader(
        new URL[]{jarPath.toUri().toURL()},
        Thread.currentThread().getContextClassLoader()
);
```

这个 ClassLoader 有两个重要特点：

1. 插件 JAR 是自己的 URL 来源。
2. 宿主的 ClassLoader 是父加载器。

因此插件可以使用宿主提供的稳定契约类型，同时可以加载自己 JAR 内的业务实现类。

当前实现的加载步骤可以拆成下面几步：

```text
校验 JAR 路径
    ↓
创建 URLClassLoader(URL = 插件 JAR, parent = 宿主上下文 ClassLoader)
    ↓
用插件 ClassLoader 读取 META-INF/plugin.yaml
    ↓
按 entrypoint 反射实例化入口类
    ↓  entrypoint 未配置时
用 ServiceLoader<JavaHarnessPlugin> 发现入口实现
    ↓
调用 plugin.onStart()
    ↓
交给 JavaPluginRuntimeManager 执行 configure(PluginContext)
    ↓
把 Tool、Prompt、Hook、Event 注册到宿主
```

这里有一个值得强调的边界：`JavaPluginLoader` 负责“把代码加载成插件实例”，`JavaPluginRuntimeManager` 负责“把插件实例接入宿主运行时”。前者不应该直接知道 Tool Registry、Prompt Assembler 或业务数据库，后者也不应该自己解析 JAR 内部的类。

`plugin.yaml` 与 `ServiceLoader` 分别承担不同职责：

| 机制 | 作用 | 当前使用方式 |
| --- | --- | --- |
| `META-INF/plugin.yaml` | 描述插件身份、版本、入口类和能力摘要 | 必须包含 `id`、`name`、`version`；`entrypoint` 可直接指定入口 |
| `META-INF/services/...JavaHarnessPlugin` | Java 标准 SPI 发现入口 | 未指定 `entrypoint` 时作为回退发现方式 |

入口类通常需要公共无参构造方法，因为宿主会通过 `getDeclaredConstructor().newInstance()` 创建它。设计约束上，JAR 中的入口实现应保证 `plugin.yaml` 的 `id` 与 `pluginId()` 一致，否则插件身份、工具命名空间和配置作用域会发生漂移；工程实现中也应在加载后显式校验这一点。当前代码在 ServiceLoader 回退分支会按 `pluginId()` 过滤候选入口，显式 `entrypoint` 分支则应由清单和插件工程共同保证一致性。

### 5.3 父加载器与共享契约

Java ClassLoader 通常采用父加载优先模型：

```text
插件请求加载 JavaHarnessPlugin
        ↓
优先由父 ClassLoader 加载
        ↓
宿主和插件使用同一个契约接口
```

这是非常关键的设计。如果宿主和插件分别加载了两份 `JavaHarnessPlugin`，即使类名完全相同，JVM 也会认为它们是两个不同的类型：

```text
同一个全限定名 + 不同 ClassLoader = 不同的 Java 类型
```

最终会出现 `ClassCastException` 或 ServiceLoader 无法发现实现的问题。

因此，插件 SPI、基础结果类型和宿主交互接口必须由父加载器统一提供。

可以把一次类型解析理解为下面的过程：

```text
pluginClassLoader.loadClass("net.linlan...JavaHarnessPlugin")
             ↓ 委托给 parent
宿主 ClassLoader 返回 JavaHarnessPlugin.class

pluginClassLoader.loadClass("com.example.MyPlugin")
             ↓ parent 找不到
plugin ClassLoader 从 JAR 定义 com.example.MyPlugin
```

这也是插件依赖设计的核心：**契约类型向上共享，业务实现向下私有。** 宿主和插件必须共享 `JavaHarnessPlugin`、`ToolDefinition`、`ToolExecutionResult` 等边界类型；插件自己的 HTTP 客户端、JSON 映射器或业务 SDK 则尽量留在插件 ClassLoader 的范围内。

ServiceLoader 同样必须使用正确的加载器。当前实现显式传入：

```java
ServiceLoader<JavaHarnessPlugin> services =
        ServiceLoader.load(JavaHarnessPlugin.class, pluginClassLoader);
```

第一个参数是由宿主加载的 SPI 类型，第二个参数告诉 Java 去哪个插件 JAR 中读取 `META-INF/services` 和实现类。只调用 `ServiceLoader.load(JavaHarnessPlugin.class)`，可能只搜索宿主默认的加载路径，导致插件已经存在但发现不到。

### 5.4 类身份、依赖隔离与实际边界

JVM 判断一个类型时，不只看全限定类名，还看定义它的 ClassLoader：

```text
类型身份 = (全限定类名, 定义该类的 ClassLoader)
```

因此，即使下面两个类的名字完全一样，它们也不是同一个 Java 类型：

```text
com.example.sdk.Client  ← 由插件 A ClassLoader 定义
com.example.sdk.Client  ← 由插件 B ClassLoader 定义
```

这种隔离可以避免插件 A 的静态字段与插件 B 共享，也允许两个插件分别拥有自己的私有实现类。但是，当前使用的是标准父优先 `URLClassLoader`，所以不能把它理解为“插件 JAR 中的所有依赖都天然隔离”：

- 父加载器已经提供的类通常由父加载器返回。
- 插件 JAR 中只存在、父加载器找不到的类，才由插件 ClassLoader 定义。
- 宿主已经暴露的共享依赖可能覆盖插件想携带的同名版本。
- 跨越宿主边界的对象如果携带插件私有类型，容易出现类型转换或版本兼容问题。

因此实际工程应采用“窄契约 + 私有依赖”的策略：跨边界只传递字符串、数字、布尔值、集合、JSON 结构和稳定 SPI 类型，不把插件私有 SDK 的对象直接暴露给宿主。

> 如果未来需要更强的依赖隔离，可以实现自定义 `ClassLoader` 的子优先策略，或把插件依赖放入专用依赖目录后由独立加载器管理。但子优先也会增加 SPI 类型重复加载、链接错误和类转换异常的风险，不能脱离共享契约设计单独使用。

### 5.5 ClassLoader 隔离的边界

ClassLoader 隔离不是进程隔离，也不是安全沙箱：

- 插件仍然运行在宿主 JVM 内。
- 插件仍然可能消耗宿主 CPU 和内存。
- 插件仍然可以访问 JVM 允许访问的文件和网络。
- 插件异常可能影响宿主线程或资源。
- 恶意插件不能仅依靠 ClassLoader 得到安全阻断。

如果需要强安全隔离，应使用独立进程、容器或远程沙箱。Java Native 更适合受信任代码、低延迟调用和 Java 生态复用。

这里的 `ClassLoader` 只适用于图中 Java Native 插件路径。若插件是 Node Bridge，隔离方式是启动 Node sidecar，并通过 JSON-RPC 或标准输入输出通信；两者不能混用同一套加载和停止逻辑。前者是进程内调用，延迟更低但与宿主共享 JVM；后者拥有独立进程边界，但需要处理进程启动、通信、序列化和退出。

### 5.6 ClassLoader 与依赖冲突

插件自己的业务依赖可以放在插件 JAR 中，但需要注意：

- 宿主已经提供的 SPI 契约不要重复打包。
- 不要把 Spring Boot 宿主整体打入插件。
- 避免把共享类型复制成插件私有版本。
- 业务依赖尽量保持内部化，减少暴露到宿主边界的类型。
- Tool 参数和结果使用稳定的基础类型或契约类型。

### 5.7 ClassLoader 与线程上下文

ClassLoader 还会影响资源查找、SPI 发现和部分第三方框架的自动配置。插件代码如果在异步线程中调用 `Thread.currentThread().getContextClassLoader()`，得到的未必是插件 ClassLoader。对于依赖上下文 ClassLoader 的库，宿主应在调用边界临时设置并在 `finally` 中恢复：

```java
ClassLoader previous = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginClassLoader);
    pluginCallback.run();
} finally {
    Thread.currentThread().setContextClassLoader(previous);
}
```

当前加载器对读取 `plugin.yaml` 使用了插件 ClassLoader，对 `ServiceLoader` 也显式传入了插件 ClassLoader，因此核心发现流程不依赖默认 TCCL。若插件自己创建线程、线程池或定时任务，则应在线程退出前停止任务，并避免让这些线程永久持有插件 ClassLoader。

### 5.8 ClassLoader 与卸载

插件停止时，宿主必须完成：

```text
注销 Tool
移除 Prompt
注销 Hook
取消事件订阅
关闭线程池和连接池
停止定时器和文件监听器
执行 plugin.onStop()
关闭 URLClassLoader
```

仅仅调用 `URLClassLoader.close()` 并不能保证类可以被垃圾回收。如果还有以下引用，ClassLoader 仍可能泄漏：

- 宿主静态集合引用插件对象。
- 插件创建的线程仍在运行。
- `ThreadLocal` 保存插件类对象。
- 定时任务仍然持有插件 Runnable。
- 日志、JDBC 驱动或缓存注册表保存插件类。
- 异步回调持有插件实例。

因此，插件生命周期的本质不仅是“加载和卸载 JAR”，也是“建立和清理所有跨边界引用”。

可以用一个判断标准理解卸载：当运行表、Tool Registry、Hook Registry、事件总线、提示词装配器、线程和异步回调都不再引用插件实例或插件 ClassLoader 时，JVM 才有机会回收该加载器定义的类。`URLClassLoader.close()` 主要负责关闭 JAR 文件等底层资源，它不是“立即删除类”的命令，也不会替宿主清理对象引用。

## 6. 插件加载与生命周期

插件生命周期可以抽象为：

```text
INSTALL
   ↓
ACTIVATE
   ↓
STARTING
   ↓
ACTIVE
   ↓
STOPPING
   ↓
DISPOSED
```

### 配图三：插件开发与安装

> **截图位置：** 此处放置 `dsh-java-plugin-integration.drawio` 的 `03-插件开发与安装` 页面。

这张图可以按“开发阶段、安装阶段、运行阶段”三段来读：

```text
开发者工程
    ↓ mvn clean package
插件 JAR（plugin.yaml + ServiceLoader + 业务代码）
    ↓ POST /api/harness/plugins/install
Harness 登记并保存插件工件
    ↓ activate / run
ClassLoader 加载、注册 Tool、Prompt、Hook、Event
    ↓
ACTIVE：Agent 可以在对话中选择插件工具
```

### 6.1 Install

安装阶段负责：

1. 接收插件来源。
2. 校验插件路径和插件 ID。
3. 读取 `META-INF/plugin.yaml`。
4. 保存插件登记信息。
5. 将插件放入宿主的安装目录。

安装不是执行。安装完成只代表宿主知道这个插件，并且能够定位到它的运行产物。

一个最小的 Java 插件 JAR 结构如下：

```text
my-plugin.jar
├── META-INF/
│   ├── plugin.yaml
│   └── services/
│       └── net.linlan.dsh.domain.spi.JavaHarnessPlugin
└── com/example/
    ├── MyPlugin.class
    └── QueryResourceTool.class
```

示例清单：

```yaml
id: my-plugin
name: My Plugin
version: 1.0.0
author: Example Team
description: 查询业务资源并返回面向用户的摘要。
entrypoint: com.example.MyPlugin
```

安装阶段读取元数据、校验工件和登记插件；它不应该因为“文件已经上传”就认为插件已经可以被 Agent 调用。真正可调用的前提是后续激活和运行成功。

### 6.2 Activate

激活阶段负责建立运行绑定：

- 确认运行类型。
- 确认 Java Native 或 Node Bridge。
- 检查入口信息。
- 生成运行绑定记录。
- 准备插件启动所需的路径和配置。

### 6.3 Start / Run

Java Native 插件启动过程：

```text
读取已安装 JAR
  ↓
创建插件 ClassLoader
  ↓
读取 plugin.yaml
  ↓
根据 entrypoint 或 ServiceLoader 找到入口类
  ↓
实例化 JavaHarnessPlugin
  ↓
执行 onStart()
  ↓
JavaPluginRuntimeManager 执行 configure(PluginContext)
  ↓
注册 tools() 或 configure 中贡献的 Tool
  ↓
注册 Tool / Prompt / Hook / Event
  ↓
状态变为 ACTIVE
```

启动过程中任何一步失败，都应该回滚已经完成的注册项，并关闭已经创建的 ClassLoader。

当前实现把启动分成两个层次：`JavaPluginLoader.load()` 完成清单读取、入口发现、实例化和 `onStart()`；`JavaPluginRuntimeManager.start()` 创建 `SpringPluginContext`，调用 `configure(context)`，并把上下文中的贡献项接入宿主。这样既能复用同一个加载器，也能让注册项具备统一的回滚能力。

### 6.4 Stop / Unload

停止顺序应该与注册顺序相反：

```text
停止接收新调用
  ↓
注销工具
  ↓
移除提示词
  ↓
注销 Hook 和事件
  ↓
释放插件资源
  ↓
调用 onStop()
  ↓
关闭 ClassLoader
```

逆序释放可以减少资源之间的依赖问题。

停止时还要先阻止新的调用进入旧实例。否则可能发生这样的竞态：旧 Tool 已经从 Registry 移除，但某个 Agent 回合仍然持有旧 Tool 引用并继续访问已经关闭的业务客户端。生产实现应结合运行状态、调用计数或取消信号，确保“停止接收新调用”和“释放旧资源”之间有明确的排空策略。

### 6.5 Hot Reload

热重载的本质不是重新读取同一个 ClassLoader，而是：

```text
停止旧实例
  ↓
清理旧注册项
  ↓
关闭旧 ClassLoader
  ↓
创建新 ClassLoader
  ↓
加载新 JAR
  ↓
重新注册同名工具
```

新旧插件不能同时向 Tool Registry 暴露同名工具，否则会出现工具指向不确定的问题。

## 7. 工具如何进入 Agent

插件工具不会直接被模型看到。中间需要经过工具注册和 Agent 工具目录。

```text
插件入口返回 Tool
        ↓
Plugin Runtime 注册到 ToolRegistry
        ↓
Agent 运行时构建当前工具目录
        ↓
System Prompt / Function Schema
        ↓
发送给模型
```

工具名称使用命名空间：

```text
plugin__<pluginId>__<toolName>
```

命名空间解决两个问题：

1. 不同插件可以使用相同的局部工具名。
2. 模型和执行器可以根据工具名定位插件来源。

工具注册时还应该同步注册：

- 工具描述。
- 参数 Schema。
- 输出 Schema。
- 并发安全属性。
- 所属插件 ID。

## 8. 对话执行时序

### 配图二：对话时序

> **截图位置：** 此处放置 `dsh-java-plugin-integration.drawio` 的 `02-对话时序` 页面。

打开 draw.io 文件的 `02-对话时序` 页面，沿着编号从 1 到 13 阅读。

对应页面：`02-对话时序`

### 8.1 应用发起请求

外部应用调用：

```text
POST /api/agent/stream
```

请求中通常包含：

- `agentId`。
- 用户消息。
- 会话或工作区信息。
- 模型和运行参数。
- 当前用户身份或业务上下文。

Harness 返回 SSE 流，应用不需要等待整个 Agent 回合结束后才获得内容。

### 8.2 Agent 准备工具目录

Agent 创建运行上下文后，会组合：

- 内置工具。
- 当前可用插件工具。
- 当前会话的系统提示词。
- 插件贡献的提示词段落。
- 当前用户和工作区上下文。

工具目录是模型可以选择的行动空间。

### 8.3 模型提出 Tool Call

模型并不直接执行 Tool，而是返回结构化调用：

```json
{
  "name": "plugin__my-plugin__query_order",
  "arguments": {
    "orderId": "order-1001"
  }
}
```

模型负责选择工具和组织参数，宿主负责验证、授权和执行。

### 8.4 ToolCallExecutor 执行

工具统一经过 `ToolCallExecutor`：

```text
解析工具名
  ↓
查找 ToolRegistry
  ↓
解析和校验参数
  ↓
执行 PRE_TOOL_USE Hook
  ↓
运行期审批 / 权限门禁
  ↓
检查取消、超时和并发策略
  ↓
调用 Tool.execute()
  ↓
执行 POST_TOOL_USE Hook
  ↓
记录 ToolCall / ToolResult 事件
```

插件工具和内置工具共享这一条执行链，因此插件不能绕过宿主的工具治理能力。

### 8.5 结果回填

Tool 返回结果后，Harness 会：

1. 记录工具结果。
2. 将结果追加到当前上下文。
3. 再次请求模型。
4. 让模型基于真实结果继续推理。

这就是 ReAct 循环中的 Observe：

```text
Reason → Act → Observe → Reason → Act → Observe
```

### 8.6 最终回答

当模型不再请求工具时，Agent 回合结束，Harness 通过 SSE 返回最终文本和终态事件。

### 8.7 按图还原完整调用链

把图中的 13 个编号串起来，就是一次“查询订单状态”对话：

| 编号 | 调用方 → 被调用方 | 发生的事情 |
| --- | --- | --- |
| 1 | 用户 → 外部应用 | 用户在商城、客服页面或管理后台输入“查询订单状态”。 |
| 2 | 外部应用后端 → Harness | 后端向 `POST /api/agent/stream` 发送用户消息、会话和业务上下文。 |
| 3 | Harness → Agent / Tool Registry | Harness 创建 Agent 回合，准备当前可用的内置工具和插件工具。 |
| 4 | Agent → LLM | Harness 将用户问题、系统提示词和工具定义发送给模型，模型判断需要查询业务数据。 |
| 5 | LLM → Harness | 模型返回结构化 Tool Call，例如 `plugin__my-plugin__query_order` 和 `orderId` 参数。 |
| 6 | Harness → 插件 Tool | Harness 根据完整工具名从 Registry 找到工具，经过统一执行链后调用插件实现。 |
| 7 | 插件 → 业务 API | 插件把 Tool 参数转换成业务请求，例如 `GET /api/orders/{orderId}`。 |
| 8 | 业务 API → 数据源 | 业务服务执行权限校验和业务规则，再查询数据库、内部服务或第三方系统。 |
| 9 | 数据源 → 业务 API → 插件 | 真实订单数据沿原路径返回，插件只保留完成回答所需的字段。 |
| 10 | 插件 Tool → Harness | 插件将业务结果转换为 `ToolExecutionResult`，失败时也返回结构化错误。 |
| 11 | Harness → Agent / LLM | Harness 将 Tool Result 写入当前会话上下文，再次请求模型进行 Observe 和下一轮 Reason。 |
| 12 | LLM → Harness | 模型基于真实业务结果生成面向用户的最终回答；如果仍需要工具，则回到第 5 步。 |
| 13 | Harness → 外部应用 → 页面 | Harness 通过 SSE 推送流式文本和终态事件，外部应用将答案展示给用户。 |

这条链路体现了一个关键事实：**模型只提出意图，插件执行动作，业务系统确认事实。** 模型不应该直接连接数据库，也不应该直接持有业务系统的内部对象。

如果一次回答需要多个工具，步骤 5～12 可以重复多轮；如果模型返回多个彼此独立的 Tool Call，Harness 还可以根据工具的并发安全属性进行并行调度。无论串行还是并行，插件工具都必须经过同一套参数校验、Hook、审批、取消、超时和事件记录机制。

## 9. 插件如何访问业务数据

### 9.1 访问方式一：业务 API

推荐链路：

```text
Plugin Tool
    ↓
Business Client
    ↓ HTTP / RPC
Business API
    ↓
Database / Internal Service
```

优点：

- 权限和业务规则集中在业务服务。
- 插件不需要保存数据库密码。
- 业务系统可以继续演进内部数据结构。
- 便于审计、限流和多租户控制。

### 9.2 访问方式二：直接访问数据库

适合确实需要数据库能力的插件，但必须自行设计：

- 只读数据库账号。
- 连接池和连接超时。
- 查询超时。
- 返回行数和字段限制。
- SQL 白名单或解析器。
- 脱敏规则。
- 审计日志。

插件直接连接数据库，不代表可以绕过业务权限。生产系统仍然应明确数据访问边界。

### 9.3 业务上下文如何传递

用户身份、租户 ID、业务上下文等信息可以通过以下方式传递：

- Tool 参数显式传递。
- `PluginContext` 配置传递。
- 当前会话上下文传递。
- 业务 API 的认证 Token 传递。
- 由插件从可信宿主上下文读取。

不要仅从模型生成的普通文本中推断用户权限。权限信息必须来自可信的应用上下文。

## 10. 配置、提示词和 Hook 的设计

### 10.1 配置

配置应按插件隔离：

```text
pluginId = my-plugin
key      = business.base-url
value    = http://business-service:8080
```

插件通过 `PluginContext` 读取，而不是直接依赖宿主配置对象。

### 10.2 系统提示词

插件可以贡献提示词，用于说明：

- 工具适用场景。
- 工具调用顺序。
- 数据真实性要求。
- 禁止事项。
- 输出格式。

提示词是模型治理层，不是权限层。任何关键限制都必须在 Tool 或业务 API 中再次执行。

### 10.3 Hook

Hook 适合处理横切能力：

- 调用审计。
- 敏感参数检查。
- 前置阻断。
- 结果观察。
- 业务事件通知。

常见 Hook 点：

```text
PRE_TOOL_USE
POST_TOOL_USE
USER_PROMPT_SUBMIT
SESSION_START
STOP
```

Hook 不应该承担核心业务逻辑，否则插件会变得难以测试和维护。

## 11. 安全设计

### 11.1 ClassLoader 不是安全边界

ClassLoader 解决的是类命名空间和依赖隔离，不解决恶意代码执行问题。

需要安全隔离时，应增加：

- 独立 JVM 进程。
- 容器。
- 远程沙箱。
- 网络策略。
- 文件系统权限。
- 资源配额。

### 11.2 最小权限

每个插件和每个 Tool 只获得完成任务所需的最小权限：

- 只读能力与写能力分开。
- 查询与删除能力分开。
- 不把通用数据库管理员账号交给插件。
- 不把宿主文件系统全部暴露给插件。

### 11.3 多层校验

安全控制应至少存在于：

```text
模型提示词
  ↓
Tool 参数 Schema
  ↓
插件本地校验
  ↓
Harness Hook / Approval
  ↓
业务 API 权限校验
  ↓
数据库权限
```

任何一层都不能被视为唯一安全防线。

### 11.4 数据脱敏

插件返回给模型的数据应该经过裁剪：

- 不返回无关字段。
- 不返回密码、Token 和密钥。
- 不返回完整身份证号、手机号等敏感信息。
- 控制单次结果大小。
- 对错误消息做脱敏。

## 12. 错误、超时与可观测性

插件是外部系统调用方，必须假设业务 API 会失败。

### 12.1 错误分类

建议区分：

```text
INVALID_ARGUMENT   参数错误
UNAUTHORIZED       权限失败
NOT_FOUND          业务对象不存在
TIMEOUT            业务 API 超时
UPSTREAM_ERROR     外部服务错误
RATE_LIMITED       触发限流
INTERNAL_ERROR     插件内部错误
```

### 12.2 超时策略

至少设置三类超时：

- 连接建立超时。
- 单次请求超时。
- Agent 工具协作超时。

超时后应该返回结构化失败结果，而不是让线程永久阻塞。

### 12.3 观测指标

建议记录：

- 插件加载成功和失败次数。
- 插件启动耗时。
- Tool 调用次数。
- Tool 成功率和失败率。
- Tool 平均耗时和 P95 耗时。
- 业务 API 返回状态。
- 超时和限流次数。
- 插件 ClassLoader 重新加载次数。

日志中应包含 `pluginId`、`toolName`、`callId` 和 `sessionId`，但不要记录敏感参数原文。

## 13. 版本与兼容性

插件系统至少有三组版本：

```text
Host Version       Harness 宿主版本
SPI Version        dsh-java-types 版本
Plugin Version     业务插件版本
```

兼容策略应该明确：

- SPI 接口增加方法时提供默认实现。
- Tool 参数增加字段时保持向后兼容。
- Tool 名称不要随意修改。
- 插件元数据版本用于展示和升级判断。
- 宿主升级前检查插件依赖。
- 插件升级失败时保留旧版本回滚能力。

## 14. 插件实现落地顺序

推荐按以下顺序实现：

```text
1. 定义业务边界和最小能力
2. 设计 Tool 名称、描述和参数
3. 设计业务 API 或数据访问接口
4. 创建 Plugin Entry
5. 实现 Tool
6. 增加参数校验和错误映射
7. 增加配置和鉴权
8. 增加 plugin.yaml 和 ServiceLoader
9. 编译并检查 JAR
10. 安装、激活、运行插件
11. 通过真实对话验证 Tool Call
12. 增加日志、指标、超时和资源释放
```

## 15. 设计检查清单

```text
[ ] 插件只依赖稳定 SPI，不依赖宿主内部实现
[ ] 插件 ID、入口类和元数据保持一致
[ ] Tool 是清晰、单一职责的业务动作
[ ] description 能指导模型正确选择工具
[ ] parameters 与执行逻辑一致
[ ] 插件 ClassLoader 与宿主共享稳定契约类型
[ ] 插件没有把 ClassLoader 当成安全沙箱
[ ] Tool 调用经过统一 Registry 和 ToolCallExecutor
[ ] 业务 API 负责最终权限校验
[ ] 外部调用具备超时、错误码和限流处理
[ ] 敏感数据不会返回给模型
[ ] stop 时注销所有 Tool、Hook、Prompt 和事件
[ ] stop 时关闭线程、连接池、定时器和 ClassLoader
[ ] 插件升级具备兼容性和回滚策略
```

## 16. 总结

DSH Java 插件系统的核心不是“动态加载一个 JAR”，而是建立一套可治理的运行时扩展机制：

```text
稳定 SPI
  + 独立 ClassLoader
  + Tool Registry
  + 统一 ToolCallExecutor
  + PluginContext 生命周期管理
  + 业务 API 权限边界
  + Session / Hook / Approval / SSE
```

一次完整调用可以抽象为：

```text
用户消息
  → 外部应用
  → /api/agent/stream
  → Agent / ReAct Loop
  → 模型选择 Tool
  → ToolCallExecutor
  → 插件 Tool
  → 业务 API / 数据库
  → ToolResult
  → Agent 上下文
  → 模型最终回答
  → SSE 返回外部应用
```

其中，ClassLoader 负责插件代码的命名空间和依赖隔离，Plugin Runtime 负责插件生命周期，Tool Registry 负责能力发现，ToolCallExecutor 负责统一治理，业务系统负责最终数据和权限。这几层边界共同构成了 DSH Java 插件的设计基础。
