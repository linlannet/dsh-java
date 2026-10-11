package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CwdResolvingFsPort
implements IFsPort {
    private final IFsPort delegate;
    private final AtomicReference<String> cwd;

    public CwdResolvingFsPort(IFsPort delegate, String cwd) {
        this(delegate, new AtomicReference<String>(cwd));
    }

    public CwdResolvingFsPort(IFsPort delegate, AtomicReference<String> cwd) {
        this.delegate = delegate;
        this.cwd = cwd != null ? cwd : new AtomicReference<String>(null);
    }

    public void updateCwd(String newCwd) {
        if (newCwd != null && !newCwd.isBlank()) {
            this.cwd.set(newCwd);
        }
    }

    public String currentCwd() {
        return this.cwd.get();
    }

    private String resolve(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        Path p = Paths.get(path, new String[0]);
        if (p.isAbsolute()) {
            return path;
        }
        String base = this.cwd.get();
        if (base == null || base.isBlank()) {
            return path;
        }
        return Paths.get(base, new String[0]).toAbsolutePath().normalize().resolve(p).normalize().toString();
    }

    @Override
    public String read(String path, int offset, int limit) {
        return this.delegate.read(this.resolve(path), offset, limit);
    }

    @Override
    public void write(String path, String content) {
        this.delegate.write(this.resolve(path), content);
    }

    @Override
    public List<String> search(String directory, String pattern, int maxResults) {
        return this.delegate.search(this.resolve(directory), pattern, maxResults);
    }

    @Override
    public boolean exists(String path) {
        return this.delegate.exists(this.resolve(path));
    }

    @Override
    public List<IFsPort.DirEntry> listDir(String directory) {
        return this.delegate.listDir(this.resolve(directory));
    }

    @Override
    public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
        return this.delegate.grep(this.resolve(directory), pattern, include, isRegex, maxResults);
    }

    @Override
    public byte[] readBytes(String path) {
        return this.delegate.readBytes(this.resolve(path));
    }
}

