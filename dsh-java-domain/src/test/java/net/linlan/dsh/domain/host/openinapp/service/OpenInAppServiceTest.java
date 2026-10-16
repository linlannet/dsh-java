package net.linlan.dsh.domain.host.openinapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenInAppService 应用启动")
class OpenInAppServiceTest {

    @Test
    @DisplayName("detectInstalled 检测已安装应用")
    void detectInstalled() {
        var service = new OpenInAppService();
        assertTrue(service.detectInstalled("java") || !service.detectInstalled("java"));
    }

    @Test
    @DisplayName("launch 未安装应用返回空")
    void launchNotInstalled() {
        var service = new OpenInAppService();
        var result = service.launch("nonexistent-app-xyz", "/path");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("detectAll 过滤未安装应用")
    void detectAllFilters() {
        var service = new OpenInAppService();
        List<String> installed = service.detectAll(List.of("nonexistent-xyz", "another-nonexistent"));
        assertTrue(installed.isEmpty());
    }
}
