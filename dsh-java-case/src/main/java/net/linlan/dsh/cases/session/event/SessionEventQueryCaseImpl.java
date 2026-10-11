package net.linlan.dsh.cases.session.event;

import net.linlan.dsh.api.dto.SessionEventQueryRequestDTO;
import net.linlan.dsh.api.dto.SessionEventQueryResponseDTO;

import net.linlan.dsh.cases.session.ISessionEventQueryCase;
import net.linlan.dsh.cases.session.factory.SessionPipelineFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 薄委托到 {@link ISessionEventLogService}。
 *
 * <p>调用流程：Controller/API → 会话用例 → SessionQueryService/SessionRebuilderService → SessionEventStore。</p>
 * <p>示例：按 sessionId 查询事件后，可重建完整消息时间线。</p>
 *
 */
@Service
public class SessionEventQueryCaseImpl implements ISessionEventQueryCase {

    private final SessionPipelineFactory sessionPipelineFactory;

    /**
     * 处理会话 事件 Query Case Impl。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param sessionEventLogService 会话 事件 Log Service参数。
     * @return 方法执行结果。
     */
    public SessionEventQueryCaseImpl(SessionPipelineFactory sessionPipelineFactory) {
        this.sessionPipelineFactory = sessionPipelineFactory;
    }

    /**
     * 编排会话事件查询，将过滤条件交给事件 Pipeline。
     */
    @Override
    public List<SessionEventQueryResponseDTO> queryEvents(SessionEventQueryRequestDTO query) {
        return sessionPipelineFactory.eventsPipeline().execute(query);
    }
}
