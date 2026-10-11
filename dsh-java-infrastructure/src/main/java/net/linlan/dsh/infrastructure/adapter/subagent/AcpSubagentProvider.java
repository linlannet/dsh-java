package net.linlan.dsh.infrastructure.adapter.subagent;

import java.util.List;
import java.util.Map;

/**
 * Out-of-process ACP subagent backend. Each child has its own process, session,
 * model, and tools, so it shares no parent context. Corresponds to TS
 * {@code subagent-acp}.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class AcpSubagentProvider extends OutOfProcessSubagentProvider {

    /**
     * 初始化 ACP 子智能体提供者。
     * <p>流程：配置子进程命令和工作目录 → 建立 ACP 会话。</p>
     */
    public AcpSubagentProvider(String command, List<String> args, String cwd,
                               Map<String, String> env, long timeoutMs) {
        super(command, args, cwd, env, timeoutMs);
    }

    @Override
    public String name() { return "acp"; }

    @Override
    protected String encodePrompt(SubagentStartRequest request) {
        // ACP uses JSON-RPC; for simplicity we send the prompt as plain text.
        // A full implementation would do initialize → session/new → session/prompt.
        return request.prompt();
    }
}
