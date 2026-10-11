package net.linlan.dsh.domain.acp.service;

import net.linlan.dsh.domain.acp.IAcpServerService;
import net.linlan.dsh.domain.acp.adapter.port.IAcpAgentPort;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptRequest;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptResponse;
import net.linlan.dsh.domain.acp.model.valobj.AcpSessionInfo;
import org.springframework.stereotype.Service;

/**
 * ACP server service: delegates session management and prompt driving to the
 * agent port.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class AcpServerService implements IAcpServerService {

    private final IAcpAgentPort agentPort;

    /**
     * 处理ACP Server Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agentPort Agent Port参数。
     * @return 方法执行结果。
     */
    public AcpServerService(IAcpAgentPort agentPort) {
        this.agentPort = agentPort;
    }

    /**
     * 取消目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */

    /**
     * 取消目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */

    @Override
    public AcpSessionInfo newSession(String cwd, String mcpConfig) {
        return agentPort.initialize(cwd, mcpConfig);
    }

    /**
     * 关闭Session。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */

    /**
     * 关闭Session。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */

    @Override
    public AcpPromptResponse prompt(AcpPromptRequest request) {
        return agentPort.prompt(request);
    }

    @Override
    public void cancel(String sessionId) {
        agentPort.cancel(sessionId);
    }

    @Override
    public void closeSession(String sessionId) {
        agentPort.dispose(sessionId);
    }
}
