package net.linlan.dsh.domain.sandbox.service;

import java.nio.file.Path;
import java.util.List;

/**
 * workspace-write 写入边界：以 Session 当前目录为根，并显式拒绝 .git。
 */
public final class WorkspaceWritePolicy {

    private final List<Path> roots;

    public WorkspaceWritePolicy(Path sessionCwd) {
        this(List.of(sessionCwd));
    }

    public WorkspaceWritePolicy(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("workspace roots must not be empty");
        }
        this.roots = roots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    public Path check(Path target) {
        Path resolved = target.toAbsolutePath().normalize();
        boolean allowed = roots.stream().anyMatch(resolved::startsWith);
        if (!allowed) {
            throw new SecurityException(
                    "[sandbox: file access denied under workspace-write mode] " + resolved);
        }
        if (containsGitSegment(resolved)) {
            throw new SecurityException("[sandbox: writes to .git are denied] " + resolved);
        }
        return resolved;
    }

    private static boolean containsGitSegment(Path path) {
        for (Path segment : path) {
            if (".git".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
