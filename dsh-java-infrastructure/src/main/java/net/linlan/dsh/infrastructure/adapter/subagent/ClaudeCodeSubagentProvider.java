package net.linlan.dsh.infrastructure.adapter.subagent;

import java.util.List;
import java.util.Map;

/**
 * Out-of-process Claude Code subagent backend. Drives a child agent in a
 * spawned subprocess using the Claude Code CLI. Corresponds to TS
 * {@code subagent-claude-code}.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class ClaudeCodeSubagentProvider extends OutOfProcessSubagentProvider {

    /**
     * 初始化 Claude Code 子智能体提供者。
     * <p>流程：配置子进程命令和工作目录 → 通过外部进程执行任务。</p>
     */
    public ClaudeCodeSubagentProvider(String command, List<String> args, String cwd,
                                      Map<String, String> env, long timeoutMs) {
        super(command, args, cwd, env, timeoutMs);
    }

    @Override
    public String name() { return "claude-code"; }

    @Override
    protected String encodePrompt(SubagentStartRequest request) {
        // Claude Code CLI: prompt is passed as plain text on stdin
        return request.prompt();
    }
}
