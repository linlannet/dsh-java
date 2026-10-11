package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/agent/workspace"})
public class WorkspaceFileQueryController {
    private final WorkspaceRegistryService workspaceRegistryService;

    public WorkspaceFileQueryController(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    @GetMapping(value={"/file"})
    public ResponseEntity<ByteArrayResource> file(@RequestParam(value="path") String path, @RequestParam(value="download", required=false) String download) {
        String mime;
        byte[] bytes;
        Path target;
        try {
            target = this.workspaceRegistryService.resolveInsideRoot(path);
        }
        catch (IllegalArgumentException exception) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(new ByteArrayResource(("{\"code\":\"A0001\",\"info\":\"" + exception.getMessage() + "\"}").getBytes()));
        }
        if (!Files.isRegularFile(target, new LinkOption[0])) {
            return ResponseEntity.notFound().build();
        }
        try {
            bytes = Files.readAllBytes(target);
        }
        catch (IOException exception) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        try {
            mime = Files.probeContentType(target);
        }
        catch (IOException ignored) {
            mime = null;
        }
        MediaType mediaType = mime == null || mime.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType((String)mime);
        ResponseEntity.BodyBuilder builder = ((ResponseEntity.BodyBuilder)ResponseEntity.ok().contentType(mediaType).header("Cache-Control", new String[]{"no-store"})).contentLength((long)bytes.length);
        if ("1".equals(download)) {
            String fileName = target.getFileName() == null ? "file" : target.getFileName().toString();
            builder.header("Content-Disposition", new String[]{"attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8)});
        }
        return builder.body(new ByteArrayResource(bytes));
    }
}

