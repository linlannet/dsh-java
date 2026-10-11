package net.linlan.dsh.cases.config;

import java.util.Map;

/**
 * Configuration query use case for Web console and operational diagnostics.
 */
public interface IHarnessConfigCase {

    Map<String, Object> effectiveConfig();
}
