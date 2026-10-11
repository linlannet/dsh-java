package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ApprovalAwareFsPort
implements IFsPort {
    private final IFsPort sandboxedPort;
    private final IFsPort unrestrictedPort;
    private final Supplier<AgentOptions> optionsSupplier;

    public ApprovalAwareFsPort(IFsPort sandboxedPort, IFsPort unrestrictedPort, Supplier<AgentOptions> optionsSupplier) {
        this.sandboxedPort = Objects.requireNonNull(sandboxedPort);
        this.unrestrictedPort = Objects.requireNonNull(unrestrictedPort);
        this.optionsSupplier = Objects.requireNonNull(optionsSupplier);
    }

    @Override
    public String read(String path, int offset, int limit) {
        return this.sandboxedPort.read(path, offset, limit);
    }

    @Override
    public void write(String path, String content) {
        if (this.isFullOpen()) {
            this.unrestrictedPort.write(path, content);
            return;
        }
        this.sandboxedPort.write(path, content);
    }

    @Override
    public List<String> search(String directory, String pattern, int maxResults) {
        return this.sandboxedPort.search(directory, pattern, maxResults);
    }

    @Override
    public boolean exists(String path) {
        return this.sandboxedPort.exists(path);
    }

    @Override
    public List<IFsPort.DirEntry> listDir(String directory) {
        return this.sandboxedPort.listDir(directory);
    }

    @Override
    public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
        return this.sandboxedPort.grep(directory, pattern, include, isRegex, maxResults);
    }

    @Override
    public byte[] readBytes(String path) {
        return this.sandboxedPort.readBytes(path);
    }

    private boolean isFullOpen() {
        AgentOptions options = this.optionsSupplier.get();
        return options != null && options.approvalMode() == ApprovalModeVO.FULL_OPEN;
    }
}

