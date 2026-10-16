package net.linlan.dsh.infrastructure.adapter.subprocess;

import net.linlan.dsh.domain.subprocess.adapter.port.ISubprocessExecutorPort;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RemoteSubprocessExecutor 远程子进程执行适配器。
 *
 * <p>根据命令运行位置选择本地或远程执行。</p>
 *
 */
@Component
public class RemoteSubprocessExecutor implements ISubprocessExecutorPort {

    private final LocalSubprocessExecutor localExecutor;
    private final boolean remoteEnabled;

    @Autowired
    public RemoteSubprocessExecutor(LocalSubprocessExecutor localExecutor) {
        this(localExecutor, false);
    }

    public RemoteSubprocessExecutor(LocalSubprocessExecutor localExecutor, boolean remoteEnabled) {
        this.localExecutor = localExecutor;
        this.remoteEnabled = remoteEnabled;
    }

    @Override
    public SubprocessResult execute(SubprocessSpec spec) {
        if (remoteEnabled && isRemotePath(spec.cwd())) {
            return SubprocessResult.of(-1, "", "远程执行未配置");
        }
        return localExecutor.execute(spec);
    }

    @Override
    public SubprocessResult collect(SubprocessSpec spec, long timeoutMs) {
        return localExecutor.collect(spec, timeoutMs);
    }

    private boolean isRemotePath(String cwd) {
        return cwd != null && (cwd.startsWith("ssh://") || cwd.startsWith("rsync://"));
    }
}
