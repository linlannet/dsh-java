package net.linlan.dsh.api.gateway;

import net.linlan.dsh.api.gateway.dto.GatewayStreamRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IGatewayStreamApi {

    SseEmitter stream(GatewayStreamRequestDTO request);
}
