package net.linlan.dsh.domain.subprocess.adapter.port;

import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;

/**
 * ISubprocessExecutorPort 子进程执行器出站端口。
 *
 */
public interface ISubprocessExecutorPort {

    SubprocessResult execute(SubprocessSpec spec);

    SubprocessResult collect(SubprocessSpec spec, long timeoutMs);
}
