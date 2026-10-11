package net.linlan.dsh.trigger.service.stream;

import java.util.function.Consumer;

final class DeltaBatch {
    private static final long FLUSH_INTERVAL_MS = 60L;
    private final Consumer<String> sender;
    private final StringBuilder buffer = new StringBuilder();
    private long lastFlushAt = System.currentTimeMillis();

    DeltaBatch(Consumer<String> sender) {
        this.sender = sender;
    }

    synchronized void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        this.buffer.append(delta);
        long now = System.currentTimeMillis();
        if (now - this.lastFlushAt >= 60L) {
            this.flush(now);
        }
    }

    synchronized void flush() {
        this.flush(System.currentTimeMillis());
    }

    private void flush(long now) {
        if (this.buffer.length() == 0) {
            this.lastFlushAt = now;
            return;
        }
        String content = this.buffer.toString();
        this.buffer.setLength(0);
        this.lastFlushAt = now;
        this.sender.accept(content);
    }
}

