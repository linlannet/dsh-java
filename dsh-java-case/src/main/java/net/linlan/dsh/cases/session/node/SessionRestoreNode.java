package net.linlan.dsh.cases.session.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.SessionRestoreRequestDTO;
import net.linlan.dsh.api.dto.SessionRestoreResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.session.event.service.SessionRebuilderService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会话状态恢复节点。
 * <p>流程：校验恢复请求 → 调用领域重建服务重放事件 → 将快照映射为恢复结果 DTO。</p>
 * <p>数据示例：传入 {@code sessionId=BLUE-8090}；返回 {@code success=true, phase=IDLE, turnCount=3}，
 * 并包含恢复后的用户/助手消息。</p>
 */
@Service
public class SessionRestoreNode
        extends AbstractStrategyRouter<SessionRestoreRequestDTO, NoContext, SessionRestoreResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(SessionRestoreNode.class);

    private final SessionRebuilderService sessionRebuilderService;

    public SessionRestoreNode(SessionRebuilderService sessionRebuilderService) {
        this.sessionRebuilderService = sessionRebuilderService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected SessionRestoreResponseDTO doApply(SessionRestoreRequestDTO request, NoContext context) {
        CaseGuard.requireNonNull(request, "session restore request must not be null");
        CaseGuard.requireSessionId(request.sessionId());
        var snapshot = sessionRebuilderService.rebuild(request.sessionId());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (var message : snapshot.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role().toString().toLowerCase());
            item.put("content", message.content().toString());
            messages.add(item);
        }
        var result = new SessionRestoreResponseDTO(
                request.sessionId(),
                true,
                snapshot.phase() != null ? snapshot.phase().getClass().getSimpleName().toUpperCase() : "IDLE",
                snapshot.turnCount(),
                snapshot.stepCount(),
                snapshot.lastTurnEndReason() != null ? snapshot.lastTurnEndReason().toString() : null,
                messages,
                Instant.now()
        );
        log.info("[Session] 会话恢复完成，sessionId={} phase={} turnCount={} stepCount={}",
                request.sessionId(), result.agentPhase(), result.turnCount(), result.stepCount());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<SessionRestoreRequestDTO, NoContext, SessionRestoreResponseDTO> getNext(
            SessionRestoreRequestDTO request, NoContext context) {
        return defaultStrategyHandler;
    }
}
