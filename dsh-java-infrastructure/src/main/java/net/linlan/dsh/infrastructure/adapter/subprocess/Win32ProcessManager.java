package net.linlan.dsh.infrastructure.adapter.subprocess;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Win32ProcessManager Win32 平台进程管理。
 *
 * <p>维护 sandbox 与普通进程创建、stdio、Job 分配、轮询、等待与句柄清理。
 * 在非 Win32 平台上退化为进程组管理。</p>
 *
 */
public class Win32ProcessManager {

    private final boolean win32;

    public Win32ProcessManager() {
        this.win32 = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public long currentPid() {
        return ProcessHandle.current().pid();
    }

    public List<Long> descendantPids(long pid) {
        List<Long> pids = new ArrayList<>();
        ProcessHandle.of(pid).ifPresent(handle -> {
            for (ProcessHandle child : handle.descendants().toList()) {
                pids.add(child.pid());
            }
        });
        return pids;
    }

    public void terminateProcessTree(long pid, long graceMs) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            for (ProcessHandle child : handle.descendants().toList()) {
                child.destroyForcibly();
            }
            handle.destroyForcibly();
        });
    }

    public boolean isWin32() {
        return win32;
    }
}
