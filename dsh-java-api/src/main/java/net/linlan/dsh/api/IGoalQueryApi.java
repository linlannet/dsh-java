package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IGoalQueryApi {

    Response<GoalViewResponseDTO> getGoal(String sessionId);
}
