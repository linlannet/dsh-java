package net.linlan.dsh.infrastructure.adapter.sandbox.win32;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobObject Win32 进程组控制")
class JobObjectTest {

    @Test
    @DisplayName("非 Win32 平台 create 返回 false")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void createReturnsFalseOnNonWin32() {
        var job = new JobObject();
        assertFalse(job.create());
        assertFalse(job.isCreated());
    }

    @Test
    @DisplayName("非 Win32 平台 assign 返回 false")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void assignReturnsFalseOnNonWin32() {
        var job = new JobObject();
        ProcessHandle current = ProcessHandle.current();
        assertFalse(job.assign(current));
    }

    @Test
    @DisplayName("非 Win32 平台 terminateAll 返回 false")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void terminateAllReturnsFalseOnNonWin32() {
        var job = new JobObject();
        assertFalse(job.terminateAll());
    }

    @Test
    @DisplayName("非 Win32 平台 isActive 返回 false")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void isActiveReturnsFalseOnNonWin32() {
        var job = new JobObject();
        assertFalse(job.isActive());
    }

    @Test
    @DisplayName("isPlatformSupported 在 macOS 返回 false")
    @EnabledOnOs(OS.MAC)
    void isPlatformSupportedReturnsFalseOnMac() {
        var job = new JobObject();
        assertFalse(job.isPlatformSupported());
    }

    @Test
    @DisplayName("isPlatformSupported 在 Linux 返回 false")
    @EnabledOnOs(OS.LINUX)
    void isPlatformSupportedReturnsFalseOnLinux() {
        var job = new JobObject();
        assertFalse(job.isPlatformSupported());
    }

    @Test
    @DisplayName("assignedCount 初始为 0")
    void assignedCountInitiallyZero() {
        var job = new JobObject();
        assertEquals(0, job.assignedCount());
    }

    @Test
    @DisplayName("isCreated 初始为 false")
    void isCreatedInitiallyFalse() {
        var job = new JobObject();
        assertFalse(job.isCreated());
    }

    @Test
    @DisplayName("assign null 返回 false")
    void assignNullReturnsFalse() {
        var job = new JobObject();
        assertFalse(job.assign(null));
    }
}
