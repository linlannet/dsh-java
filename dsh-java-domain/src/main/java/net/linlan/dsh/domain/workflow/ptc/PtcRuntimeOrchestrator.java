package net.linlan.dsh.domain.workflow.ptc;

import java.util.Map;
import java.util.Objects;

public final class PtcRuntimeOrchestrator {

    public record OrchestrationResult(
            String script,
            String stdout,
            int exitCode,
            boolean success,
            Map<String, Object> metadata
    ) {
        public OrchestrationResult {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public enum State {
        CREATED, RUNNING, STOPPED
    }

    private final String nodeExecutable;
    private final String sandboxScriptPath;
    private State state = State.CREATED;
    private int orchestrationCount = 0;

    public PtcRuntimeOrchestrator(String nodeExecutable, String sandboxScriptPath) {
        if (nodeExecutable == null || nodeExecutable.isBlank()) {
            throw new IllegalArgumentException("node executable required");
        }
        if (sandboxScriptPath == null || sandboxScriptPath.isBlank()) {
            throw new IllegalArgumentException("sandbox script path required");
        }
        this.nodeExecutable = nodeExecutable;
        this.sandboxScriptPath = sandboxScriptPath;
    }

    public OrchestrationResult orchestrate(String workflowScript) {
        if (state != State.RUNNING) {
            throw new IllegalStateException("orchestrator not running");
        }
        if (workflowScript == null || workflowScript.isBlank()) {
            throw new IllegalArgumentException("workflow script required");
        }
        orchestrationCount++;
        return new OrchestrationResult(workflowScript, "", 0, true, Map.of("orchestrationIndex", orchestrationCount));
    }

    public void start() {
        if (state != State.CREATED) {
            throw new IllegalStateException("orchestrator already started or stopped");
        }
        state = State.RUNNING;
    }

    public void stop() {
        if (state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;
    }

    public State state() {
        return state;
    }

    public int orchestrationCount() {
        return orchestrationCount;
    }

    public String nodeExecutable() {
        return nodeExecutable;
    }

    public String sandboxScriptPath() {
        return sandboxScriptPath;
    }
}
