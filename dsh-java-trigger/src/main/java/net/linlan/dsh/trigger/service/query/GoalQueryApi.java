package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.cases.goal.IGoalQueryCase;
import net.linlan.dsh.api.IGoalQueryApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.stereotype.Service;

/**
 * 目标查询适配器。
 * <p>
 * 执行流程：
 * 1. 接收目标查询请求；
 * 2. 调用领域查询服务；
 * 3. 转换为展示响应。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class GoalQueryApi implements IGoalQueryApi {

    private final IGoalQueryCase goalQueryCase;

    /**
     * 处理目标 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param goalQueryService 目标 Query Service参数。
     * @return 方法执行结果。
     */
    public GoalQueryApi(IGoalQueryCase goalQueryCase) {
        this.goalQueryCase = goalQueryCase;
    }

    /**
     * 查询当前目标。
     * <p>
     * 执行流程：
     * 1. 读取 sessionId；
     * 2. 查询领域目标快照；
     * 3. 返回可展示结果。
     */
    public Response<GoalViewResponseDTO> getGoal(String sessionId) {
        requireSessionId(sessionId);
        return execute(() -> {
            GoalViewResponseDTO view = goalQueryCase.getGoal(sessionId);
            return new GoalViewResponseDTO(
                    view.sessionId(), view.goalId(), view.revision(), view.title(), view.description(),
                    view.status(), view.blockedReason(), view.maxRounds(), view.roundsStarted(),
                    view.continuationEnabled(), view.createdAt(), view.updatedAt(), view.exists()
            );
        });
    }

    private void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
