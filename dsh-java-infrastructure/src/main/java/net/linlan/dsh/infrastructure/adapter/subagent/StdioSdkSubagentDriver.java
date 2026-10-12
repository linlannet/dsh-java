package net.linlan.dsh.infrastructure.adapter.subagent;

import net.linlan.dsh.domain.agent.service.subagent.adapter.port.ISubagentDriverPort;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class StdioSdkSubagentDriver implements ISubagentDriverPort {

    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, BufferedWriter> stdins = new ConcurrentHashMap<>();
    private final Map<String, BufferedReader> stdouts = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final AtomicInteger rpcIdCounter = new AtomicInteger(0);

    @Override
    public String spawn(String agentPreset, String cwd, String command) {
        String id = "sdk-subagent-" + idCounter.incrementAndGet();
        List<String> cmd = parseCommand(command);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new File(cwd));
        }
        pb.redirectErrorStream(false);
        try {
            Process process = pb.start();
            processes.put(id, process);
            stdins.put(id, new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)));
            stdouts.put(id, new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to spawn sdk subagent: " + agentPreset, e);
        }
        return id;
    }

    @Override
    public String send(String subagentId, String message) {
        BufferedWriter writer = stdins.get(subagentId);
        BufferedReader reader = stdouts.get(subagentId);
        if (writer == null || reader == null) {
            throw new IllegalArgumentException("unknown subagentId: " + subagentId);
        }
        int rpcId = rpcIdCounter.incrementAndGet();
        String request = buildJsonRpcRequest(rpcId, "message", message);
        try {
            writer.write(request);
            writer.newLine();
            writer.flush();
            String response = reader.readLine();
            if (response == null) {
                throw new IllegalStateException("subagent stdout closed: " + subagentId);
            }
            return extractJsonRpcResult(response);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to communicate with subagent: " + subagentId, e);
        }
    }

    @Override
    public void shutdown(String subagentId) {
        Process process = processes.remove(subagentId);
        stdins.remove(subagentId);
        stdouts.remove(subagentId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Override
    public boolean isAlive(String subagentId) {
        Process process = processes.get(subagentId);
        return process != null && process.isAlive();
    }

    private List<String> parseCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        return List.of(command.trim().split("\\s+"));
    }

    private String buildJsonRpcRequest(int id, String method, String message) {
        String escaped = message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":{\"message\":\"" + escaped + "\"}}";
    }

    private String extractJsonRpcResult(String response) {
        int idx = response.indexOf("\"result\"");
        if (idx < 0) {
            return response;
        }
        int colon = response.indexOf(':', idx);
        int startQuote = response.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return response;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startQuote + 1; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '"' && (i == 0 || response.charAt(i - 1) != '\\')) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
