package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.QueryPluginStatusResponseDTO;
import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;
import net.linlan.dsh.api.dto.PluginInventoryResponseDTO;
import net.linlan.dsh.api.IHarnessPluginQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端插件查询请求；
 * 2. 调用插件查询适配器读取安装态或运行态信息；
 * 3. 直接返回 JSON 响应体。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/plugins")
public class HarnessPluginQueryController {

    private final IHarnessPluginQueryApi harnessPluginQueryApi;

    /**
     * 处理Harness 插件 Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessPluginQueryApi harness 插件 Query API参数。
     * @return 方法执行结果。
     */
    public HarnessPluginQueryController(IHarnessPluginQueryApi harnessPluginQueryApi) {
        this.harnessPluginQueryApi = harnessPluginQueryApi;
    }

    /**
     * 查询已安装插件列表。
     * <p>
     * 执行流程：
     * 1. 调用查询适配器读取插件列表；
     * 2. 将结果保持原样返回；
     * 3. 供前端插件中心直接渲染。
     */
    @GetMapping
    public Response<List<InstalledPluginResponseDTO>> listInstalledPlugins() {
        return harnessPluginQueryApi.listInstalledPlugins();
    }

    @GetMapping("/inventory")
    public Response<PluginInventoryResponseDTO> listPluginInventory() {
        return harnessPluginQueryApi.listPluginInventory();
    }

    /**
     * 查询单个插件运行状态。
     * <p>
     * 执行流程：
     * 1. 从路径参数中读取插件标识；
     * 2. 调用查询适配器获取运行状态；
     * 3. 返回插件状态响应。
     */
    @GetMapping("/{pluginId}/status")
    public Response<QueryPluginStatusResponseDTO> queryPluginStatus(@PathVariable("pluginId") String pluginId) {
        return harnessPluginQueryApi.queryPluginStatus(pluginId);
    }
}
