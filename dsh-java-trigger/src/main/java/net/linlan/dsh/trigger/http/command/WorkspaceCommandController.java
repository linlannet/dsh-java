package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.CreateWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.RenameWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.ReorderWorkspaceRequestDTO;
import net.linlan.dsh.api.dto.WorkspaceEntryResponseDTO;
import net.linlan.dsh.api.IWorkspaceCommandApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向 Agent UI 暴露工作区写操作。 */
@RestController
@RequestMapping("/api/agent")
public class WorkspaceCommandController {

    private final IWorkspaceCommandApi workspaceCommandApi;

    /**
     * 处理工作区 命令 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param workspaceCommandApi 工作区 命令 API参数。
     * @return 方法执行结果。
     */
    public WorkspaceCommandController(IWorkspaceCommandApi workspaceCommandApi) {
        this.workspaceCommandApi = workspaceCommandApi;
    }

    /**
     * 创建 工作区。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param request 请求对象。
     * @return 创建的领域对象。
     */
    @PostMapping("/workspaces")
    public Response<List<WorkspaceEntryResponseDTO>> createWorkspace(@RequestBody CreateWorkspaceRequestDTO request) {
        return workspaceCommandApi.createWorkspace(request);
    }

    /**
     * 删除 工作区。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param name 名称。
     * @return 方法执行结果。
     */
    @DeleteMapping("/workspaces/{name}")
    public Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(@PathVariable("name") String name) {
        return workspaceCommandApi.deleteWorkspace(name);
    }

    /**
     * 重命名工作区并刷新目录顺序。
     * <p>流程：读取路径参数和请求体 → 调用工作区命令服务 → 返回更新后的工作区列表。</p>
     *
     * @param name 原工作区名称。
     * @param request 新工作区名称。
     * @return 更新后的工作区列表。
     */
    @PatchMapping("/workspaces/{name}")
    public Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(@PathVariable("name") String name,
                                                        @RequestBody RenameWorkspaceRequestDTO request) {
        return workspaceCommandApi.renameWorkspace(name, request);
    }

    /**
     * 处理reorder Workspaces。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param request 请求对象。
     * @return 方法执行结果。
     */
    @PutMapping("/workspaces/order")
    public Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(@RequestBody ReorderWorkspaceRequestDTO request) {
        return workspaceCommandApi.reorderWorkspaces(request);
    }

}
