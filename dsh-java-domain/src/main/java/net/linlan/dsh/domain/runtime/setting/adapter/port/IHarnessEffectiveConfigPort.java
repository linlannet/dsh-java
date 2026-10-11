package net.linlan.dsh.domain.runtime.setting.adapter.port;

import java.util.Map;

/**
 * Port for reading the effective, deployment-facing harness configuration.
 */
public interface IHarnessEffectiveConfigPort {

    Map<String, Object> effectiveConfig();
}
