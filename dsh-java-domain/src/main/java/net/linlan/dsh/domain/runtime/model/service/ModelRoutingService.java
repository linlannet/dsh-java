package net.linlan.dsh.domain.runtime.model.service;

import net.linlan.dsh.domain.runtime.IModelRoutingService;
import net.linlan.dsh.domain.runtime.model.adapter.port.IModelProviderPort;
import net.linlan.dsh.domain.runtime.model.model.valobj.ModelProviderProfileVO;
import org.springframework.stereotype.Service;

/**
 * 模型路由服务。
 * <p>
 * 执行流程：
 * 1. 接收可选模型渠道编码；
 * 2. 转交模型提供方端口解析具体模型；
 * 3. 返回模型渠道画像。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class ModelRoutingService implements IModelRoutingService {

    private final IModelProviderPort modelProviderPort;

    /**
     * 处理模型 Routing Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param modelProviderPort 模型 模型提供方 Port参数。
     * @return 方法执行结果。
     */
    public ModelRoutingService(IModelProviderPort modelProviderPort) {
        this.modelProviderPort = modelProviderPort;
    }

    /**
     * 解析对应模型提供方。
     * <p>
     * 执行流程：
     * 1. 读取 channelCode；
     * 2. 调用模型提供方端口；
     * 3. 返回解析结果。
     */
    @Override
    public ModelProviderProfileVO resolve(String channelCode) {
        return modelProviderPort.resolve(channelCode);
    }

}
