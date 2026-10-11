package net.linlan.dsh.domain.tool.shell;

/**
 * IShellExecutor 表示IShellExecutor。
 */
public interface IShellExecutor {

    /**
     * 执行领域动作并返回结果。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param command 命令。
     * @param timeoutMs timeoutMsM列表。
     * @return 领域处理结果。
     */
    ShellResult execute(String command, String cwd, long timeoutMs);
}
