package net.linlan.dsh.domain.tool.session;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.IConversationQueryService;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationMessageVO;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationSessionSummaryVO;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code session_search} 工具：检索历史会话。
 * <p>
 * 执行流程：
 * 1. 不带 query 时列出最近会话摘要；
 * 2. 带 query 时在最近会话的消息文本中做包含匹配；
 * 3. 返回命中会话与片段。
 * <p>
 * Mirrors TS {@code tool-session-query}（简化为单工具两模式）。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class SessionSearchTool implements ToolDefinition {

    private static final int MAX_SESSIONS_SCANNED = 20;
    private static final int MAX_HITS = 30;

    private final IConversationQueryService conversations;

    /**
     * 处理会话 搜索 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param conversations conversations参数。
     * @return 方法执行结果。
     */
    public SessionSearchTool(IConversationQueryService conversations) {
        this.conversations = conversations;
    }

    @Override public String name() { return "session_search"; }

    @Override
    public String description() {
        return "Search past conversation sessions. Without 'query', lists recent sessions. " +
                "With 'query', finds sessions whose messages contain the text and shows matching snippets.";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "query", Map.of("type", "string",
                                "description", "Optional text to search for in message contents."),
                        "limit", Map.of("type", "integer",
                                "description", "Max sessions to list/scan (default 20).")
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        try {
            Map<String, Object> m = args instanceof Map ? (Map<String, Object>) args : Map.of();
            String query = m.get("query") == null ? "" : String.valueOf(m.get("query")).trim();
            int limit = m.get("limit") instanceof Number n ? n.intValue() : MAX_SESSIONS_SCANNED;
            limit = Math.max(1, Math.min(limit, 100));

            List<ConversationSessionSummaryVO> sessions = conversations.listRecentSessions(limit, 0);
            if (sessions.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.ok(List.of(new TextBlock("No past sessions found."))));
            }

            if (query.isEmpty()) {
                StringBuilder sb = new StringBuilder("Recent sessions:\n");
                for (ConversationSessionSummaryVO s : sessions) {
                    sb.append("- ").append(s.sessionId())
                      .append(" | ").append(s.title() == null ? "" : s.title())
                      .append(" | ").append(s.status())
                      .append(" | ").append(s.lastActivityAt() != null ? s.lastActivityAt() : s.createdAt())
                      .append("\n");
                }
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
            }

            String needle = query.toLowerCase();
            StringBuilder sb = new StringBuilder("Sessions matching '").append(query).append("':\n");
            int hits = 0;
            for (ConversationSessionSummaryVO s : sessions) {
                List<ConversationMessageVO> messages;
                try {
                    messages = conversations.listMessages(s.sessionId());
                } catch (Exception e) {
                    continue;
                }
                boolean sessionHit = false;
                for (ConversationMessageVO msg : messages) {
                    String content = msg.content();
                    if (content == null) continue;
                    int idx = content.toLowerCase().indexOf(needle);
                    if (idx < 0) continue;
                    if (!sessionHit) {
                        sb.append("\n## ").append(s.sessionId())
                          .append(" | ").append(s.title() == null ? "" : s.title()).append("\n");
                        sessionHit = true;
                    }
                    int from = Math.max(0, idx - 60);
                    int to = Math.min(content.length(), idx + needle.length() + 60);
                    sb.append("- [").append(msg.role()).append("] ...")
                      .append(content.substring(from, to).replace("\n", " "))
                      .append("...\n");
                    if (++hits >= MAX_HITS) break;
                }
                if (hits >= MAX_HITS) break;
            }
            if (hits == 0) sb.append("(no matches)");
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("session_search failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
}
