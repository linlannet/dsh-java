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

    /** 返回连接器评估目录（品牌、多语言名称与功能预览）。 */
    @GetMapping("/assessments")
    public Response<List<ConnectorAssessmentDTO>> assessments() {
        return connectorApi.listAssessments();
    }

    /** 返回已注册 Provider 的账号状态列表。 */
    @GetMapping("/accounts")
    public Response<List<ConnectorAccountStatusDTO>> accounts() {
        return connectorApi.listAccounts();
    }

    /** 查询单个连接器的账号状态。 */
    @GetMapping("/accounts/{connectorId}")
    public Response<ConnectorAccountStatusDTO> account(@PathVariable String connectorId) {
        return connectorApi.getAccount(connectorId);
    }

    /** 发起首次连接或再次授权；OAuth 连接器返回授权地址。 */
    @PostMapping("/accounts/{connectorId}/connect")
    public Response<ConnectorAccountStatusDTO> connect(@PathVariable String connectorId) {
        return connectorApi.connect(connectorId);
    }

    /** OAuth 回调：匿名访问，交付后端校验 code/state 并一次性消费。 */
    @GetMapping("/accounts/{connectorId}/oauth/callback")
    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    public Response<ConnectorAccountStatusDTO> oauthCallback(@PathVariable String connectorId,
                                                             @RequestParam String code,
                                                             @RequestParam String state) {
        return connectorApi.completeAuthorization(connectorId, code, state);
    }

    /** 断开连接并清理本地令牌缓存。 */
    @PostMapping("/accounts/{connectorId}/disconnect")
    public Response<ConnectorAccountStatusDTO> disconnect(@PathVariable String connectorId) {
        return connectorApi.disconnect(connectorId);
    }

    /** 显式刷新令牌；不支持被动刷新的 Provider 返回明确诊断。 */
    @PostMapping("/accounts/{connectorId}/refresh")
    public Response<ConnectorAccountStatusDTO> refresh(@PathVariable String connectorId) {
        return connectorApi.refresh(connectorId);
    }
}
