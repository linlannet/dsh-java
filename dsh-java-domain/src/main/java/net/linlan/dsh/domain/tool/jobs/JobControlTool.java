package net.linlan.dsh.domain.tool.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JobControlTool {

    private final Map<String, JobEntry> store = new ConcurrentHashMap<>();

    public String createJob(String name, String cron, String command) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cron, "cron");
        Objects.requireNonNull(command, "command");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (cron.isBlank()) {
            throw new IllegalArgumentException("cron must not be blank");
        }
        if (command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        String id = UUID.randomUUID().toString();
        store.put(id, new JobEntry(id, name, cron, command, true, Instant.now()));
        return "job created: id=" + id + ", name=" + name + ", cron=" + cron + ", command=" + command;
    }

    public JobEntry getJob(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        return store.get(jobId);
    }

    public String cancelJob(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        JobEntry existing = store.get(jobId);
        if (existing == null) {
            return "job not found: id=" + jobId;
        }
        store.put(jobId, new JobEntry(existing.id(), existing.name(), existing.cron(), existing.command(), false, existing.createdAt()));
        return "job cancelled: id=" + jobId;
    }

    public List<JobEntry> listJobs() {
        return new ArrayList<>(store.values());
    }

    public int size() {
        return store.size();
    }

    public static final class JobEntry {
        private final String id;
        private final String name;
        private final String cron;
        private final String command;
        private final boolean active;
        private final Instant createdAt;

        public JobEntry(String id, String name, String cron, String command, boolean active, Instant createdAt) {
            this.id = id;
            this.name = name;
            this.cron = cron;
            this.command = command;
            this.active = active;
            this.createdAt = createdAt;
        }

        public String id() { return id; }
        public String name() { return name; }
        public String cron() { return cron; }
        public String command() { return command; }
        public boolean active() { return active; }
        public Instant createdAt() { return createdAt; }
    }
}
