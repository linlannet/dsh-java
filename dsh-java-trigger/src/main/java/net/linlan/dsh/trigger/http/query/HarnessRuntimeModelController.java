package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.api.IHarnessRuntimeModelQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时模型查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端的运行时模型查询请求；
 * 2. 调用查询适配器读取当前可用模型；
 * 3. 返回可直接用于模型选择器的列表。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/runtime/models")
public class HarnessRuntimeModelController {

    private final IHarnessRuntimeModelQueryApi harnessRuntimeModelQueryApi;

    /**
     * 处理Harness 运行时 模型 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessRuntimeModelQueryApi harness 运行时 模型 Query API参数。
     * @return 方法执行结果。
     */
    public HarnessRuntimeModelController(IHarnessRuntimeModelQueryApi harnessRuntimeModelQueryApi) {
        this.harnessRuntimeModelQueryApi = harnessRuntimeModelQueryApi;
    }

    /**
     * 查询当前可用的运行时模型。
     * <p>
     * 执行流程：
     * 1. 调用查询适配器读取模型列表；
     * 2. 过滤结果由领域层或适配器完成；
     * 3. 返回给前端渲染。
     */
    @GetMapping
    public Response<List<AvailableModelResponseDTO>> queryAll() {
        return harnessRuntimeModelQueryApi.queryAll();
    }
}
