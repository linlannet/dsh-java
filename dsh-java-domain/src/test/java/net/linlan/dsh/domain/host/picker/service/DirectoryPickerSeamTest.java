package net.linlan.dsh.domain.host.picker.service;

import net.linlan.dsh.domain.host.picker.adapter.port.IDirectoryPickerBackendPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectoryPickerSeam 目录选择")
class DirectoryPickerSeamTest {

    private static class StubBackend implements IDirectoryPickerBackendPort {
        final boolean supported;
        final Optional<String> result;

        StubBackend(boolean supported, Optional<String> result) {
            this.supported = supported;
            this.result = result;
        }

        @Override
        public boolean supports() { return supported; }

        @Override
        public Optional<String> pick(String title, String initialPath) { return result; }
    }

    @Test
    @DisplayName("支持的后端返回选择结果")
    void supportedBackendReturnsResult() {
        var seam = new DirectoryPickerSeam(List.of(
                new StubBackend(true, Optional.of("/selected/path"))
        ));
        Optional<String> result = seam.pick(PickerMode.NATIVE, "选择目录", "/home");
        assertTrue(result.isPresent());
        assertEquals("/selected/path", result.get());
    }

    @Test
    @DisplayName("无匹配后端返回空")
    void noMatchingBackendReturnsEmpty() {
        var seam = new DirectoryPickerSeam(List.of(
                new StubBackend(false, Optional.empty())
        ));
        Optional<String> result = seam.pick(PickerMode.NATIVE, "选择目录", "/home");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("多个后端时使用第一个支持的")
    void multipleBackendsFirstSupported() {
        var seam = new DirectoryPickerSeam(List.of(
                new StubBackend(false, Optional.empty()),
                new StubBackend(true, Optional.of("/from/second"))
        ));
        Optional<String> result = seam.pick(PickerMode.AUTO, "选择", "/");
        assertEquals("/from/second", result.get());
    }
}
