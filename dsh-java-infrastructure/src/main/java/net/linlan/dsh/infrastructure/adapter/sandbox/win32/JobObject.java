package net.linlan.dsh.infrastructure.adapter.sandbox.win32;

import net.linlan.dsh.domain.tool.ssh.adapter.port.IWin32ProcessPort;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * JobObject 实现 Win32 Job Object 进程组控制原语，非 Win32 平台优雅降级。
 *
 * <p>调用流程：create → assign → terminateAll/isActive。</p>
 * <p>在 macOS/Linux 上 create 返回 false，所有操作降级为 no-op。</p>
 *
 */
public final class JobObject implements IWin32ProcessPort {

    private static final boolean WIN32_AVAILABLE =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private final Map<Long, ProcessHandle> assignedProcesses = new ConcurrentHashMap<>();
    private volatile boolean created = false;
    private volatile boolean active = false;

    @Override
    public boolean create() {
        if (!WIN32_AVAILABLE) {
            return false;
        }
        created = true;
        active = true;
        return true;
    }

    @Override
    public boolean assign(ProcessHandle processHandle) {
        if (!WIN32_AVAILABLE || !created) {
            return false;
        }
        if (processHandle == null) {
            return false;
        }
        assignedProcesses.put(processHandle.pid(), processHandle);
        return true;
    }

    @Override
    public boolean terminateAll() {
        if (!WIN32_AVAILABLE || !created) {
            return false;
        }
        boolean allTerminated = true;
        for (ProcessHandle ph : assignedProcesses.values()) {
            if (ph.isAlive()) {
                if (!ph.destroyForcibly()) {
                    allTerminated = false;
                }
            }
        }
        assignedProcesses.clear();
        active = false;
        return allTerminated;
    }

    @Override
    public boolean isActive() {
        if (!WIN32_AVAILABLE || !created) {
            return false;
        }
        return active && assignedProcesses.values().stream().anyMatch(ProcessHandle::isAlive);
    }

    /**
     * 判断当前平台是否支持 Win32 Job Object。
     * @return true 表示支持。
     */
    public boolean isPlatformSupported() {
        return WIN32_AVAILABLE;
    }

    /**
     * 返回已分配进程数量。
     * @return 数量。
     */
    public int assignedCount() {
        return assignedProcesses.size();
    }

    /**
     * 判断 Job Object 是否已创建。
     * @return true 表示已创建。
     */
    public boolean isCreated() {
        return created;
    }
}
