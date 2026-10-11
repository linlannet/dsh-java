package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface ITerminalCommandApi {

    Response<TerminalSessionResponseDTO> open(TerminalOpenRequestDTO request);

    Response<TerminalSendResponseDTO> send(String sessionId, TerminalSendRequestDTO request);

    Response<TerminalSessionResponseDTO> close(String sessionId);
}
