package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.api.IHarnessModelDiscoveryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.runtime.IModelDiscoveryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 代理上游模型发现，前端可以同步可用模型并避免浏览器 CORS 限制。
 * <p>
 * 流程：解析协议参数 → 委托模型同步用例按协议分派（openai → /models，
 * anthropic → /v1/models，ollama → /api/tags）→ 包装为统一响应。
 * 任何失败都只返回错误提示，绝不覆盖调用方已有模型列表。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessModelDiscoveryApi implements IHarnessModelDiscoveryApi {

    private final IModelDiscoveryCase modelDiscoveryCase;

    /**
     * 处理Harness 模型 Discovery API。
     * 流程：转换请求 → 调用模型同步用例 → 转换响应。
     *
     * @param modelDiscoveryCase 模型同步用例
     */
    public HarnessModelDiscoveryApi(IModelDiscoveryCase modelDiscoveryCase) {
        this.modelDiscoveryCase = modelDiscoveryCase;
    }

    /**
     * 从上游渠道同步可用模型。
     */
    public Response<List<String>> discover(DiscoverModelsRequestDTO request) {
        return execute(() -> modelDiscoveryCase.syncModels(
                request.baseUrl(), request.apiKeyRef(), request.protocol()));
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
