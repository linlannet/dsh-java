package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.PluginArtifactCandidateDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.plugin.analysis.IPluginArtifactAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/harness/plugins")
public class HarnessPluginAnalysisController {

    private final IPluginArtifactAnalyzer pluginArtifactAnalyzer;

    public HarnessPluginAnalysisController(IPluginArtifactAnalyzer pluginArtifactAnalyzer) {
        this.pluginArtifactAnalyzer = pluginArtifactAnalyzer;
    }

    public record MavenDependencyRequest(String pomXml) {
    }

    @PostMapping("/analyze-jar")
    public Response<PluginArtifactCandidateDTO> analyzeJar(@RequestParam("file") MultipartFile file) {
        try {
            String original = file.getOriginalFilename() == null ? "plugin.jar" : file.getOriginalFilename();
            String extension = original.toLowerCase(Locale.ROOT).endsWith(".jar") ? ".jar" : "";
            if (extension.isEmpty()) {
                return Response.invalidArgument("请选择 .jar 文件");
            }
            Path staging = Path.of(System.getProperty("java.io.tmpdir"), "dsh-plugin-uploads");
            Files.createDirectories(staging);
            Path target = staging.resolve(UUID.randomUUID() + extension).toAbsolutePath().normalize();
            file.transferTo(target);
            return Response.success(pluginArtifactAnalyzer.analyzeJar(target));
        } catch (RuntimeException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (Exception exception) {
            return Response.internalError("JAR 上传失败：" + exception.getMessage());
        }
    }

    @PostMapping("/analyze-maven")
    public Response<List<PluginArtifactCandidateDTO>> analyzeMaven(@RequestBody MavenDependencyRequest request) {
        return Response.success(pluginArtifactAnalyzer.analyzeMavenDependency(request == null ? null : request.pomXml()));
    }
}
