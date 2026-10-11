package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.api.IHarnessModelDiscoveryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向 Web 控制台代理上游模型发现。 */
@RestController
@RequestMapping("/api/harness/settings/models/discover")
public class HarnessModelDiscoveryController {

    private final IHarnessModelDiscoveryApi harnessModelDiscoveryApi;

    /**
     * 处理Harness 模型 Discovery Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessModelDiscoveryApi harness 模型 Discovery API参数。
     * @return 方法执行结果。
     */
    public HarnessModelDiscoveryController(IHarnessModelDiscoveryApi harnessModelDiscoveryApi) {
        this.harnessModelDiscoveryApi = harnessModelDiscoveryApi;
    }

    @PostMapping
    public Response<List<String>> discover(@RequestBody DiscoverModelsRequestDTO request) {
        return harnessModelDiscoveryApi.discover(request);
    }
}
