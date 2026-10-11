package net.linlan.dsh.domain.acp.model.valobj;

/**
 * Request to send a prompt to an ACP session.
 *
 * @param sessionId  session id
 * @param messages   prompt messages
 * @param cwd        working directory for the session
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record AcpPromptRequest(String sessionId, java.util.List<AcpMessage> messages, String cwd) {}
