package net.linlan.dsh.domain.guard.timeout;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class PerToolDeadlinePolicy {

    private final Map<String, Duration> perToolDeadlines;
    private final Duration defaultDeadline;

    public PerToolDeadlinePolicy(Map<String, Duration> perToolDeadlines, Duration defaultDeadline) {
        this.perToolDeadlines = Map.copyOf(Objects.requireNonNull(perToolDeadlines, "perToolDeadlines"));
        this.defaultDeadline = Objects.requireNonNull(defaultDeadline, "defaultDeadline");
    }

    public PerToolDeadlinePolicy(Map<String, Duration> perToolDeadlines) {
        this(perToolDeadlines, Duration.ofSeconds(30));
    }

    public Duration deadline(String toolName) {
        Objects.requireNonNull(toolName, "toolName");
        return perToolDeadlines.getOrDefault(toolName, defaultDeadline);
    }

    public Duration deadlineOrDefault(String toolName, Duration defaultDuration) {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(defaultDuration, "defaultDuration");
        return perToolDeadlines.getOrDefault(toolName, defaultDuration);
    }

    public Duration defaultDeadline() {
        return defaultDeadline;
    }

    public boolean hasToolDeadline(String toolName) {
        return perToolDeadlines.containsKey(toolName);
    }
}
