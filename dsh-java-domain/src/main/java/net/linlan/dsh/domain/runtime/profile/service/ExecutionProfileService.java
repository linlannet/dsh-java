package net.linlan.dsh.domain.runtime.profile.service;

import net.linlan.dsh.domain.runtime.IExecutionProfileService;
import net.linlan.dsh.domain.runtime.profile.model.valobj.ExecutionProfileVO;
import net.linlan.dsh.domain.runtime.profile.service.router.ExecutionProfileStrategy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 执行画像解析服务。
 * <p>
 * 执行流程：
 * 1. 初始化时收集所有画像策略；
 * 2. 运行时根据画像编码定位策略；
 * 3. 输出对应的执行模式。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class ExecutionProfileService implements IExecutionProfileService {

    private final Map<String, ExecutionProfileStrategy> profileStrategies;

    /**
     * 处理Execution Profile Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param profileStrategies profile Strategies参数。
     * @return 方法执行结果。
     */
    public ExecutionProfileService(List<ExecutionProfileStrategy> profileStrategies) {
        this.profileStrategies = indexStrategies(profileStrategies);
    }

    /**
     * 解析执行画像。
     * <p>
     * 执行流程：
     * 1. 根据 profileCode 查找策略；
     * 2. 找不到则抛出异常；
     * 3. 找到后封装为执行画像值对象。
     */
    @Override
    public ExecutionProfileVO resolve(String profileCode) {
        ExecutionProfileStrategy strategy = profileStrategies.get(profileCode);
        if (strategy == null) {
            throw new IllegalStateException("unsupported profileCode: " + profileCode);
        }
        return new ExecutionProfileVO(strategy.profileCode(), strategy.executionMode());
    }

    /**
     * 处理index Strategies。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param strategies strategies参数。
     * @return 方法执行结果。
     */
    private Map<String, ExecutionProfileStrategy> indexStrategies(List<ExecutionProfileStrategy> strategies) {
        Map<String, ExecutionProfileStrategy> indexedStrategies = new LinkedHashMap<>();
        for (ExecutionProfileStrategy strategy : strategies) {
            indexedStrategies.put(strategy.profileCode(), strategy);
        }
        return Map.copyOf(indexedStrategies);
    }
}
