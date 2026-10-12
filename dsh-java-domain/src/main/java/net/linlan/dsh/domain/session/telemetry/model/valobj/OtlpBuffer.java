package net.linlan.dsh.domain.session.telemetry.model.valobj;

import java.util.ArrayDeque;
import java.util.Deque;

public final class OtlpBuffer {

    private final long byteLimit;
    private final Deque<byte[]> queue = new ArrayDeque<>();
    private long currentBytes = 0;
    private long droppedCount = 0;

    public OtlpBuffer() {
        this(4L * 1024 * 1024);
    }

    public OtlpBuffer(long byteLimit) {
        if (byteLimit <= 0) {
            throw new IllegalArgumentException("byteLimit must be positive");
        }
        this.byteLimit = byteLimit;
    }

    public synchronized boolean offer(byte[] event) {
        if (event.length > byteLimit) {
            droppedCount++;
            return false;
        }
        while (currentBytes + event.length > byteLimit && !queue.isEmpty()) {
            byte[] oldest = queue.pollFirst();
            currentBytes -= oldest.length;
            droppedCount++;
        }
        queue.addLast(event);
        currentBytes += event.length;
        return true;
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized long currentBytes() {
        return currentBytes;
    }

    public synchronized long droppedCount() {
        return droppedCount;
    }

    public long byteLimit() {
        return byteLimit;
    }
}
