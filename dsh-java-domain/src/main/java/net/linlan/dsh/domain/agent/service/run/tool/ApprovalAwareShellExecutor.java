package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;
import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.shell.ShellResult;
import java.util.Objects;
import java.util.function.Supplier;

public final class ApprovalAwareShellExecutor
implements IShellExecutor {
    private final IShellExecutor sandboxedExecutor;
    private final IShellExecutor unrestrictedExecutor;
    private final Supplier<AgentOptions> optionsSupplier;

    public ApprovalAwareShellExecutor(IShellExecutor sandboxedExecutor, IShellExecutor unrestrictedExecutor, Supplier<AgentOptions> optionsSupplier) {
        this.sandboxedExecutor = Objects.requireNonNull(sandboxedExecutor);
        this.unrestrictedExecutor = Objects.requireNonNull(unrestrictedExecutor);
        this.optionsSupplier = Objects.requireNonNull(optionsSupplier);
    }

    @Override
    public ShellResult execute(String command, String cwd, long timeoutMs) {
        if (this.isFullOpen()) {
            return this.unrestrictedExecutor.execute(command, cwd, timeoutMs);
        }
        return this.sandboxedExecutor.execute(command, cwd, timeoutMs);
    }

    private boolean isFullOpen() {
        AgentOptions options = this.optionsSupplier.get();
        return options != null && options.approvalMode() == ApprovalModeVO.FULL_OPEN;
    }
}

