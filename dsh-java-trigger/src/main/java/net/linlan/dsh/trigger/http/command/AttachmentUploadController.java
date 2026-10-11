package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value={"/api/agent"})
public class AttachmentUploadController {
    private static final String ATTACHMENT_DIR = ".dsh-attachments";
    private static final long MAX_ATTACHMENT_BYTES = 0x1E00000L;
    private final WorkspaceRegistryService workspaceRegistryService;

    public AttachmentUploadController(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    @PostMapping(value={"/attachments"})
    public Response<Map<String, Object>> upload(@RequestParam(value="file") MultipartFile file, @RequestParam(value="workspace") String workspace) {
        if (file == null || file.isEmpty()) {
            return Response.invalidArgument((String)"\u9644\u4ef6\u5185\u5bb9\u4e3a\u7a7a");
        }
        if (file.getSize() > 0x1E00000L) {
            return Response.invalidArgument((String)"\u9644\u4ef6\u4e0d\u80fd\u8d85\u8fc7 30MB");
        }
        String original = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "attachment" : file.getOriginalFilename();
        try {
            Path workspaceDir = this.workspaceRegistryService.resolveWorkspaceDir(workspace);
            Path attachDir = workspaceDir.resolve(ATTACHMENT_DIR);
            Files.createDirectories(attachDir, new FileAttribute[0]);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path target = attachDir.resolve(timestamp + "-" + AttachmentUploadController.sanitizeFileName(original)).toAbsolutePath().normalize();
            file.transferTo(target);
            LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("path", target.toString());
            data.put("name", original);
            data.put("size", file.getSize());
            data.put("mimeType", file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            return Response.success(data);
        }
        catch (IllegalArgumentException exception) {
            return Response.invalidArgument((String)exception.getMessage());
        }
        catch (IOException exception) {
            return Response.internalError((String)("\u9644\u4ef6\u4fdd\u5b58\u5931\u8d25\uff1a" + exception.getMessage()));
        }
        catch (Exception exception) {
            return Response.internalError((String)("\u9644\u4ef6\u4e0a\u4f20\u5931\u8d25\uff1a" + exception.getMessage()));
        }
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/\\x00-\\x1f]", "_").trim();
        if (cleaned.length() > 120) {
            String ext = "";
            int dot = cleaned.lastIndexOf(46);
            if (dot > 0) {
                ext = cleaned.substring(dot).toLowerCase(Locale.ROOT);
            }
            cleaned = cleaned.substring(0, 120 - ext.length()) + ext;
        }
        return cleaned;
    }
}

