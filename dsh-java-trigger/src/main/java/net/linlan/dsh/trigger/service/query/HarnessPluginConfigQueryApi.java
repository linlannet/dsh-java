package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.plugin.config.IPluginConfigCase;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HarnessPluginConfigQueryApi {

    private final IPluginConfigCase pluginConfigCase;

    public HarnessPluginConfigQueryApi(IPluginConfigCase pluginConfigCase) {
        this.pluginConfigCase = pluginConfigCase;
    }

    public Response<List<Map<String, String>>> list(String pluginId) {
        try {
            List<Map<String, String>> items = pluginConfigCase.load(pluginId);
            return Response.success(items);
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
