package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.ActivateModelSettingRequestDTO;
import net.linlan.dsh.api.dto.DeleteModelSettingRequestDTO;
import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.dto.SaveModelSettingRequestDTO;
import net.linlan.dsh.api.IHarnessModelSettingCommandApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.runtime.IModelSettingCommandCase;
import org.springframework.stereotype.Service;

@Service
public class HarnessModelSettingCommandApi implements IHarnessModelSettingCommandApi {
    private final IModelSettingCommandCase modelSettingCommandCase;

    public HarnessModelSettingCommandApi(IModelSettingCommandCase modelSettingCommandCase) {
        this.modelSettingCommandCase = modelSettingCommandCase;
    }

    public Response<ModelSettingResponseDTO> save(SaveModelSettingRequestDTO request) {
        return execute(() -> {
            var setting = modelSettingCommandCase.save(new SaveModelSettingRequestDTO(
                    request.channelCode(), request.displayName(), request.providerCode(), request.modelCode(),
                    request.baseUrl(), request.apiKeyRef(), request.protocol(), request.enabled()
            ));
            return toResponse(setting);
        });
    }

    public Response<Void> delete(String channelCode) {
        return execute(() -> {
            modelSettingCommandCase.delete(new DeleteModelSettingRequestDTO(channelCode));
            return null;
        });
    }

    public Response<ModelSettingResponseDTO> activate(ActivateModelSettingRequestDTO request) {
        return execute(() -> {
            var setting = modelSettingCommandCase.activate(request);
            return toResponse(setting);
        });
    }

    private ModelSettingResponseDTO toResponse(ModelSettingResponseDTO setting) {
        return new ModelSettingResponseDTO(
                setting.channelCode(), setting.displayName(), setting.providerCode(), setting.modelCode(),
                setting.baseUrl(), setting.apiKeyRef(), setting.protocol(),
                setting.enabled(), setting.active(), setting.updatedAt()
        );
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
