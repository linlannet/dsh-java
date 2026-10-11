package net.linlan.dsh.cases.session.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.SessionEventQueryRequestDTO;
import net.linlan.dsh.api.dto.SessionEventQueryResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会话事件查询节点。
 * <p>流程：校验 sessionId → 按 fromSeq 选择增量或全量读取 → 过滤 toSeq 和事件类型 → 返回事件 DTO 列表。</p>
 * <p>数据示例：传入 {@code sessionId=BLUE-8090, fromSeq=10, toSeq=20, eventTypes=["UserMessageEvent"]}；
 * 返回 {@code seq=11, type=UserMessageEvent, data={...}}。</p>
 */
@Service
public class SessionEventQueryNode
        extends AbstractStrategyRouter<SessionEventQueryRequestDTO, NoContext, List<SessionEventQueryResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(SessionEventQueryNode.class);

    private final ISessionEventLogService sessionEventLogService;

    public SessionEventQueryNode(ISessionEventLogService sessionEventLogService) {
        this.sessionEventLogService = sessionEventLogService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<SessionEventQueryResponseDTO> doApply(SessionEventQueryRequestDTO query, NoContext context) {
        CaseGuard.requireSessionId(query.sessionId());
        List<SessionEvent> events = query.fromSeq() != null
                ? sessionEventLogService.readAfter(query.sessionId(), query.fromSeq())
                : sessionEventLogService.readAll(query.sessionId());

        List<SessionEventQueryResponseDTO> result = new ArrayList<>();
        for (SessionEvent event : events) {
            if (query.toSeq() != null && event.seq() > query.toSeq()) {
                break;
            }
            String eventType = event.getClass().getSimpleName();
            if (query.eventTypes() != null && !query.eventTypes().isEmpty()
                    && !query.eventTypes().contains(eventType)) {
                continue;
            }
            result.add(new SessionEventQueryResponseDTO(event.seq(), eventType, event.time(), toMap(event)));
        }
        log.debug("[Session] 会话事件查询完成，sessionId={} fromSeq={} toSeq={} count={}",
                query.sessionId(), query.fromSeq(), query.toSeq(), result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<SessionEventQueryRequestDTO, NoContext, List<SessionEventQueryResponseDTO>> getNext(
            SessionEventQueryRequestDTO query, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private Map<String, Object> toMap(SessionEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seq", event.seq());
        data.put("type", event.getClass().getSimpleName());
        data.put("time", event.time() != null ? event.time().toString() : null);
        return data;
    }
}
