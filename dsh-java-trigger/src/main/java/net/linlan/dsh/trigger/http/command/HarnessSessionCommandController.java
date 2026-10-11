package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.SessionRestoreResponseDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.session.ISessionRestoreCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/harness/sessions")
public class HarnessSessionCommandController {

    private final ISessionRestoreCase sessionRestoreCase;

    public HarnessSessionCommandController(ISessionRestoreCase sessionRestoreCase) {
        this.sessionRestoreCase = sessionRestoreCase;
    }

    @PostMapping("/{sessionId}/restore")
    public Response<SessionRestoreResponseDTO> restore(@PathVariable("sessionId") String sessionId) {
        return Response.success(sessionRestoreCase.execute(new net.linlan.dsh.api.dto.SessionRestoreRequestDTO(sessionId)));
    }
}
