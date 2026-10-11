package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.service.query.HarnessPluginConfigQueryApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/harness/plugins/{pluginId}/config")
public class HarnessPluginConfigQueryController {

    private final HarnessPluginConfigQueryApi pluginConfigQueryApi;

    public HarnessPluginConfigQueryController(HarnessPluginConfigQueryApi pluginConfigQueryApi) {
        this.pluginConfigQueryApi = pluginConfigQueryApi;
    }

    @GetMapping
    public Response<List<Map<String, String>>> get(@PathVariable("pluginId") String pluginId) {
        return pluginConfigQueryApi.list(pluginId);
    }
}
