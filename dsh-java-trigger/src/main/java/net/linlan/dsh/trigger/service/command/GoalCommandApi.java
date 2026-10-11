package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.GoalCommandRequestDTO;
import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.IGoalCommandApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.goal.IGoalCommandCase;
import org.springframework.stereotype.Service;

@Service
public class GoalCommandApi implements IGoalCommandApi {
    private final IGoalCommandCase goalCommandCase;

    public GoalCommandApi(IGoalCommandCase goalCommandCase) {
        this.goalCommandCase = goalCommandCase;
    }

    public Response<GoalViewResponseDTO> apply(String sessionId, GoalCommandRequestDTO request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new IllegalArgumentException("goal action must not be blank");
        }
        return execute(() -> goalCommandCase.apply(sessionId, request));
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
