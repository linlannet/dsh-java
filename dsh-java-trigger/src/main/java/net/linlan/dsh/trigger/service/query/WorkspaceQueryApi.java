package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.WorkspaceEntryResponseDTO;
import net.linlan.dsh.api.IWorkspaceQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工作区查询适配器。
 * <p>
 * 执行流程：
 * 1. 调用工作区注册服务读取当前目录与顺序信息；
 * 2. 组装为 `WorkspaceEntryResponse`；
 * 3. 返回给控制器展示。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class WorkspaceQueryApi implements IWorkspaceQueryApi {

    private final WorkspaceRegistryService workspaceRegistryService;

    /**
     * 处理工作区 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param workspaceRegistryService 工作区 Registry Service参数。
     * @return 方法执行结果。
     */
    public WorkspaceQueryApi(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    /**
     * 查询当前所有工作区。
     * <p>
     * 执行流程：
     * 1. 从工作区注册服务读取配置和磁盘目录；
     * 2. 合并为排序后的列表；
     * 3. 返回给前端。
     */
    public Response<List<WorkspaceEntryResponseDTO>> getWorkspaces() {
        return execute(workspaceRegistryService::listWorkspaces);
    }

    /**
     * 查询目录下可选子目录。
     * <p>
     * 执行流程：
     * 1. 接收 path 参数；
     * 2. 由工作区注册服务读取目录内容；
     * 3. 返回可供选择的目录列表。
     */
    public Response<List<String>> listDirectories(String path) {
        return execute(() -> workspaceRegistryService.listDirectories(path));
    }

    private <T> Response<T> execute(java.util.concurrent.Callable<T> action) {
        try {
            return Response.success(action.call());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (Exception exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
