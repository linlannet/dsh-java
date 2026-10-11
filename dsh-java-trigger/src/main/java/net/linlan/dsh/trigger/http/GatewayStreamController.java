package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.gateway.IGatewayStreamApi;
import net.linlan.dsh.api.gateway.dto.GatewayStreamRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class GatewayStreamController {

    private static final Logger log = LoggerFactory.getLogger(GatewayStreamController.class);
    private final IGatewayStreamApi gatewayStreamApi;

    public GatewayStreamController(IGatewayStreamApi gatewayStreamApi) {
        this.gatewayStreamApi = gatewayStreamApi;
    }

    @PostMapping(value = "/api/gateway/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody GatewayStreamRequestDTO request) {
        log.info("[GatewayStream] 数字人网关请求进入，source={}，requestType={}",
                request == null ? "<null>" : request.source(), requestType(request));
        return gatewayStreamApi.stream(request);
    }

    private String requestType(GatewayStreamRequestDTO request) {
        if (request == null) {
            return "unknown";
        }
        if (request.agent() != null) {
            return "agent";
        }
        if (request.workflow() != null) {
            return "workflow";
        }
        return "empty";
    }
}
