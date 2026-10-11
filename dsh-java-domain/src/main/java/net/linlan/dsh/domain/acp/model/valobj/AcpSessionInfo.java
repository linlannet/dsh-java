package net.linlan.dsh.domain.acp.model.valobj;

/**
 * Session metadata for an ACP session.
 *
 * @param sessionId  session id
 * @param cwd        working directory
 * @param mcpConfig  MCP configuration JSON (optional)
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record AcpSessionInfo(String sessionId, String cwd, String mcpConfig) {}
