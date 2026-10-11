package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ConversationMessageResponseDTO;
import net.linlan.dsh.api.dto.ConversationSessionSummaryResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessConsoleQueryApi {

    Response<List<ConversationSessionSummaryResponseDTO>> listRecentSessions(int limit, int offset);

    Response<List<ConversationMessageResponseDTO>> listMessages(String sessionId);
}
