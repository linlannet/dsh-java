package net.linlan.dsh.infrastructure.adapter.sandbox.nativesys;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class FlockPrimitive {

    private final NativeSystemPrimitive system;

    public FlockPrimitive() {
        this(new NativeSystemPrimitive());
    }

    public FlockPrimitive(NativeSystemPrimitive system) {
        this.system = system == null ? new NativeSystemPrimitive() : system;
    }

    public boolean isAvailable() {
        return system.supportsFlock();
    }

    public FlockHandle tryLock(Path path, boolean shared) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!system.supportsFlock()) {
            throw new UnsupportedOperationException("flock unsupported on " + system.osName());
        }
        FileChannel channel = shared
                ? FileChannel.open(path, StandardOpenOption.READ)
                : FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        FileLock lock;
        try {
            lock = channel.tryLock(0L, Long.MAX_VALUE, shared);
        } catch (OverlappingFileLockException e) {
            channel.close();
            return new FlockHandle(null, null, false);
        }
        if (lock == null) {
            channel.close();
            return new FlockHandle(null, null, false);
        }
        return new FlockHandle(channel, lock, true);
    }

    public FlockHandle lock(Path path, boolean shared) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!system.supportsFlock()) {
            throw new UnsupportedOperationException("flock unsupported on " + system.osName());
        }
        FileChannel channel = shared
                ? FileChannel.open(path, StandardOpenOption.READ)
                : FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        FileLock lock = channel.lock(0L, Long.MAX_VALUE, shared);
        return new FlockHandle(channel, lock, true);
    }

    public void release(FlockHandle handle) throws IOException {
        Objects.requireNonNull(handle, "handle");
        if (!handle.acquired()) {
            return;
        }
        FileLock lock = handle.lock();
        FileChannel channel = handle.channel();
        if (lock != null && lock.isValid()) {
            lock.release();
        }
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public record FlockHandle(FileChannel channel, FileLock lock, boolean acquired) {}
}
