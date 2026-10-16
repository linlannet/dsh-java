package net.linlan.dsh.domain.typert.ptc.python;

import net.linlan.dsh.domain.typert.ptc.python.model.valobj.ResourceBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PtcSandboxPythonProcess PTC Python 沙箱")
class PtcSandboxPythonProcessTest {
    @Test
    @DisplayName("platformSupported 在 macOS/Linux 返回 true")
    void platformSupported() {
        var process = new PtcSandboxPythonProcess();
        String os = System.getProperty("os.name", "").toLowerCase();
        assertEquals(os.contains("mac") || os.contains("linux"), process.platformSupported());
    }

    @Test
    @DisplayName("ResourceBudget 默认值")
    void resourceBudgetDefaults() {
        var budget = ResourceBudget.defaults();
        assertTrue(budget.cpuLimit() > 0);
        assertTrue(budget.memoryLimitMb() > 0);
        assertTrue(budget.timeLimitMs() > 0);
    }

    @Test
    @DisplayName("execute 在支持平台返回运行时未配置错误")
    void executeRuntimeNotConfigured() {
        var process = new PtcSandboxPythonProcess();
        var result = process.execute("print('hello')");
        if (process.platformSupported()) {
            assertEquals(-1, result.exitCode());
        }
    }
}
