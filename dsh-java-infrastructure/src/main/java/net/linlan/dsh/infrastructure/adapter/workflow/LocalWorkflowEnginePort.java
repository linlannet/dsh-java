package net.linlan.dsh.infrastructure.adapter.workflow;

import net.linlan.dsh.domain.workflow.adapter.port.IWorkflowEnginePort;
import net.linlan.dsh.domain.workflow.model.entity.WorkflowRun;
import net.linlan.dsh.domain.workflow.model.entity.WorkflowStartRequest;
import net.linlan.dsh.domain.workflow.model.valobj.WorkflowResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local workflow engine: executes the script via the system Node.js interpreter.
 * A full implementation would use a worker thread with agent/log/phase bindings.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class LocalWorkflowEnginePort implements IWorkflowEnginePort {

    @Override
    public WorkflowRun start(WorkflowStartRequest request) {
        String runId = "wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        CompletableFuture<WorkflowResult> result = CompletableFuture.supplyAsync(() -> {
            // In local mode, we execute the script via node and capture its return.
            // A full implementation would provide agent(), log(), phase() bindings.
            try {
                ProcessBuilder pb = new ProcessBuilder("node", "-e",
                        "(async () => { " + request.script() + " })().then(v => console.log(JSON.stringify(v || null)))");
                if (request.parentCwd() != null) pb.directory(new java.io.File(request.parentCwd()));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                byte[] output = process.getInputStream().readAllBytes();
                boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return WorkflowResult.error("workflow timed out", 0);
                }
                if (process.exitValue() != 0) {
                    return WorkflowResult.error(new String(output).trim(), 0);
                }
                String json = new String(output).trim();
                return WorkflowResult.completed(json, 0);
            } catch (Exception e) {
                return WorkflowResult.error(e.getMessage(), 0);
            }
        });

        return new WorkflowRun(runId, request.meta(), result);
    }
}
