package net.linlan.dsh.domain.connector;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Java 侧评估与展示目录；账号提供方按插件注册，不引入 Node CLI。 */
public final class ConnectorAssessmentCatalog {
    private static final List<ConnectorAssessment> ASSESSMENTS = List.of(
            new ConnectorAssessment("feishu", "Feishu", "lark-cli + 技能",
                    "复用现有飞书 REST/WebSocket 接入，账号提供方按端口注册", "接入",
                    "保留现有 Java 接入，不引入 lark-cli 运行时", "feishu",
                    Map.of("zh-CN", "飞书", "en-US", "Feishu"),
                    Map.of("zh-CN", List.of("消息与群组协作", "文档与知识库"),
                            "en-US", List.of("Messaging and group collaboration", "Docs and knowledge base"))),
            new ConnectorAssessment("wecom", "WeCom", "CLI 账号套件",
                    "企业微信开放平台 REST（应用消息/通讯录）", "替代实现",
                    "由企业应用配置驱动的 Java REST 账号提供方替代官方 CLI", "wecom",
                    Map.of("zh-CN", "企业微信", "en-US", "WeCom"),
                    Map.of("zh-CN", List.of("应用消息", "企业通讯录"),
                            "en-US", List.of("Application messaging", "Enterprise contacts"))),
            new ConnectorAssessment("dingtalk", "DingTalk", "dws.exe",
                    "钉钉开放平台 REST", "替代实现",
                    "以 Java REST 账号提供方替代 Windows CLI，不依赖 dws.exe", "dingtalk",
                    Map.of("zh-CN", "钉钉", "en-US", "DingTalk"),
                    Map.of("zh-CN", List.of("组织消息", "审批与日程"),
                            "en-US", List.of("Organization messaging", "Approvals and calendar"))),
            new ConnectorAssessment("notion", "Notion", "共享 MCP 管理器",
                    "复用 HTTP MCP 客户端与通用 OAuth 状态契约", "替代实现",
                    "由 Notion MCP 账号提供方实现授权、刷新与回调，不新增厂商 SDK", "notion",
                    Map.of("zh-CN", "Notion", "en-US", "Notion"),
                    Map.of("zh-CN", List.of("页面与数据库查询", "知识内容同步"),
                            "en-US", List.of("Page and database queries", "Knowledge synchronization"))),
            new ConnectorAssessment("linear", "Linear", "共享 MCP 管理器",
                    "复用 HTTP MCP 客户端与通用 OAuth 状态契约", "替代实现",
                    "由 Linear MCP 账号提供方实现授权、刷新与回调，不新增厂商 SDK", "linear",
                    Map.of("zh-CN", "Linear", "en-US", "Linear"),
                    Map.of("zh-CN", List.of("议题与项目查询", "研发进度同步"),
                            "en-US", List.of("Issue and project queries", "Engineering progress synchronization"))),
            new ConnectorAssessment("tencent-docs", "Tencent Docs", "CLI 账号套件",
                    "腾讯文档开放接口能力受限，本版不建立默认提供方", "不接入",
                    "取得明确的企业开放接口授权后再按 REST 账号端口增量接入", "tencent-docs",
                    Map.of("zh-CN", "腾讯文档", "en-US", "Tencent Docs"),
                    Map.of("zh-CN", List.of("文档能力评估", "等待企业接口授权"),
                            "en-US", List.of("Document capability assessment", "Pending enterprise API access"))),
            new ConnectorAssessment("tencent-meeting", "Tencent Meeting", "tmeet CLI",
                    "腾讯会议 REST 仅面向企业自建应用，本版默认不接入", "不接入",
                    "具备企业自建应用条件时以 Java REST 账号提供方替代 tmeet", "tencent-meeting",
                    Map.of("zh-CN", "腾讯会议", "en-US", "Tencent Meeting"),
                    Map.of("zh-CN", List.of("会议能力评估", "等待企业自建应用"),
                            "en-US", List.of("Meeting capability assessment", "Pending enterprise application")))
    );

    private ConnectorAssessmentCatalog() {
    }

    /** 全部评估条目（不可变）。 */
    public static List<ConnectorAssessment> all() {
        return ASSESSMENTS;
    }

    /** 按 connectorId 查找评估条目。 */
    public static Optional<ConnectorAssessment> find(String connectorId) {
        return ASSESSMENTS.stream().filter(item -> item.connectorId().equals(connectorId)).findFirst();
    }
}
