# 智能客服插件接入指南

## 结论

**可行。** Java 版 DeepSeek Harness 已经具备“核心 Agent 由宿主承载，业务能力通过插件接入”的基本能力。智能客服不要 fork Agent 主链路，推荐实现一个 `JAVA_NATIVE` 插件，把工单、订单、知识库、人机接管等能力注册成 Agent 工具。

当前原生插件运行时提供以下扩展点：

| 能力 | 插件 API | 智能客服用途 |
| --- | --- | --- |
| 工具 | `tools()` / `context.registerTool()` | 查订单、查知识库、创建工单、转人工 |
| 系统提示词 | `context.registerSystemPrompt()` | 注入客服话术、免责声明、升级规则 |
| Hook | `context.registerHook()` | 审计敏感操作、拦截高风险工具、脱敏日志 |
| 事件 | `context.subscribe()` / `context.emit()` | 记录会话开始、工具调用、客服升级 |
| 配置 | `context.getConfig()` | 读取租户 ID、API Base、知识库开关 |
| 生命周期 | `onStart()` / `onStop()` | 初始化连接池、指标、后台任务并可靠释放 |

工具注册后的全局名称为：

```text
plugin__<pluginId>__<toolName>
```

例如插件 `acme-smartcs` 中的 `query_order` 会注册为：

```text
plugin__acme-smartcs__query_order
```

## 推荐架构

```text
用户渠道
  ↓
业务系统 / IM 网关
  ↓  POST /api/agent/message 或 /api/agent/stream
DeepSeek Harness Agent
  ↓
智能客服 Java Plugin
  ├─ search_knowledge
  ├─ query_order
  ├─ create_ticket
  └─ transfer_to_human
  ↓
客服知识库 / CRM / 工单系统 / 人工坐席系统
```

插件只封装业务能力，不负责实现 Agent 循环、工具调度、会话事件、审批和恢复。这样可以把模型和 Agent 的变化收敛在宿主内，把租户业务变化收敛在插件内。

## 1. 准备 Harness

```bash
mvn clean package -DskipTests

export DEEPSEEK_API_KEY='your-model-api-key'

java -jar dsh-java-app/target/dsh-java-app-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=standalone
```

默认端口是 `8090`。如果 `harness.auth.api-keys` 已配置鉴权，所有 `curl` 都要额外携带 API Key。

## 2. 生成插件骨架

```bash
mvn -q -pl dsh-java-types install -DskipTests
mvn -q -pl plugins/plugin-archetype install -DskipTests

mvn archetype:generate \
  -DarchetypeGroupId=net.linlan \
  -DarchetypeArtifactId=dsh-plugin-archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=com.acme \
  -DartifactId=acme-smartcs-plugin \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=com.acme.smartcs \
  -DpluginId=acme-smartcs \
  -DpluginName='Acme Smart CS Plugin' \
  -DpluginClass=SmartCsPlugin \
  -DpluginDescription='Smart customer service plugin' \
  -DpluginAuthor='Acme Team' \
  -DinteractiveMode=false
```

生成的工程只依赖 `net.linlan:dsh-java-types`，依赖作用域为 `provided`，不要把宿主、Spring 或模型 SDK 打进插件 JAR。

## 3. 实现最小智能客服插件

以下示例保留固定内存知识库和工单存储，生产实现替换为真实客户端即可。为了示例自包含，每个工具都只返回文本结果。

```java
package com.acme.smartcs;

import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SmartCsPlugin extends AbstractHarnessPlugin {

    public SmartCsPlugin() {
        super("acme-smartcs");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new SearchKnowledge(), new CreateTicket(), new TransferToHuman());
    }

    @Override
    public void configure(PluginContext context) {
        context.registerSystemPrompt(
                "customer-service-policy",
                10,
                """
                ## Acme Customer Service
                - 回答订单、退款、物流问题时先调用业务工具，不要臆测。
                - 无法判断或用户情绪升级时调用 transfer_to_human。
                - 不承诺未由工具确认的退款金额和时间。
                """
        );

        context.registerHook("POST_TOOL_USE", (toolName, payload) -> {
            if (toolName.startsWith("plugin__acme-smartcs__")) {
                log.info("smartcs tool called: {}", toolName);
            }
            return null;
        });
    }

    static class SearchKnowledge extends AbstractTool {
        @Override public String name() { return "search_knowledge"; }
        @Override public String description() { return "Search the customer service knowledge base."; }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("question", stringSchema("Customer question"))
                    .required("question")
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String question = str(args, "question");
            if (question.isBlank()) {
                return fail("question is required", "INVALID_QUESTION");
            }
            return ok("知识库结果：退款申请可在下单后 7 天内提交，需提供订单号。");
        }
    }

    static class CreateTicket extends AbstractTool {
        @Override public String name() { return "create_ticket"; }
        @Override public String description() { return "Create a customer service ticket."; }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customerId", stringSchema("Customer ID"))
                    .prop("summary", stringSchema("Ticket summary"))
                    .prop("priority", enumSchema("Ticket priority", List.of("P1", "P2", "P3")))
                    .required("customerId", "summary")
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String customerId = str(args, "customerId");
            String summary = str(args, "summary");
            String priority = str(args, "priority", "P3");
            if (customerId.isBlank() || summary.isBlank()) {
                return fail("customerId and summary are required", "INVALID_TICKET");
            }
            String ticketId = "CS-" + UUID.randomUUID();
            return ok("工单已创建：" + ticketId + "，优先级：" + priority);
        }
    }

    static class TransferToHuman extends AbstractTool {
        @Override public String name() { return "transfer_to_human"; }
        @Override public String description() { return "Transfer the conversation to a human agent."; }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("reason", stringSchema("Why human help is required"))
                    .required("reason")
                    .build();
        }

        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok("已进入人工客服队列，请稍候。");
        }
    }
}
```

