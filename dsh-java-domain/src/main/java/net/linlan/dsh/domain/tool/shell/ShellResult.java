package net.linlan.dsh.domain.tool.shell;

/**
 * ShellResult 表示ShellResult结果。
 */
public record ShellResult(
        String stdout,
        String stderr,
        int exitCode,
        long durationMs
) {
    /**
     * 判断Succes列表是否成立。
     * @return 领域处理结果。
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
