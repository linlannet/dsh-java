package net.linlan.dsh.cases.session.query;

import net.linlan.dsh.api.dto.SessionDetailRequestDTO;
import net.linlan.dsh.api.dto.SessionListRequestDTO;
import net.linlan.dsh.api.dto.SessionQueryResponseDTO;

import net.linlan.dsh.cases.session.ISessionQueryCase;
import net.linlan.dsh.cases.session.factory.SessionPipelineFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 薄委托到 {@link IConversationQueryService}。
 *
 * <p>调用流程：Controller/API → 会话用例 → SessionQueryService/SessionRebuilderService → SessionEventStore。</p>
 * <p>示例：按 sessionId 查询事件后，可重建完整消息时间线。</p>
 *
 */
@Service
public class SessionQueryCaseImpl implements ISessionQueryCase {

    private final SessionPipelineFactory sessionPipelineFactory;

    /**
     * 处理会话 Query Case Impl。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param conversationQueryService conversation Query Service参数。
     * @return 方法执行结果。
     */
    public SessionQueryCaseImpl(SessionPipelineFactory sessionPipelineFactory) {
        this.sessionPipelineFactory = sessionPipelineFactory;
    }

    /**
     * 查询Session Detail。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param query query参数。
     * @return 方法执行结果。
     */

    /**
     * 查询Session Detail。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param query query参数。
     * @return 方法执行结果。
     */

    @Override
    public List<SessionQueryResponseDTO> querySessionList(SessionListRequestDTO query) {
        return sessionPipelineFactory.sessionListPipeline().execute(query);
    }
    /**
     * 查询Session Detail。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param query query参数。
     * @return 方法执行结果。
     */


    @Override
    public SessionQueryResponseDTO querySessionDetail(SessionDetailRequestDTO query) {
        return sessionPipelineFactory.sessionDetailPipeline().execute(query);
    }
}
