package net.linlan.dsh.api.port;

import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 多数字人协作端口契约。
 */
public interface ICollaborationService {

    Map<String, Object> createRoom(String workspaceId, String title, String objective, String orchestrationMode);

    Map<String, Object> roomDetail(String roomId);

    Map<String, Object> joinRoom(String roomId, String digitalHumanId, String roomRole);

    Map<String, Object> leaveRoom(String roomId, String digitalHumanId);

    Map<String, Object> postMessage(String roomId, String content, List<String> mentions, String token,
            Map<String, String> digitalHumanTokens, String channelCode, String approvalMode, String cwd,
            List<String> sandboxRoots);

    Map<String, Object> cancelTask(String roomId, String taskId);

    Map<String, Object> resumeTask(String roomId, String taskId, String token,
            Map<String, String> digitalHumanTokens, String channelCode, String approvalMode);

    Map<String, Object> retryTask(String roomId, String taskId, String token,
            Map<String, String> digitalHumanTokens, String channelCode, String approvalMode);

    Map<String, Object> reassignTask(String roomId, String taskId, String targetDigitalHumanId, String token,
            Map<String, String> digitalHumanTokens, String channelCode, String approvalMode);

    SseEmitter subscribe(String roomId, long afterSeq);

    List<Map<String, Object>> listEvents(String roomId, long afterSeq);
}
