package net.linlan.dsh.cases.agent.node;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.cases.agent.factory.AgentMessageDynamicContext;
import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.agent.service.run.PersistingSessionLog;
import net.linlan.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import net.linlan.dsh.domain.agent.model.valobj.AgentStatus;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;
import net.linlan.dsh.domain.runtime.setting.service.AgentModelSettingResolver;
import net.linlan.dsh.domain.sandbox.service.SandboxExtraRootsRegistry;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析节点：获取或创建目标 Agent。
 *
 * <p>调用流程：HTTP/API 门面 → AgentUseCase → AgentMessageFactory → 策略树 → ReactLoopAgent；会话事件写入 SessionEventLog。</p>
 * <p>示例：调用方传入 agentId、用户消息和可选模型配置，返回助手文本与工具调用结果。</p>
 *
 */
@Service("agentMessageResolveNode")
public class AgentResolveNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentResolveNode.class);

    @Autowired
    private AgentRunLifecycle agentFactory;

    @Autowired
    private AgentModelSettingResolver modelSettingResolver;

    @Autowired(required = false)
    private ISessionEventStore sessionEventStore;

    @Autowired
    @Qualifier("agentMessageIntentNode")
    private AgentIntentNode intentNode;

    @Autowired
    private SandboxExtraRootsRegistry sandboxExtraRootsRegistry;

    /**
     * 解析或创建本次消息要使用的 Agent。
     * <p>执行流程：按 agentId 查缓存；命中则复用，未命中则用默认 provider/model 和工作目录创建。
     * 解析结果只写入上下文，本节点不产生最终响应。
     * <p>数据示例：agentId="a-1"，request.model="deepseek-chat"，cwd=null，则使用当前进程目录创建 Agent。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                            AgentMessageDynamicContext ctx) throws Exception {
        // 沙箱额外根目录：请求中携带时更新全局注册表，并确保 cwd 包含在内
        ArrayList<String> sandboxRoots = (ArrayList<String>) request.sandboxRoots();
        if (sandboxRoots != null) {
            String requestCwd = request.cwd();
            if (requestCwd != null && !requestCwd.isBlank() && !sandboxRoots.contains(requestCwd)) {
                ArrayList<String> withCwd = new ArrayList<>(sandboxRoots);
                withCwd.add(requestCwd);
                sandboxRoots = withCwd;
            }
            sandboxExtraRootsRegistry.update(sandboxRoots);
        }

        AgentRun agent = agentFactory.get(request.agentId());
        if (agent != null) {
            boolean streaming = ctx.getDeltaSink() != null || ctx.getToolCallEventSink() != null;
            if (streaming && agent.status() == AgentStatus.RUNNING) {
                throw new IllegalStateException("Agent=" + request.agentId()
                        + " 正在执行上一条消息，请等待完成或先停止当前生成");
            }
        }
        if (agent == null) {
            AgentOptions options = buildAgentOptions(request);
            String workDir = request.cwd() != null ? request.cwd() : System.getProperty("user.dir");
            SessionLog restoredSession = findPersistedSession(request.agentId());
            if (restoredSession != null) {
                log.info("[Resolve] Agent={} 内存实例缺失，从事件日志恢复会话 sessionId={}（事件数={}）",
                        request.agentId(), restoredSession.sessionId(), restoredSession.size());
                agent = agentFactory.resume(request.agentId(), options, workDir, restoredSession);
            } else {
                log.info("[Resolve] Agent={} 不存在，创建新实例（channel={} model={} cwd={}）",
                        request.agentId(), options.channelCode(), options.model(), workDir);
                agent = agentFactory.create(request.agentId(), options, workDir);
            }
            agentFactory.configureApprovalMode(request.agentId(), approvalMode(request));
        } else {
            log.debug("[Resolve] Agent={} 已存在，复用实例", request.agentId());
            agent.updateOptions(buildAgentOptions(request));
            agentFactory.configureApprovalMode(request.agentId(), approvalMode(request));
            agentFactory.updateCwd(request.agentId(), request.cwd());
        }
        ctx.attachSinks(agent);
        ctx.setAgent(agent);
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    /**
     * 获取Next。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @param ctx ctx参数。
     * @return 获取到的Next。
     */

    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return intentNode;
    }

    /**
     * 从事件日志中查找并恢复指定 Agent 的历史会话。
     * <p>按 agentPreset 匹配 SessionHeader，找到后用 PersistingSessionLog 重建事件序列。
     */
    private SessionLog findPersistedSession(String agentId) {
        if (sessionEventStore == null) return null;
        try {
            for (SessionHeader header : sessionEventStore.listHeaders(200)) {
                if (!agentId.equals(header.agentPreset())) continue;
                List<SessionEvent> events = sessionEventStore.readAll(header.sessionId());
                if (events.isEmpty()) continue;
                PersistingSessionLog restored = new PersistingSessionLog(
                        header.sessionId(), header, sessionEventStore);
                restored.restore(events);
                return restored;
            }
        } catch (Exception e) {
            log.warn("[Resolve] Agent={} 尝试从事件日志恢复会话失败，改用全新会话：{}", agentId, e.getMessage());
        }
        return null;
    }

    /**
     * 将 HTTP 请求字段转换为 Agent 运行参数。
     * <p>统一使用 {@link AgentModelSettingResolver}：请求显式值优先，其次数据库默认配置，
     * 最后才使用 application.yml 的模型兜底。再链式追加 reasoningEffort 和 approvalMode。
     */
    private AgentOptions buildAgentOptions(AgentMessageRequestDTO request) {
        return modelSettingResolver.resolve(request.channelCode(), request.maxTokens())
                .withReasoningEffort(request.reasoningEffort())
                .withApprovalMode(approvalMode(request));
    }

    private ApprovalModeVO approvalMode(AgentMessageRequestDTO request) {
        return ApprovalModeVO.from(request.approvalMode());
    }
}
