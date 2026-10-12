package net.linlan.dsh.domain.storage.deliverable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DeliverableTool {

    public record Deliverable(String name, String path, String description, Instant declaredAt) {
        public Deliverable {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(declaredAt, "declaredAt");
        }
    }

    private final List<Deliverable> deliverables = Collections.synchronizedList(new ArrayList<>());

    public Deliverable declareDeliverable(String name, String path, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path required");
        }
        Deliverable d = new Deliverable(name, path, description, Instant.now());
        deliverables.add(d);
        return d;
    }

    public List<Deliverable> listDeliverables() {
        return new ArrayList<>(deliverables);
    }

    public Deliverable findByName(String name) {
        if (name == null) {
            return null;
        }
        return deliverables.stream()
                .filter(d -> name.equals(d.name()))
                .findFirst()
                .orElse(null);
    }

    public int count() {
        return deliverables.size();
    }

    public boolean remove(String name) {
        if (name == null) {
            return false;
        }
        return deliverables.removeIf(d -> name.equals(d.name()));
    }
}
