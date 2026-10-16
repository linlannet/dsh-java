package net.linlan.dsh.domain.typert.ptc.python;

import net.linlan.dsh.domain.typert.ptc.python.model.valobj.ResourceBudget;
import java.util.Objects;

public class PtcSandboxPythonProcess {

    private final ResourceBudget budget;

    public PtcSandboxPythonProcess() {
        this(ResourceBudget.defaults());
    }

    public PtcSandboxPythonProcess(ResourceBudget budget) {
        this.budget = Objects.requireNonNull(budget);
    }

    public boolean platformSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("linux");
    }

    public PtcResult execute(String pythonCode) {
        Objects.requireNonNull(pythonCode, "pythonCode 不能为空");
        if (!platformSupported()) {
            return PtcResult.error("平台不支持: 仅 macOS/Linux");
        }
        return PtcResult.error("CPython 运行时未配置");
    }

    public record PtcResult(String stdout, String stderr, int exitCode) {
        public static PtcResult error(String message) { return new PtcResult("", message, -1); }
        public static PtcResult success(String stdout) { return new PtcResult(stdout, "", 0); }
    }
}
