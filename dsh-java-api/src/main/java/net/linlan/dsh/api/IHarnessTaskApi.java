package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IHarnessTaskApi {
    Response<SubmitTaskResponseDTO> submitTask(SubmitTaskRequestDTO request);
}
