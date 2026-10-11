package net.linlan.dsh.domain.sandbox.model.valobj;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Resolved sandbox policy for one capability call.
 *
 * @param mode           file-effect mode
 * @param workspaceRoot  absolute workspace root for workspace-write mode
 * @param workspaceRoots all writable roots（workspace-write 模式下的完整可写根集合）
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record SandboxExecutionPolicy(SandboxMode mode, Optional<Path> workspaceRoot, List<Path> workspaceRoots) {

    /**
     * 读取 Only。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static SandboxExecutionPolicy readOnly() {
        return new SandboxExecutionPolicy(SandboxMode.READ_ONLY, Optional.empty(), List.of());
    }

    /**
     * 处理工作区 Write。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param root 根路径。
     * @return 方法执行结果。
     */
    public static SandboxExecutionPolicy workspaceWrite(Path root) {
        return new SandboxExecutionPolicy(SandboxMode.WORKSPACE_WRITE, Optional.of(root), List.of(root));
    }

    /**
     * 以多个可写根创建 workspace-write 策略。
     * @param roots 可写根集合（非空）。
     * @return 方法执行结果。
     */
    public static SandboxExecutionPolicy workspaceWrite(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("workspaceWrite roots must be non-empty");
        }
        return new SandboxExecutionPolicy(SandboxMode.WORKSPACE_WRITE, Optional.of(roots.get(0)), List.copyOf(roots));
    }

    /**
     * 处理full Access。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static SandboxExecutionPolicy fullAccess() {
        return new SandboxExecutionPolicy(SandboxMode.DANGER_FULL_ACCESS, Optional.empty(), List.of());
    }

    /**
     * 判断给定路径是否位于任一可写根之下。
     * @param target 目标路径。
     * @return true 表示允许写入。
     */
    public boolean allowsWriteWithin(Path target) {
        List<Path> roots = workspaceRoots != null && !workspaceRoots.isEmpty()
                ? workspaceRoots
                : workspaceRoot.map(List::of).orElse(List.of());
        return roots.stream().anyMatch(target::startsWith);
    }
}