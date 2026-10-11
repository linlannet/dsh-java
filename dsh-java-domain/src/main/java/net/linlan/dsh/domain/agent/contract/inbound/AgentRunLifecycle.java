package net.linlan.dsh.domain.agent.contract.inbound;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import java.util.concurrent.CompletableFuture;

/**
 * AgentRunLifecycle 表示创建、复用和释放智能体运行的入口契约。
 *
 * <p>调用流程：case 层请求运行 → Agent 领域服务创建或复用运行实例。</p>
 */
public interface AgentRunLifecycle {

    AgentRun create(String agentId, AgentOptions options, String cwd);

    AgentRun resume(String agentId, AgentOptions options, String cwd, SessionLog session);

    AgentRun get(String agentId);

    void configureApprovalMode(String agentId, ApprovalModeVO mode);

    void updateCwd(String agentId, String cwd);

    CompletableFuture<Void> disposeAll();
}
