package net.linlan.dsh.domain.subprocess.model.valobj;

import java.util.Objects;

/**
 * SubprocessResult 表示子进程执行结果值对象。
 *
 * <p>输出在进程退出后仍可读取。</p>
 *
 */
public record SubprocessResult(
        String stdout,
        String stderr,
        int exitCode,
        boolean spilled
) {
    public SubprocessResult {
        stdout = Objects.requireNonNullElse(stdout, "");
        stderr = Objects.requireNonNullElse(stderr, "");
    }

    public static SubprocessResult of(int exitCode, String stdout, String stderr) {
        return new SubprocessResult(stdout, stderr, exitCode, false);
    }

    public static SubprocessResult spilled(int exitCode, String stderr) {
        return new SubprocessResult("", stderr, exitCode, true);
    }

    public boolean success() {
        return exitCode == 0;
    }
}
