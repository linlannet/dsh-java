package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.extension.IExtensionManagementCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/harness/extensions"})
public class ExtensionQueryController {
    private final IExtensionManagementCase extensionManagement;

    public ExtensionQueryController(IExtensionManagementCase extensionManagement) {
        this.extensionManagement = extensionManagement;
    }

    @GetMapping(value={"/skills"})
    public Response<List<IExtensionManagementCase.SkillSummary>> skills(@RequestParam(value="cwd", required=false) String cwd) {
        try {
            return Response.success(this.extensionManagement.listSkills(cwd));
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @GetMapping(value={"/mcp/servers"})
    public Response<List<IExtensionManagementCase.McpServer>> mcpServers() {
        try {
            return Response.success(this.extensionManagement.listMcpServers());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @GetMapping(value={"/cli"})
    public Response<IExtensionManagementCase.CliConfig> cli() {
        try {
            return Response.success(this.extensionManagement.getCliConfig());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }
}
