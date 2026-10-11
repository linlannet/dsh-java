package net.linlan.dsh.infrastructure.adapter.subagent;

import net.linlan.dsh.domain.agent.service.subagent.SubagentProvider;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Base for out-of-process subagent providers: spawns a child process, sends the
 * prompt via stdin, and reads the assistant output from stdout.
 *
 * <p>Subclasses define the command, arguments, and wire protocol encoding.
 *
 *
 */
public abstract class OutOfProcessSubagentProvider implements SubagentProvider {

    protected volatile String command;
    protected final List<String> args;
    protected final String cwd;
    protected final Map<String, String> env;
    protected final long timeoutMs;

    /**
     * 初始化进程外子智能体提供者。
     * <p>流程：准备命令和工作目录 → 启动子进程 → 转换输出。</p>
     */
    protected OutOfProcessSubagentProvider(String command, List<String> args, String cwd,
                                           Map<String, String> env, long timeoutMs) {
        this.command = command;
        this.args = List.copyOf(args);
        this.cwd = cwd;
        this.env = env == null ? Map.of() : Map.copyOf(env);
        this.timeoutMs = timeoutMs;
    }

    public void setCommand(String command) {
        if (command != null && !command.isBlank()) {
            this.command = command.trim();
        }
    }

    @Override
    public CompletableFuture<SubagentResult> start(SubagentStartRequest request) {
        return CompletableFuture.supplyAsync(() -> runChild(request));
    }
    /**
     * 返回 capabilities。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public SubagentCapabilities capabilities() {
        return new SubagentCapabilities(false, false, false);
    }

    /**
     * 运行Child。
     */
    private SubagentResult runChild(SubagentStartRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) pb.directory(new File(cwd));
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            String wireInput = encodePrompt(request);
            process.getOutputStream().write(wireInput.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new SubagentResult("error",
                        List.of(new TextBlock("subagent timed out after " + timeoutMs + "ms")),
                        "timeout");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errText = new String(stderr, StandardCharsets.UTF_8).trim();
                return new SubagentResult("error",
                        List.of(new TextBlock("subagent exited with code " + exitCode + ": " + errText)),
                        "exit-" + exitCode);
            }

            String output = new String(stdout, StandardCharsets.UTF_8).trim();
            List<ContentBlock> blocks = decodeOutput(output);
            return new SubagentResult("completed", blocks, "ok");
        } catch (Exception e) {
            return new SubagentResult("error",
                    List.of(new TextBlock("subagent execution failed: " + e.getMessage())),
                    "exception");
        }
    }

    /** Encode the prompt into the wire format expected by the child process. */
    protected abstract String encodePrompt(SubagentStartRequest request);

    /** Decode the child's stdout into content blocks. */
    protected List<ContentBlock> decodeOutput(String output) {
        return List.of(new TextBlock(output));
    }
}
