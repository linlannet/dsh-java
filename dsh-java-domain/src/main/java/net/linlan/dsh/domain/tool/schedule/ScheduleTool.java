package net.linlan.dsh.domain.tool.schedule;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

public final class ScheduleTool {

    private final Map<String, ScheduleEntry> store = new ConcurrentHashMap<>();

    public String createSchedule(String description, String cron) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(cron, "cron");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (cron.isBlank()) {
            throw new IllegalArgumentException("cron must not be blank");
        }
        String id = UUID.randomUUID().toString();
        store.put(id, new ScheduleEntry(id, description, cron, true, Instant.now()));
        return "schedule created: id=" + id + ", description=" + description + ", cron=" + cron;
    }

    public String modifySchedule(String id, String description) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        ScheduleEntry existing = store.get(id);
        if (existing == null) {
            return "schedule not found: id=" + id;
        }
        store.put(id, new ScheduleEntry(id, description, existing.cron(), existing.active(), existing.createdAt()));
        return "schedule modified: id=" + id + ", description=" + description;
    }

    public String cancelSchedule(String id) {
        Objects.requireNonNull(id, "id");
        ScheduleEntry existing = store.get(id);
        if (existing == null) {
            return "schedule not found: id=" + id;
        }
        store.put(id, new ScheduleEntry(id, existing.description(), existing.cron(), false, existing.createdAt()));
        return "schedule cancelled: id=" + id;
    }

    public ScheduleEntry getSchedule(String id) {
        Objects.requireNonNull(id, "id");
        return store.get(id);
    }

    public int size() {
        return store.size();
    }

    public static final class ScheduleEntry {
        private final String id;
        private final String description;
        private final String cron;
        private final boolean active;
        private final Instant createdAt;

        public ScheduleEntry(String id, String description, String cron, boolean active, Instant createdAt) {
            this.id = id;
            this.description = description;
            this.cron = cron;
            this.active = active;
            this.createdAt = createdAt;
        }

        public String id() { return id; }
        public String description() { return description; }
        public String cron() { return cron; }
        public boolean active() { return active; }
        public Instant createdAt() { return createdAt; }
    }
}
