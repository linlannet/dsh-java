package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.IHarnessModelSettingQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型设置查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端模型设置查询请求；
 * 2. 调用查询适配器读取持久化模型配置；
 * 3. 直接返回模型设置列表。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/settings/models")
public class HarnessModelSettingQueryController {

    private final IHarnessModelSettingQueryApi harnessModelSettingQueryApi;

    /**
     * 处理Harness 模型 Setting Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessModelSettingQueryApi harness 模型 Setting Query API参数。
     * @return 方法执行结果。
     */
    public HarnessModelSettingQueryController(IHarnessModelSettingQueryApi harnessModelSettingQueryApi) {
        this.harnessModelSettingQueryApi = harnessModelSettingQueryApi;
    }

    /**
     * 查询全部模型设置。
     * <p>
     * 执行流程：
     * 1. 调用查询适配器读取全部设置；
     * 2. 组装响应对象；
     * 3. 返回给前端配置页。
     */
    @GetMapping
    public Response<List<ModelSettingResponseDTO>> queryAll() {
        return harnessModelSettingQueryApi.queryAll();
    }
}
