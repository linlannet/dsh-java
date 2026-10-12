package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;

import java.util.List;
import java.util.Objects;

public final class GlobTool {

    private static final int DEFAULT_MAX = 100;

    private final IFsPort fsPort;

    public GlobTool(IFsPort fsPort) {
        this.fsPort = Objects.requireNonNull(fsPort, "fsPort");
    }

    public List<String> glob(String pattern, String cwd) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(cwd, "cwd");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (cwd.isBlank()) {
            throw new IllegalArgumentException("cwd must not be blank");
        }
        return fsPort.search(cwd, pattern, DEFAULT_MAX);
    }

    public List<String> glob(String pattern, String cwd, int maxResults) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(cwd, "cwd");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        return fsPort.search(cwd, pattern, maxResults);
    }
}
