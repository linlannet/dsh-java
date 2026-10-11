package net.linlan.dsh.domain.llm.adapter.port;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import java.util.concurrent.Flow;

/**
 * 大模型运行时端口。
 * <p>
 * 执行流程：
 * 1. 维护 provider 与适配器的注册关系；
 * 2. 向上层暴露可用模型与路由能力；
 * 3. 统一提供流式响应入口。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ILlmRuntimePort {

    void registerAdapter(String provider, LlmAdapter adapter);

    void unregisterAdapter(String provider);

    /**
     * 判断 provider 是否已注册适配器。
     *
     * @param provider 模型提供方编码
     * @return 已注册返回 true
     */
    default boolean hasAdapter(String provider) {
        return false;
    }

    java.util.List<LlmAdapter.LlmProviderInfo> listProviders();

    java.util.List<LlmAdapter.LlmModelInfo> listModels(String provider);

    LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model);

    Flow.Publisher<StreamChunk> stream(GenerateOptions options);
}
