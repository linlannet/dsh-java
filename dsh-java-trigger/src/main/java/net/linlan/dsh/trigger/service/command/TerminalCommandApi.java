package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.ITerminalCommandApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.terminal.ITerminalCommandCase;
import org.springframework.stereotype.Service;

@Service
public class TerminalCommandApi implements ITerminalCommandApi {
    private final ITerminalCommandCase terminalCommandCase;

    public TerminalCommandApi(ITerminalCommandCase terminalCommandCase) {
        this.terminalCommandCase = terminalCommandCase;
    }

    public Response<TerminalSessionResponseDTO> open(TerminalOpenRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("terminal open request must not be null");
        }
        return execute(() -> terminalCommandCase.open(request));
    }

    public Response<TerminalSendResponseDTO> send(String sessionId, TerminalSendRequestDTO request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (request == null) {
            throw new IllegalArgumentException("terminal send request must not be null");
        }
        long timeoutMs = request.timeoutMs() == null ? 120_000L : request.timeoutMs();
        return execute(() -> terminalCommandCase.send(
                sessionId, new TerminalSendRequestDTO(request.command(), timeoutMs)
        ));
    }

    public Response<TerminalSessionResponseDTO> close(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return execute(() -> terminalCommandCase.close(sessionId));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
