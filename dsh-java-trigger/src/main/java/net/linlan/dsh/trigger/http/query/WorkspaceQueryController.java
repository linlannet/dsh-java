package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.WorkspaceEntryResponseDTO;
import net.linlan.dsh.api.IWorkspaceQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端工作区查询请求；
 * 2. 调用工作区查询适配器读取目录与排序信息；
 * 3. 返回给前端用于下拉选择和目录浏览。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/agent")
public class WorkspaceQueryController {

    private final IWorkspaceQueryApi workspaceQueryApi;

    /**
     * 处理工作区 Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param workspaceQueryApi 工作区 Query API参数。
     * @return 方法执行结果。
     */
    public WorkspaceQueryController(IWorkspaceQueryApi workspaceQueryApi) {
        this.workspaceQueryApi = workspaceQueryApi;
    }

    /**
     * 获取全部工作区。
     * <p>
     * 执行流程：
     * 1. 调用查询适配器读取工作区列表；
     * 2. 保持响应结构不变；
     * 3. 返回给前端工作区页面。
     */
    @GetMapping("/workspaces")
    public Response<List<WorkspaceEntryResponseDTO>> getWorkspaces() {
        return workspaceQueryApi.getWorkspaces();
    }

    /**
     * 列出指定路径下的可见目录。
     * <p>
     * 执行流程：
     * 1. 读取可选的 path 参数；
     * 2. 调用查询适配器扫描目录；
     * 3. 返回目录选择器可直接使用的字符串列表。
     */
    @GetMapping("/workspaces/list")
    public Response<List<String>> listDirectories(@RequestParam(value = "path", required = false) String path) {
        return workspaceQueryApi.listDirectories(path);
    }
}
