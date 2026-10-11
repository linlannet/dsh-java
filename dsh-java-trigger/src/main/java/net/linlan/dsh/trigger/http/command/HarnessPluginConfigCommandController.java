package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.service.command.HarnessPluginConfigCommandApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/harness/plugins/{pluginId}/config")
public class HarnessPluginConfigCommandController {

    private final HarnessPluginConfigCommandApi pluginConfigCommandApi;

    public HarnessPluginConfigCommandController(HarnessPluginConfigCommandApi pluginConfigCommandApi) {
        this.pluginConfigCommandApi = pluginConfigCommandApi;
    }

    public record PluginConfigItem(String key, String value) {
    }

    public record SavePluginConfigRequest(List<PluginConfigItem> configs) {
    }

    @PostMapping
    public Response<Void> save(
            @PathVariable("pluginId") String pluginId,
            @RequestBody SavePluginConfigRequest request
    ) {
        List<Map<String, String>> configs = request == null || request.configs() == null
                ? List.of()
                : request.configs().stream()
                        .map(item -> Map.of("key", item.key() == null ? "" : item.key(), "value", item.value() == null ? "" : item.value()))
                        .toList();
        return pluginConfigCommandApi.save(pluginId, configs);
    }
}
