package net.linlan.dsh.infrastructure.adapter.sandbox;

import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxMode;
import java.nio.file.Path;

/**
 * Local filesystem sandbox enforcer. Checks whether a path operation is allowed
 * under the resolved policy.
 *
 * <p>调用流程：沙箱策略解析 → 路径/命令校验 → 委托本地执行器。</p>
 * <p>示例：工作区写模式下，cwd 越界或危险命令会被拒绝。</p>
 *
 */
public class LocalSandboxEnforcer {

    /**
     * Check whether a write to the given path is allowed under the policy.
     *
     * @param policy resolved execution policy
     * @param path   target path
     * @return true if the write is allowed
     */
    public boolean canWrite(SandboxExecutionPolicy policy, Path path) {
        return switch (policy.mode()) {
            case DANGER_FULL_ACCESS -> true;
            case WORKSPACE_WRITE -> {
                Path root = policy.workspaceRoot().orElse(null);
                if (root == null) yield false;
                Path normalized = path.toAbsolutePath().normalize();
                Path normalizedRoot = root.toAbsolutePath().normalize();
                yield normalized.startsWith(normalizedRoot);
            }
            case READ_ONLY -> false;
        };
    }

    /**
     * Check whether a read from the given path is allowed. Reads are always
     * allowed under any mode (the sandbox restricts writes, not reads).
     */
    public boolean canRead(SandboxExecutionPolicy policy, Path path) {
        return true;
    }

    /**
     * 处理模式 Of。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param policy 策略对象。
     * @return 方法执行结果。
     */
    public SandboxMode modeOf(SandboxExecutionPolicy policy) {
        return policy.mode();
    }
}
