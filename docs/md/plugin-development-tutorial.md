# DSH Java 插件开发快速入门

本文以 [`dsh-java-mysql`](https://github.com/linlannet/dsh-java-mysql) 为例，说明如何开发一个 DSH Java 插件，并上传到 DSH-Java 使用。

插件开发的基本流程只有 5 步：

```text
引入依赖 → 编写插件入口 → 编写 Tool → 打包 JAR → 上传到 DSH-Java
```

## 一、准备环境

需要：

```text
JDK 17+
Maven 3.9+
DSH-Java 已启动：http://127.0.0.1:8090
```

如果使用 MySQL 案例，还需要启动管理后台：

```bash
cd dsh-java-mysql
mvn -pl dsh-java-mysql-app spring-boot:run
```

管理后台地址：

```text
http://127.0.0.1:8091
```

## 二、创建插件工程

插件就是一个普通 Maven 工程，最小结构如下：

```text
my-plugin/
├── pom.xml
└── src/main/
    ├── java/com/example/MyPlugin.java
    ├── java/com/example/HelloTool.java
    └── resources/META-INF/
        ├── plugin.yaml
        └── services/
            └── net.linlan.dsh.domain.spi.JavaHarnessPlugin
```

在 `pom.xml` 中加入 DSH-Java 插件开发依赖：

```xml
<dependency>
    <groupId>net.linlan.dsh</groupId>
    <artifactId>dsh-java-types</artifactId>
    <version>0.1.5</version>
    <scope>provided</scope>
</dependency>
```

实际案例可以参考：

```text
dsh-java-mysql/dsh-java-mysql-plugin/pom.xml
```

## 三、编写插件入口

插件入口继承 `AbstractHarnessPlugin`，并返回需要提供的工具：

```java
public class MyPlugin extends AbstractHarnessPlugin {

    public MyPlugin() {
        super("my-plugin");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new HelloTool());
    }
}
```

`my-plugin` 是插件 ID，后面要和 `plugin.yaml` 中的 `id` 保持一致。

## 四、编写一个 Tool

Tool 是插件真正提供的能力。继承 `AbstractTool`，实现工具名称、说明、参数和执行逻辑：

```java
public class HelloTool extends AbstractTool {

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public String description() {
        return "向用户返回问候语。";
    }

    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("name", stringSchema("用户姓名"))
                .required("name")
                .build();
    }

    @Override
    protected CompletableFuture<ToolExecutionResult> run(
            Map<String, Object> args, ToolRunContext ctx) {
        return ok("你好，" + str(args, "name") + "！");
    }
}
```

在 `dsh-java-mysql` 中，Tool 的实现方式相同，只是业务从简单问候变成了调用管理后台：

```java
return ok(post(
        "/api/mysql/" + connectionId + "/query",
        sqlJson(args, "sql"),
        args));
```

MySQL 案例已经实现了以下 Tool，可以直接参考源码：

```text
dsh-java-mysql/dsh-java-mysql-plugin/src/main/java/
└── net/linlan/dsh/mysql/plugin/DshMysqlPlugin.java
```

提供的工具包括：

```text
mysql_list_connections
mysql_read_query
mysql_explain_query
mysql_performance_snapshot
mysql_sql_review
```

## 五、添加插件元数据

### 1. plugin.yaml

文件：

```text
src/main/resources/META-INF/plugin.yaml
```

内容示例：

```yaml
id: my-plugin
name: My Plugin
version: 1.0.0
author: Your Name
description: My first DSH Java plugin.
entrypoint: com.example.MyPlugin
```

### 2. ServiceLoader 文件

文件路径必须是：

```text
src/main/resources/META-INF/services/net.linlan.dsh.domain.spi.JavaHarnessPlugin
```

内容为插件入口类的全限定名：

```text
com.example.MyPlugin
```

这两个文件必须打进最终 JAR，否则 DSH-Java 无法发现插件。

## 六、编译插件

在插件工程根目录执行：

```bash
mvn clean package -DskipTests
```

例如 `dsh-java-mysql` 插件的打包命令：

```bash
cd dsh-java-mysql
mvn -pl dsh-java-mysql-plugin package -DskipTests
```

生成的 JAR：

```text
dsh-java-mysql-plugin/target/dsh-java-mysql-plugin-0.1.0-SNAPSHOT.jar
```

可以简单检查元数据是否存在：

```bash
jar tf dsh-java-mysql-plugin/target/dsh-java-mysql-plugin-0.1.0-SNAPSHOT.jar \
  | grep 'META-INF/'
```

## 七、上传到 DSH-Java

使用插件安装接口上传 JAR：

```bash
JAR="$(pwd)/dsh-java-mysql-plugin/target/dsh-java-mysql-plugin-0.1.0-SNAPSHOT.jar"

curl -X POST http://127.0.0.1:8090/api/harness/plugins/install \
  -H 'Content-Type: application/json' \
  -d "{
    \"pluginId\": \"dsh-java-mysql-plugin\",
    \"displayName\": \"DSH MySQL Plugin\",
    \"pluginVersion\": \"0.1.0\",
    \"runtimeType\": \"JAVA_NATIVE\",
    \"sourcePath\": \"$JAR\",
    \"entrypoint\": \"dsh-java-mysql-plugin-0.1.0-SNAPSHOT.jar\"
  }"
```

然后激活插件：

```bash
curl -X POST http://127.0.0.1:8090/api/harness/plugins/activate \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"dsh-java-mysql-plugin"}'
```

查看插件状态：

```bash
curl http://127.0.0.1:8090/api/harness/plugins
```

## 八、开始使用

安装 `dsh-java-mysql` 插件后，先在 `http://127.0.0.1:8091` 添加一个 MySQL 连接。

然后在 DSH-Java 中直接提问，例如：

```text
列出当前数据库的所有表
查询 user 表前 10 条数据
分析这条 SQL 是否使用索引
查看当前数据库的性能情况
```

Agent 会根据问题调用插件工具：

```text
plugin__dsh-java-mysql-plugin__mysql_list_connections
plugin__dsh-java-mysql-plugin__mysql_read_query
plugin__dsh-java-mysql-plugin__mysql_explain_query
plugin__dsh-java-mysql-plugin__mysql_performance_snapshot
```

## 九、记住这几个要点

```text
1. 插件入口继承 AbstractHarnessPlugin。
2. 业务能力继承 AbstractTool。
3. plugin.yaml 和 ServiceLoader 文件必须放在 META-INF 下。
4. 插件依赖 dsh-java-types。
5. 打包成 JAR 后，通过 DSH-Java 插件管理或 HTTP 接口上传。
```

开发时直接参考 `dsh-java-mysql/dsh-java-mysql-plugin`，先复制它的工程结构，再把 MySQL 查询逻辑替换成自己的业务逻辑即可。
