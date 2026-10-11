package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IAgentApi {
    Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request);

    Response<AgentMessageResponseDTO> sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink);

    Response<String> getAgentStatus(String agentId);

    Response<Void> cancelAgent(String agentId);
}
