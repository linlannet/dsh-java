package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessModelSettingQueryApi {

    Response<List<ModelSettingResponseDTO>> queryAll();
}
