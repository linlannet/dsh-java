package net.linlan.dsh.domain.tool.ssh;

import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;

/**
 * SandboxedSsh 在沙箱策略下执行 SSH 命令。
 *
 * <p>调用流程：根据策略包装命令 → 委托 ISshPort 执行。</p>
 *
 */
public final class SandboxedSsh {

    private final ISshPort port;
    private final SandboxExecutionPolicy policy;

    /**
     * 构造。
     * @param port   ISshPort 实现。
     * @param policy 沙箱策略。
     */
    public SandboxedSsh(ISshPort port, SandboxExecutionPolicy policy) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }
        this.port = port;
        this.policy = policy;
    }

    /**
     * 在沙箱下执行命令。
     * @param command 命令。
     * @return 执行结果。
     */
    public ISshPort.SshCommandResult execute(String command) {
        if (command == null || command.isBlank()) {
            return new ISshPort.SshCommandResult("", "empty command", -1);
        }
        if (policy == null) {
            return port.execute(command);
        }
        return switch (policy.mode()) {
            case READ_ONLY -> executeReadOnly(command);
            case WORKSPACE_WRITE -> executeWorkspaceWrite(command);
            case DANGER_FULL_ACCESS -> port.execute(command);
        };
    }

    private ISshPort.SshCommandResult executeReadOnly(String command) {
        String wrapped = "set -e; " + command;
        return port.execute(wrapped);
    }

    private ISshPort.SshCommandResult executeWorkspaceWrite(String command) {
        StringBuilder prefix = new StringBuilder("set -e; ");
        policy.workspaceRoots().forEach(root ->
                prefix.append("export DSH_WORKSPACE_ROOT=\"")
                        .append(root)
                        .append(":$DSH_WORKSPACE_ROOT\"; "));
        String wrapped = prefix + command;
        return port.execute(wrapped);
    }
}
