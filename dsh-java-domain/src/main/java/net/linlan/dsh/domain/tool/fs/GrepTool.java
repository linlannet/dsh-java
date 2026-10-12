package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;

import java.util.List;
import java.util.Objects;

public final class GrepTool {

    private static final int DEFAULT_MAX = 100;

    private final IFsPort fsPort;

    public GrepTool(IFsPort fsPort) {
        this.fsPort = Objects.requireNonNull(fsPort, "fsPort");
    }

    public List<String> grep(String pattern, String cwd, boolean isRegex) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(cwd, "cwd");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        return fsPort.grep(cwd, pattern, null, isRegex, DEFAULT_MAX);
    }

    public List<String> grep(String pattern, String cwd, boolean isRegex, int maxResults) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(cwd, "cwd");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        return fsPort.grep(cwd, pattern, null, isRegex, maxResults);
    }

    public List<String> grep(String pattern, String cwd, String include, boolean isRegex, int maxResults) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(cwd, "cwd");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        return fsPort.grep(cwd, pattern, include, isRegex, maxResults);
    }
}
