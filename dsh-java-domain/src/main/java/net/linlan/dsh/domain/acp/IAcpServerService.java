package net.linlan.dsh.domain.acp;

import net.linlan.dsh.domain.acp.model.valobj.AcpPromptRequest;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptResponse;
import net.linlan.dsh.domain.acp.model.valobj.AcpSessionInfo;

/**
 * Application-facing ACP server seam. Exposes fresh harness sessions to trusted
 * programmatic clients over JSON-RPC stdio.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IAcpServerService {

    /**
     * 处理newSession会话。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param cwd 工作目录。
     * @return 领域处理结果。
     */
    AcpSessionInfo newSession(String cwd, String mcpConfig);

    /**
     * 处理提示词。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param request 请求。
     * @return 领域处理结果。
     */
    AcpPromptResponse prompt(AcpPromptRequest request);

    /**
     * 处理cancel。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    void cancel(String sessionId);

    /**
     * 关闭资源或生命周期。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    void closeSession(String sessionId);
}
