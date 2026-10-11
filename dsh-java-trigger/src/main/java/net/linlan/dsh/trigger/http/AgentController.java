package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.IAgentApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.api.stream.IAgentStreamApi;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IAgentApi agentApi;
    private final IAgentStreamApi agentStreamApi;

    public AgentController(IAgentApi agentApi, IAgentStreamApi agentStreamApi) {
        this.agentApi = agentApi;
        this.agentStreamApi = agentStreamApi;
    }

    @PostMapping("/message")
    public Response<AgentMessageResponseDTO> sendMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentApi.sendMessage(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentStreamApi.sendMessageStreaming(request);
    }

    @GetMapping("/{agentId}/status")
    public Response<String> getAgentStatus(@PathVariable("agentId") String agentId) {
        return agentApi.getAgentStatus(agentId);
    }

    @PostMapping("/{agentId}/cancel")
    public Response<Void> cancelAgent(@PathVariable("agentId") String agentId) {
        return agentApi.cancelAgent(agentId);
    }
}
