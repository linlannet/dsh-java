package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.IConnectorApi;
import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.filter.RouteAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 插件页连接器状态与账号操作入口。 */
@RestController
@RequestMapping("/api/harness/connectors")
public class ConnectorController {
    private final IConnectorApi connectorApi;

    public ConnectorController(IConnectorApi connectorApi) {
        this.connectorApi = connectorApi;
    }

    @GetMapping("/assessments")
    public Response<List<ConnectorAssessmentDTO>> assessments() {
        return connectorApi.listAssessments();
    }

    @GetMapping("/accounts")
    public Response<List<ConnectorAccountStatusDTO>> accounts() {
        return connectorApi.listAccounts();
    }

    @GetMapping("/accounts/{connectorId}")
    public Response<ConnectorAccountStatusDTO> account(@PathVariable String connectorId) {
        return connectorApi.getAccount(connectorId);
    }

    @PostMapping("/accounts/{connectorId}/connect")
    public Response<ConnectorAccountStatusDTO> connect(@PathVariable String connectorId) {
        return connectorApi.connect(connectorId);
    }

    @GetMapping("/accounts/{connectorId}/oauth/callback")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public Response<ConnectorAccountStatusDTO> oauthCallback(@PathVariable String connectorId,
                                                             @RequestParam String code,
                                                             @RequestParam String state) {
        return connectorApi.completeAuthorization(connectorId, code, state);
    }

    @PostMapping("/accounts/{connectorId}/disconnect")
    public Response<ConnectorAccountStatusDTO> disconnect(@PathVariable String connectorId) {
        return connectorApi.disconnect(connectorId);
    }

    @PostMapping("/accounts/{connectorId}/refresh")
    public Response<ConnectorAccountStatusDTO> refresh(@PathVariable String connectorId) {
        return connectorApi.refresh(connectorId);
    }
}
