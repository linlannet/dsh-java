package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.IHarnessConfigQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.config.IHarnessConfigCase;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Web-facing adapter for effective configuration queries.
 */
@Service
public class HarnessConfigQueryApi implements IHarnessConfigQueryApi {

    private final IHarnessConfigCase configCase;

    public HarnessConfigQueryApi(IHarnessConfigCase configCase) {
        this.configCase = configCase;
    }

    @Override
    public Response<Map<String, Object>> effectiveConfig() {
        try {
            return Response.success(configCase.effectiveConfig());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
