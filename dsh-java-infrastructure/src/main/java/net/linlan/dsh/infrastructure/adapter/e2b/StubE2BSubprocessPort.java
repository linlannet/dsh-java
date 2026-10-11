package net.linlan.dsh.infrastructure.adapter.e2b;

import net.linlan.dsh.domain.e2b.adapter.port.IE2BSubprocessPort;
import net.linlan.dsh.domain.e2b.model.valobj.E2BExecResult;
import org.springframework.stereotype.Service;

/** Stub E2B subprocess port for local mode (no real E2B sandbox). */
@Service
public class StubE2BSubprocessPort implements IE2BSubprocessPort {
    @Override public E2BExecResult run(String sandboxId, String command) {
        return new E2BExecResult("", "", 0, 0);
    }
    @Override public E2BExecResult runWithInput(String sandboxId, String command, String stdin) {
        return new E2BExecResult("", "", 0, 0);
    }
}
