package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.worktree.model.valobj.WorktreeCreatedPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** enter_worktree：同名工作树存在则进入，否则通过 Git 端口创建后进入。 */
public final class EnterWorktreeTool implements ToolDefinition {

    private final IGitPort gitPort;
    private final ISessionEventStore eventStore;
    private final String sessionId;
    private final AtomicReference<String> cwdRef;

    public EnterWorktreeTool() {
        this(null, null, null, null);
    }

    public EnterWorktreeTool(
            IGitPort gitPort,
            ISessionEventStore eventStore,
            String sessionId,
            AtomicReference<String> cwdRef
    ) {
        this.gitPort = gitPort;
        this.eventStore = eventStore;
        this.sessionId = sessionId;
        this.cwdRef = cwdRef;
    }

    @Override
    public String name() {
        return "enter_worktree";
    }

    @Override
    public String description() {
        return "Create a Git worktree for the branch when needed, enter it, and make it the session working directory.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "repoPath", Map.of("type", "string", "description", "Path to the source Git repository."),
                        "branchName", Map.of("type", "string", "description", "Branch for the same-named worktree.")
                ),
                "required", List.of("repoPath", "branchName")
        );
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext context) {
        if (!(args instanceof Map<?, ?> arguments)) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail(new ToolArgsError("arguments must be an object").toFailure()));
        }
        String repoPath = arguments.get("repoPath") == null ? null : String.valueOf(arguments.get("repoPath"));
        String branchName = arguments.get("branchName") == null ? null : String.valueOf(arguments.get("branchName"));
        try {
            WorktreeResult result = enter(repoPath, branchName);
            return CompletableFuture.completedFuture(ToolExecutionResult.ok(List.of(new TextBlock(
                    "Entered worktree " + result.path() + " on branch " + result.branch()))));
        } catch (HarnessError error) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(error.toFailure()));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(
                    "enter_worktree failed: " + error.getMessage(), "WORKTREE_CREATE_FAILED"));
        }
    }

    public WorktreeResult enter(String repoPath, String branchName) {
        return enter(repoPath, branchName, sessionId);
    }

    public WorktreeResult enter(String repoPath, String branchName, String eventSessionId) {
        if (repoPath == null || repoPath.isBlank() || branchName == null || branchName.isBlank()) {
            throw new ToolArgsError("repoPath and branchName must not be blank");
        }
        if (!Files.exists(Path.of(repoPath, ".git"))) {
            throw new NotGitRepoException("非 Git 仓库: " + repoPath);
        }
        String worktreePath = repoPath + "-worktree-" + branchName;
        Path path = Path.of(worktreePath);
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("worktree path is not a directory: " + worktreePath);
            }
            return entered(new WorktreeResult(worktreePath, branchName));
        }
        if (gitPort == null) {
            throw new IllegalStateException("Git worktree port is not configured");
        }
        String createdPath = gitPort.createWorktree(repoPath, branchName, worktreePath);
        WorktreeResult result = new WorktreeResult(createdPath, branchName);
        if (eventStore != null && eventSessionId != null && !eventSessionId.isBlank()) {
            eventStore.append(eventSessionId, new SessionEvent.Generic(
                    eventStore.getLastSeq(eventSessionId) + 1,
                    Instant.now(),
                    SessionEventType.WORKTREE_CREATED,
                    payloadJson(new WorktreeCreatedPayload(repoPath, createdPath, branchName))));
        }
        return entered(result);
    }

    private WorktreeResult entered(WorktreeResult result) {
        if (cwdRef != null) {
            cwdRef.set(result.path());
        }
        return result;
    }

    private static String payloadJson(WorktreeCreatedPayload payload) {
        return "{\"repoPath\":\"" + escape(payload.repoPath())
                + "\",\"worktreePath\":\"" + escape(payload.worktreePath())
                + "\",\"branch\":\"" + escape(payload.branch()) + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record WorktreeResult(String path, String branch) {
    }

    public static class NotGitRepoException extends HarnessError {

        public static final String NOT_GIT_REPO = "WORKTREE_NOT_GIT_REPO";

        public NotGitRepoException(String message) {
            super(NOT_GIT_REPO, message);
        }
    }
}
