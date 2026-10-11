package net.linlan.dsh.cases.config;

import net.linlan.dsh.domain.runtime.setting.adapter.port.IHarnessEffectiveConfigPort;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reads the effective harness configuration for Trigger-facing diagnostics.
 */
@Service
public class HarnessConfigQueryCase implements IHarnessConfigCase {

    private final IHarnessEffectiveConfigPort effectiveConfigPort;

    public HarnessConfigQueryCase(IHarnessEffectiveConfigPort effectiveConfigPort) {
        this.effectiveConfigPort = effectiveConfigPort;
    }

    @Override
    public Map<String, Object> effectiveConfig() {
        return effectiveConfigPort.effectiveConfig();
    }
}
