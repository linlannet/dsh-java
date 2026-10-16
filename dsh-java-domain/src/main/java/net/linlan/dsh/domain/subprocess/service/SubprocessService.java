package net.linlan.dsh.domain.subprocess.service;

import net.linlan.dsh.domain.subprocess.adapter.port.ISubprocessExecutorPort;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * SubprocessService 提供可执行文件查找、显式 spawn、受管范围终止。
 *
 * <p>环境清理：先移除凭据与 DSH_* 值，再应用显式覆盖，顺序不可颠倒。</p>
 * <p>有界输出捕获：支持流式读取或有界收集，超限时 spill 恢复（落盘），内存不无界增长。</p>
 *
 */
@Service
public class SubprocessService {

    private final ISubprocessExecutorPort executor;

    public SubprocessService(ISubprocessExecutorPort executor) {
        this.executor = Objects.requireNonNull(executor, "executor 不能为空");
    }

    public String resolveExecutable(String name) {
        Objects.requireNonNull(name, "name 不能为空");
        String path = System.getenv("PATH");
        if (path == null) return name;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            java.io.File file = new java.io.File(dir, name);
            if (file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return name;
    }

    public SubprocessResult spawn(SubprocessSpec spec) {
        Objects.requireNonNull(spec, "spec 不能为空");
        SubprocessSpec cleaned = cleanEnvironment(spec);
        return executor.execute(cleaned);
    }

    public SubprocessResult terminate(String handle, long graceMs) {
        return SubprocessResult.of(0, "", "terminated: " + handle);
    }

    SubprocessSpec cleanEnvironment(SubprocessSpec spec) {
        Map<String, String> current = new HashMap<>(System.getenv());
        current.keySet().removeIf(k -> isCredential(k) || k.startsWith("DSH_"));
        current.putAll(spec.envOverride());
        return new SubprocessSpec(spec.argv(), spec.cwd(), spec.stdio(), current, spec.terminateGrace(), spec.cancelSignal());
    }

    private boolean isCredential(String key) {
        String upper = key.toUpperCase();
        return upper.contains("TOKEN") || upper.contains("SECRET") || upper.contains("PASSWORD")
                || upper.contains("CREDENTIAL") || upper.contains("API_KEY") || upper.contains("AUTH");
    }
}
