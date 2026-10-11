package net.linlan.dsh.api.stream;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IAgentStreamApi {

    SseEmitter sendMessageStreaming(AgentMessageRequestDTO request);
}
