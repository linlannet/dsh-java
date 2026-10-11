package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.GoalCommandRequestDTO;
import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IGoalCommandApi {

    Response<GoalViewResponseDTO> apply(String sessionId, GoalCommandRequestDTO request);
}
