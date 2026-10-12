package net.linlan.dsh.infrastructure.adapter.sandbox.nativesys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NativeSystemPrimitive 原生系统能力")
class NativeSystemPrimitiveTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("默认构造识别当前操作系统")
    void defaultConstructorDetectsOs() {
        var primitive = new NativeSystemPrimitive();
        String os = System.getProperty("os.name", "");
        assertNotNull(primitive.osName());
        assertEquals(os, primitive.osName());
        assertTrue(primitive.isLinux() || primitive.isMac() || primitive.isWindows()
                || (!primitive.isLinux() && !primitive.isMac() && !primitive.isWindows()));
    }

    @Test
    @DisplayName("显式传入 os.name 进行平台判定")
    void explicitOsNameClassification() {
        var linux = new NativeSystemPrimitive("Linux");
        assertTrue(linux.isLinux());
        assertFalse(linux.isMac());
        assertFalse(linux.isWindows());

        var mac = new NativeSystemPrimitive("Mac OS X");
        assertFalse(mac.isLinux());
        assertTrue(mac.isMac());
        assertFalse(mac.isWindows());

        var win = new NativeSystemPrimitive("Windows 11");
        assertFalse(win.isLinux());
        assertFalse(win.isMac());
        assertTrue(win.isWindows());
    }

    @Test
    @DisplayName("currentPid 返回正值")
    void currentPidPositive() {
        var primitive = new NativeSystemPrimitive();
        long pid = primitive.currentPid();
        assertTrue(pid > 0, "pid 应为正值");
    }

    @Test
    @DisplayName("supportsLandlock 仅在 Linux 为真")
    void supportsLandlockOnlyLinux() {
        assertTrue(new NativeSystemPrimitive("Linux").supportsLandlock());
        assertFalse(new NativeSystemPrimitive("Mac OS X").supportsLandlock());
        assertFalse(new NativeSystemPrimitive("Windows").supportsLandlock());
    }

    @Test
    @DisplayName("supportsFlock 在 Linux 与 Mac 为真")
    void supportsFlockOnLinuxAndMac() {
        assertTrue(new NativeSystemPrimitive("Linux").supportsFlock());
        assertTrue(new NativeSystemPrimitive("Mac OS X").supportsFlock());
        assertFalse(new NativeSystemPrimitive("Windows").supportsFlock());
    }

    @Test
    @DisplayName("LandlockPrimitive 在非 Linux 优雅降级")
    void landlockDegradesOnNonLinux() {
        var mac = new LandlockPrimitive(new NativeSystemPrimitive("Mac OS X"));
        assertFalse(mac.isAvailable());
        var result = mac.restrictTo("/tmp", false);
        assertFalse(result.success());
        assertTrue(result.message().contains("unsupported"));
    }

    @Test
    @DisplayName("LandlockPrimitive 在 Linux 可限制路径")
    void landlockRestrictOnLinux() {
        var linux = new LandlockPrimitive(new NativeSystemPrimitive("Linux"));
        assertTrue(linux.isAvailable());
        var result = linux.restrictTo("/tmp/work", false);
        assertTrue(result.success());
        assertEquals("/tmp/work", result.handle());
    }

    @Test
    @DisplayName("LandlockPrimitive 路径为空返回失败")
    void landlockEmptyPathFails() {
        var linux = new LandlockPrimitive(new NativeSystemPrimitive("Linux"));
        var result = linux.restrictTo("", false);
        assertFalse(result.success());
        assertEquals("path required", result.message());
    }

    @Test
    @DisplayName("LandlockPrimitive release null handle 返回失败")
    void landlockReleaseNullHandle() {
        var primitive = new LandlockPrimitive(new NativeSystemPrimitive("Linux"));
        var result = primitive.release(null);
        assertFalse(result.success());
    }

    @Test
    @DisplayName("LandlockPrimitive release 有效 handle 返回成功")
    void landlockReleaseValidHandle() {
        var primitive = new LandlockPrimitive(new NativeSystemPrimitive("Linux"));
        var result = primitive.release(new Object());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("FlockPrimitive 在 Mac 可用")
    void flockAvailableOnMac() {
        var mac = new FlockPrimitive(new NativeSystemPrimitive("Mac OS X"));
        assertTrue(mac.isAvailable());
    }

    @Test
    @DisplayName("FlockPrimitive 在 Windows 不可用")
    void flockUnavailableOnWindows() {
        var win = new FlockPrimitive(new NativeSystemPrimitive("Windows"));
        assertFalse(win.isAvailable());
    }

    @Test
    @DisplayName("FlockPrimitive 独占锁可获取与释放")
    void flockExclusiveLockAndRelease() throws Exception {
        var primitive = new FlockPrimitive(new NativeSystemPrimitive("Mac OS X"));
        Path file = tempDir.resolve("lock.txt");
        var handle = primitive.lock(file, false);
        assertTrue(handle.acquired());
        assertNotNull(handle.lock());
        assertTrue(handle.lock().isValid());
        primitive.release(handle);
        assertFalse(handle.lock().isValid());
    }

    @Test
    @DisplayName("FlockPrimitive 共享锁可获取")
    void flockSharedLock() throws Exception {
        var primitive = new FlockPrimitive(new NativeSystemPrimitive("Mac OS X"));
        Path file = tempDir.resolve("shared.txt");
        Files.writeString(file, "data");
        var handle = primitive.lock(file, true);
        assertTrue(handle.acquired());
        primitive.release(handle);
    }

    @Test
    @DisplayName("FlockPrimitive tryLock 同文件第二次返回未获取")
    void flockTryLockContended() throws Exception {
        var primitive = new FlockPrimitive(new NativeSystemPrimitive("Mac OS X"));
        Path file = tempDir.resolve("contended.txt");
        var first = primitive.lock(file, false);
        assertTrue(first.acquired());
        var second = primitive.tryLock(file, false);
        assertFalse(second.acquired());
        primitive.release(first);
    }

    @Test
    @DisplayName("FlockPrimitive 在 Windows 抛出 UnsupportedOperationException")
    void flockWindowsThrows() {
        var win = new FlockPrimitive(new NativeSystemPrimitive("Windows"));
        assertThrows(UnsupportedOperationException.class,
                () -> win.lock(tempDir.resolve("a.txt"), false));
    }
}
