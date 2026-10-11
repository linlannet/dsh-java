package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.AvailableModelResponseDTO;
import net.linlan.dsh.api.IHarnessRuntimeModelQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.runtime.IModelSettingQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 运行时模型查询适配器。
 * <p>
 * 执行流程：
 * 1. 读取已启用的模型渠道；
 * 2. 保留渠道、provider 与模型的对应关系；
 * 3. 汇总为前端可展示的渠道清单。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessRuntimeModelQueryApi implements IHarnessRuntimeModelQueryApi {

    private final IModelSettingQueryCase modelSettingQueryCase;

    /**
     * 处理Harness 运行时 模型 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param modelSettingQueryService 模型 Setting Query Service参数。
     * @return 方法执行结果。
     */
    public HarnessRuntimeModelQueryApi(IModelSettingQueryCase modelSettingQueryCase) {
        this.modelSettingQueryCase = modelSettingQueryCase;
    }

    /**
     * 查询当前运行时可用模型。
     * <p>
     * 执行流程：
     * 1. 读取模型设置中已启用的渠道；
     * 2. 返回每个渠道绑定的 provider 与模型；
     * 3. 由前端使用 channelCode 作为选择值。
     */
    public Response<List<AvailableModelResponseDTO>> queryAll() {
        return execute(() -> modelSettingQueryCase.listAvailableModels().stream()
                .map(model -> new AvailableModelResponseDTO(
                        model.channelCode(), model.displayName(), model.providerCode(), model.modelCode()
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
