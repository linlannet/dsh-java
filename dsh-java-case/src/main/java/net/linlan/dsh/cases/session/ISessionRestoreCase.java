package net.linlan.dsh.cases.session;

import net.linlan.dsh.api.dto.SessionRestoreRequestDTO;
import net.linlan.dsh.api.dto.SessionRestoreResponseDTO;

/**
 * 基于持久化事件日志恢复会话的用例。
 *
 * <p>调用流程：Controller/API → 会话用例 → SessionQueryService/SessionRebuilderService → SessionEventStore。</p>
 * <p>示例：按 sessionId 查询事件后，可重建完整消息时间线。</p>
 *
 */
public interface ISessionRestoreCase {

    SessionRestoreResponseDTO execute(SessionRestoreRequestDTO request);
}
