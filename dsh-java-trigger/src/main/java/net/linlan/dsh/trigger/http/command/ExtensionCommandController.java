package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.extension.IExtensionManagementCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value={"/api/harness/extensions"})
public class ExtensionCommandController {
    private final IExtensionManagementCase extensionManagement;

    public ExtensionCommandController(IExtensionManagementCase extensionManagement) {
        this.extensionManagement = extensionManagement;
    }

    @PostMapping(value={"/skills/install"})
    public Response<IExtensionManagementCase.SkillInstallOutcome> installSkill(@RequestBody Map<String, String> body) {
        try {
            return Response.success(this.extensionManagement.installSkillFromGit(body.get("gitUrl"), body.get("subdir"), body.get("name")));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @PostMapping(value={"/skills/upload"}, consumes={"multipart/form-data"})
    public Response<IExtensionManagementCase.SkillInstallOutcome> uploadSkill(@RequestParam(value="file") MultipartFile file, @RequestParam(value="subdir", required=false) String subdir, @RequestParam(value="name", required=false) String name) {
        try {
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!original.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                return Response.invalidArgument("请选择 .zip 压缩包");
            }
            Path staging = Path.of(System.getProperty("java.io.tmpdir"), "dsh-skill-uploads");
            Files.createDirectories(staging, new FileAttribute[0]);
            Path target = staging.resolve(String.valueOf(UUID.randomUUID()) + ".zip").toAbsolutePath().normalize();
            file.transferTo(target);
            return Response.success(this.extensionManagement.installSkillFromZip(target.toString(), subdir, name));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError("技能压缩包上传失败：" + e.getMessage());
        }
    }

    @PostMapping(value={"/skills/{name}/enabled"})
    public Response<Boolean> setSkillEnabled(@PathVariable(value="name") String name, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = Boolean.TRUE.equals(body.get("enabled")) || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
            this.extensionManagement.setSkillEnabled(name, enabled);
            return Response.success(enabled);
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @DeleteMapping(value={"/skills/{name}"})
    public Response<Boolean> removeSkill(@PathVariable(value="name") String name, @RequestParam(value="cwd", required=false) String cwd) {
        try {
            this.extensionManagement.removeSkill(name, cwd);
            return Response.success(Boolean.TRUE);
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @PutMapping(value={"/mcp/servers"})
    public Response<IExtensionManagementCase.McpApplyOutcome> upsertMcpServer(@RequestBody IExtensionManagementCase.McpServer server) {
        try {
            return Response.success(this.extensionManagement.upsertMcpServer(server));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @PostMapping(value={"/mcp/servers/test"})
    public Response<IExtensionManagementCase.McpApplyOutcome> testMcpServer(@RequestBody IExtensionManagementCase.McpServer server) {
        try {
            return Response.success(this.extensionManagement.testMcpServer(server));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @DeleteMapping(value={"/mcp/servers/{name}"})
    public Response<Boolean> removeMcpServer(@PathVariable(value="name") String name) {
        try {
            this.extensionManagement.removeMcpServer(name);
            return Response.success(Boolean.TRUE);
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }

    @PutMapping(value={"/cli"})
    public Response<IExtensionManagementCase.CliConfig> setCli(@RequestBody IExtensionManagementCase.CliConfig config) {
        try {
            return Response.success(this.extensionManagement.setCliConfig(config));
        }
        catch (IllegalArgumentException e) {
            return Response.invalidArgument(e.getMessage());
        }
        catch (Exception e) {
            return Response.internalError(e.getMessage());
        }
    }
}
