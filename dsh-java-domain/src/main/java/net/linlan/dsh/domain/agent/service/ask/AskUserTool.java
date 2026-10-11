package net.linlan.dsh.domain.agent.service.ask;

import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code ask_user_question} tool.
 * <p>
 * Mirrors TS {@code dsh-tool-ask-user}. Pauses the agent loop until the
 * user responds, then feeds the answer back as a tool result.
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public class AskUserTool implements ToolDefinition {

    private static final String NAME = "ask_user_question";

    private static final String DESCRIPTION =
            "Ask the user a concise question when you need confirmation, a choice, " +
            "or missing information before proceeding. Send one or more questions, " +
            "each with a stable id that will be echoed in the answer.";

    private final UserQuestionGateway userQuestions;
    private final String agentId;

    /**
     * 询问 用户 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param userQuestions 用户问答网关。
     * @param agentId Agent ID。
     * @return 方法执行结果。
     */
    public AskUserTool(UserQuestionGateway userQuestions, String agentId) {
        this.userQuestions = userQuestions;
        this.agentId = agentId;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */

    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "questions", Map.of(
                                "type", "array",
                                "description", "Questions to ask the user before continuing.",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", true,
                                        "properties", Map.of(
                                                "id", Map.of("type", "string", "description", "Stable id for this question; echoed in the answer."),
                                                "question", Map.of("type", "string", "description", "The specific question to ask the user."),
                                                "header", Map.of("type", "string", "description", "Optional short heading for the question."),
                                                "options", Map.of(
                                                        "type", "array",
                                                        "description", "Optional choices to show the user.",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", true,
                                                                "properties", Map.of(
                                                                        "label", Map.of("type", "string", "description", "Short user-facing option label."),
                                                                        "description", Map.of("type", "string", "description", "One sentence explaining the tradeoff or impact.")
                                                                )
                                                        )
                                                ),
                                                "multi_select", Map.of("type", "boolean", "description", "Whether the user may select more than one option.")
                                        )
                                )
                        )
                ),
                "required", List.of("questions")
        );
    }

    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @Override
    public Map<String, Object> outputSchema() { return Map.of(); }

    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */

    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        try {
            if (!userQuestions.isAvailable()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("no user-questions channel is available", "NO_CHANNEL"));
            }

            Map<String, Object> argMap = (Map<String, Object>) args;
            List<Map<String, Object>> rawQuestions = (List<Map<String, Object>>) argMap.get("questions");
            if (rawQuestions == null || rawQuestions.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("questions parameter is required and must be non-empty", "MISSING_PARAM"));
            }

            List<UserQuestionGateway.Question> questions = new ArrayList<>();
            for (Map<String, Object> raw : rawQuestions) {
                String id = String.valueOf(raw.get("id"));
                String question = String.valueOf(raw.get("question"));
                String header = raw.get("header") != null ? String.valueOf(raw.get("header")) : null;

                List<UserQuestionGateway.QuestionOption> options = null;
                if (raw.get("options") != null) {
                    List<Map<String, Object>> rawOpts = (List<Map<String, Object>>) raw.get("options");
                    options = rawOpts.stream()
                            .map(o -> new UserQuestionGateway.QuestionOption(
                                    String.valueOf(o.get("label")),
                                    o.get("description") != null ? String.valueOf(o.get("description")) : null))
                            .toList();
                }

                boolean multiSelect = raw.get("multi_select") != null
                        && Boolean.parseBoolean(String.valueOf(raw.get("multi_select")));

                questions.add(new UserQuestionGateway.Question(id, question, header, options, multiSelect));
            }

            UserQuestionGateway.UserAnswer answer = userQuestions.ask(
                    new UserQuestionGateway.UserQuestionRequest(questions, agentId));

            StringBuilder sb = new StringBuilder();
            sb.append("{\"answers\":[");
            for (int i = 0; i < answer.answers().size(); i++) {
                var a = answer.answers().get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"id\":\"").append(escapeJson(a.id())).append("\"");
                sb.append(",\"selected\":");
                sb.append("[");
                for (int j = 0; j < a.selected().size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(a.selected().get(j))).append("\"");
                }
                sb.append("]");
                if (a.custom() != null) {
                    sb.append(",\"custom\":\"").append(escapeJson(a.custom())).append("\"");
                }
                sb.append("}");
            }
            sb.append("]}");
            String resultJson = sb.toString();

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(resultJson))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("ask_user_question failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return false; }

    /**
     * 转换为 Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 转换后的目标对象。
     */
    public ToolSchema toSchema() {
        return new ToolSchema(NAME, DESCRIPTION, parameters());
    }

    /**
     * 处理escape JSON。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param s s参数。
     * @return 方法执行结果。
     */
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
