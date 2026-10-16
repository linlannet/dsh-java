package net.linlan.dsh.domain.subprocess.model.valobj;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SubprocessSpec 表示子进程启动规格值对象。
 *
 * <p>显式指定参数不添加 shell 解释或隐藏执行默认值。</p>
 *
 */
public record SubprocessSpec(
        List<String> argv,
        String cwd,
        String stdio,
        Map<String, String> envOverride,
        long terminateGrace,
        String cancelSignal
) {
    public SubprocessSpec {
        Objects.requireNonNull(argv, "argv 不能为空");
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("argv 不能为空列表");
        }
        stdio = stdio == null ? "pipe" : stdio;
        envOverride = envOverride == null ? Map.of() : Map.copyOf(envOverride);
        cwd = cwd == null ? "." : cwd;
        terminateGrace = terminateGrace <= 0 ? 5000 : terminateGrace;
        cancelSignal = cancelSignal == null ? "SIGTERM" : cancelSignal;
    }

    public static SubprocessSpec of(List<String> argv) {
        return new SubprocessSpec(argv, null, null, null, 0, null);
    }
}
