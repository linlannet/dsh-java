package net.linlan.dsh.infrastructure.adapter.subagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/**
 * Out-of-process Codex subagent backend. Drives a child agent in a spawned
 * subprocess using the Codex wire protocol. Corresponds to TS
 * {@code subagent-codex}.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class CodexSubagentProvider extends OutOfProcessSubagentProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 初始化 Codex 子智能体提供者。
     * <p>流程：配置子进程命令和工作目录 → 调用外部 Agent 执行任务。</p>
     */
    public CodexSubagentProvider(String command, List<String> args, String cwd,
                                 Map<String, String> env, long timeoutMs) {
        super(command, args, cwd, env, timeoutMs);
    }

    @Override
    public String name() { return "codex"; }

    @Override
    protected String encodePrompt(SubagentStartRequest request) {
        // Codex wire format: JSON with instructions and optional tools
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("instructions", request.prompt());
            if (request.parentCwd() != null) root.put("cwd", request.parentCwd());
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return request.prompt();
        }
    }
}
