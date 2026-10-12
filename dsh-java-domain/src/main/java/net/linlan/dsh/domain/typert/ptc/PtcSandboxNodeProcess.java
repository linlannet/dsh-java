package net.linlan.dsh.domain.typert.ptc;

import java.util.Map;

/**
 * PtcSandboxNodeProcess 定义沙箱 Node 进程执行 PTC 的契约。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class PtcSandboxNodeProcess {

    private final String nodeExecutable;
    private final String sandboxScriptPath;
    private State state = State.CREATED;

    public PtcSandboxNodeProcess(String nodeExecutable, String sandboxScriptPath) {
        if (nodeExecutable == null || nodeExecutable.isBlank()) {
            throw new IllegalArgumentException("node executable required");
        }
        if (sandboxScriptPath == null || sandboxScriptPath.isBlank()) {
            throw new IllegalArgumentException("sandbox script path required");
        }
        this.nodeExecutable = nodeExecutable;
        this.sandboxScriptPath = sandboxScriptPath;
    }

    public void start() {
        if (state != State.CREATED) {
            throw new IllegalStateException("process already started or stopped");
        }
        state = State.RUNNING;
    }

    public PtcResult execute(String script) {
        if (state != State.RUNNING) {
            throw new IllegalStateException("process not running");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script required");
        }
        return new PtcResult(script, "", 0, Map.of(), true);
    }

    public PtcResult execute(String script, Map<String, Object> env) {
        if (state != State.RUNNING) {
            throw new IllegalStateException("process not running");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script required");
        }
        Map<String, Object> effectiveEnv = env == null ? Map.of() : Map.copyOf(env);
        return new PtcResult(script, "", 0, effectiveEnv, true);
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

    public String nodeExecutable() {
        return nodeExecutable;
    }

    public String sandboxScriptPath() {
        return sandboxScriptPath;
    }

    public enum State {
        CREATED, RUNNING, STOPPED
    }

    public record PtcResult(
            String script,
            String stdout,
            int exitCode,
            Map<String, Object> env,
            boolean success
    ) {
        public PtcResult {
            env = env == null ? Map.of() : Map.copyOf(env);
        }
    }
}
