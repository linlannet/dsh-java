package net.linlan.dsh.domain.sandbox.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class SandboxExtraRootsRegistry {
    private static final int MAX_ROOTS = 50;
    private final AtomicReference<List<Path>> roots = new AtomicReference(List.of());

    public void update(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        List<Path> current = this.roots.get();
        ArrayList<Path> merged = new ArrayList<Path>(current);
        for (String p : paths) {
            if (p == null || p.isBlank()) continue;
            try {
                Path normalized = Path.of(p, new String[0]).toAbsolutePath().normalize();
                if (merged.contains(normalized)) continue;
                merged.add(normalized);
            }
            catch (Exception exception) {}
        }
        if (merged.size() > 50) {
            merged = new ArrayList(merged.subList(merged.size() - 50, merged.size()));
        }
        this.roots.set(List.copyOf(merged));
    }

    public void clear() {
        this.roots.set(List.of());
    }

    public List<Path> current() {
        return this.roots.get();
    }
}