## 4. 编译打包

```bash
cd acme-smartcs-plugin
mvn package
cp target/acme-smartcs-plugin-1.0.0-SNAPSHOT.jar /path/to/harness/plugins/
```

## 5. 注册并启用插件

### 方式一：HTTP 安装

`sourcePath` 使用插件 JAR 的绝对路径；宿主会先把它复制到 `harness.extensions.plugins.install-root`：

```bash
curl -X POST http://localhost:8090/api/harness/plugins/install \
  -H 'Content-Type: application/json' \
  -d '{
    "pluginId": "acme-smartcs",
    "displayName": "Acme Smart CS Plugin",
    "pluginVersion": "1.0.0-SNAPSHOT",
    "runtimeType": "JAVA_NATIVE",
    "sourcePath": "/Users/acme/devops/dsh-java/plugins/acme-smartcs-plugin-1.0.0-SNAPSHOT.jar",
    "entrypoint": "acme-smartcs-plugin-1.0.0-SNAPSHOT.jar"
  }'

curl -X POST http://localhost:8090/api/harness/plugins/run \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"acme-smartcs"}'
```

### 方式二：配置文件预装

写入 `harness.yml`：

```yaml
harness:
  extensions:
    plugins:
      install-root: ./plugins
      preset:
        - plugin-id: acme-smartcs
          display-name: Acme Smart CS Plugin
          plugin-version: 1.0.0-SNAPSHOT
          runtime-type: JAVA_NATIVE
          source-path: ./plugins/acme-smartcs-plugin-1.0.0-SNAPSHOT.jar
          entrypoint: acme-smartcs-plugin-1.0.0-SNAPSHOT.jar
          auto-start: true
```

生产部署建议使用版本化文件名和不可变镜像，避免同名 JAR 被覆盖导致热重载状态与发布版本不一致。

## 6. 验证插件

查询状态：

```bash
curl http://localhost:8090/api/harness/plugins/acme-smartcs/status
```

发送一条客服消息：

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "customer-service-demo",
    "message": "退款规则是什么？如果需要人工帮我创建工单。"
  }'
```

通过的验收标准：

1. 插件状态为运行中。
2. Agent 工具目录中存在 `plugin__acme-smartcs__search_knowledge`、`plugin__acme-smartcs__create_ticket`、`plugin__acme-smartcs__transfer_to_human`。
3. 退款问题触发知识库工具。
4. 请求人工时触发热线转人工工具。
5. 停用插件后，三个工具从下一轮对话中消失。

## 生产化建议

| 关注点 | 建议 |
| --- | --- |
| 权限 | 只读工具优先；写 CRM、退款、换绑手机号等工具必须进入审批、幂等和二次确认流程 |
| 数据 | 工具入参不透传全量用户隐私；日志和 Hook 中做脱敏 |
| 超时 | 每个工具实现明确超时和降级文案，避免客服会话阻塞 |
| 幂等 | 创建工单、退款、发送短信等写操作必须带业务幂等键 |
| 可观测 | 记录 `sessionId`、租户、工具名、耗时、错误码和升级原因 |
| 配置 | 租户、环境、API 地址通过配置中心或启动参数注入，不硬编码在 JAR 中 |
| 回滚 | 保留上一个稳定插件 JAR；异常时 disable 新版本、enable 旧版本 |

## 当前边界

- 原生插件适合注册工具、Hook、事件和提示词；不建议在插件中自行替换 Agent 循环。
- 智能客服的“多渠道接入”应由业务网关或 IM 适配器先统一成 Agent 消息，再进入 Harness。
- 复杂人工坐席状态机建议放在企业客服系统，插件只暴露 `transfer_to_human` 和查询接口。
- 如果需要直接扩展 MCP 服务，可将知识库或 CRM 包装成 MCP Server，再配置到 `harness.extensions.mcp.servers`；只有需要更紧密生命周期、配置和 Hook 时才使用 Java 插件。
