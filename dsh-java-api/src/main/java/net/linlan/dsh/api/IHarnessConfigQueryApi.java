package net.linlan.dsh.api;

import net.linlan.dsh.api.response.Response;
import java.util.Map;

/**
 * API for reading the effective harness configuration.
 */
public interface IHarnessConfigQueryApi {

    Response<Map<String, Object>> effectiveConfig();
}
