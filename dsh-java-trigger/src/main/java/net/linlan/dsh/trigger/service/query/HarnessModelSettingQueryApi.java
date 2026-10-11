package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.ModelSettingResponseDTO;
import net.linlan.dsh.api.IHarnessModelSettingQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.runtime.IModelSettingQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型设置查询适配器。
 * <p>
 * 执行流程：
 * 1. 调用领域查询服务读取模型设置 VO；
 * 2. 将 VO 映射为接口返回的响应对象；
 * 3. 返回给 Trigger 层直接输出。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessModelSettingQueryApi implements IHarnessModelSettingQueryApi {

    private final IModelSettingQueryCase modelSettingQueryCase;

    /**
     * 处理Harness 模型 Setting Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param modelSettingQueryService 模型 Setting Query Service参数。
     * @return 方法执行结果。
     */
    public HarnessModelSettingQueryApi(IModelSettingQueryCase modelSettingQueryCase) {
        this.modelSettingQueryCase = modelSettingQueryCase;
    }

    /**
     * 查询全部模型设置。
     * <p>
     * 执行流程：
     * 1. 拉取领域层的模型设置列表；
     * 2. 逐条转换为 `ModelSettingResponseDTO`；
     * 3. 返回给上层控制器。
     */
    public Response<List<ModelSettingResponseDTO>> queryAll() {
        return execute(() -> modelSettingQueryCase.listSettings().stream()
                .map(setting -> new ModelSettingResponseDTO(
                        setting.channelCode(), setting.displayName(), setting.providerCode(), setting.modelCode(),
                        setting.baseUrl(), setting.apiKeyRef(), setting.protocol(),
                        setting.enabled(), setting.active(), setting.updatedAt()
                ))
                .toList());
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
