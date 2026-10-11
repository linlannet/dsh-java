package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.TerminalReadResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface ITerminalQueryApi {

    Response<List<TerminalSessionResponseDTO>> listSessions();

    Response<TerminalReadResponseDTO> read(String sessionId, int maxChars);
}
