package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/agent/workspace"})
public class WorkspaceFileTreeQueryController {
    private final WorkspaceRegistryService workspaceRegistryService;

    public WorkspaceFileTreeQueryController(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    @GetMapping(value={"/tree"})
    public ResponseEntity<?> tree(@RequestParam(value="path", required=false) String path, @RequestParam(value="depth", required=false, defaultValue="6") int depth) {
        int safeDepth = Math.min(12, Math.max(1, depth));
        try {
            Map<String, Object> tree = this.workspaceRegistryService.fileTree(path, safeDepth);
            return ResponseEntity.ok(tree);
        }
        catch (IllegalArgumentException exception) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("code", "A0001", "info", exception.getMessage()));
        }
    }
}

