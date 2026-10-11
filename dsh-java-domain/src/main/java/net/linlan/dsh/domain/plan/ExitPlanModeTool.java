package net.linlan.dsh.domain.plan;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import net.linlan.dsh.domain.plan.model.valobj.PlanModeState;
import net.linlan.dsh.domain.plan.service.PlanModeService;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * `exit_plan_mode` 工具。
 * <p>
 * 执行流程：
 * 1. 接收模型提交的计划文本；
 * 2. 记录 plan/mode 退出事件；
 * 3. 将状态推进到等待批准。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class ExitPlanModeTool implements ToolDefinition {

    private static final String NAME = "exit_plan_mode";
    private static final String DESCRIPTION =
            "Present your proposed plan to the user for approval. " +
            "This exits plan mode. If the user approves, you may proceed " +
            "to execute the plan. If they reject it, you remain in plan mode " +
            "and must revise your approach.";

    private final SessionLog session;
    private final AtomicReference<PlanModeState> state;
    private final PlanModeService planModeService;

    /**
     * 处理Exit 计划 模式 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param session 会话参数。
     * @param state 状态对象。
     * @param planModeService 计划 模式 Service参数。
     * @return 方法执行结果。
     */
    public ExitPlanModeTool(SessionLog session, AtomicReference<PlanModeState> state, PlanModeService planModeService) {
        this.session = session;
        this.state = state;
        this.planModeService = planModeService;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "plan", Map.of(
                                "type", "string",
                                "description", "The proposed plan to present to the user for approval."
                        )
                ),
                "required", List.of("plan")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() { return Map.of(); }

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
        Map<String, Object> argMap = (Map<String, Object>) args;
        Object planValue = argMap.get("plan");
        String plan = planValue == null ? null : String.valueOf(planValue);
        if (plan == null || plan.isBlank()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("plan parameter is required and must be non-empty", "MISSING_PARAM"));
        }

        planModeService.submitPlan(session, state, plan.trim());
        return CompletableFuture.completedFuture(
                ToolExecutionResult.ok(List.of(new TextBlock("Plan submitted for approval."))));
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
     * 进入计划模式。
     * <p>
     * 执行流程：
     * 1. 记录 enter 事件；
     * 2. 切换内存状态；
     * 3. 供 Agent 启动计划阶段使用。
     */
    public static void enterPlanMode(SessionLog session, AtomicReference<PlanModeState> state, PlanModeService planModeService) {
        planModeService.enterPlanMode(session, state);
    }

    /**
     * 从会话事件恢复计划模式状态。
     * <p>
     * 执行流程：
     * 1. 遍历 plan/mode 事件；
     * 2. 读取最新状态；
     * 3. 返回可恢复的内存状态。
     */
    public static PlanModeState foldState(SessionLog session, PlanModeService planModeService) {
        return planModeService.foldState(session);
    }
}
