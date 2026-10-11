package net.linlan.dsh.domain.runtime;

import net.linlan.dsh.domain.runtime.profile.model.valobj.ExecutionProfileVO;

/**
 * 执行画像查询服务。
 * <p>
 * 执行流程：
 * 1. 接收执行画像编码；
 * 2. 解析对应执行模式；
 * 3. 返回供后续调度使用的执行画像。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IExecutionProfileService {

    /**
     * 解析一个执行画像。
     * <p>
     * 执行流程：
     * 1. 读取 profileCode；
     * 2. 定位对应策略；
     * 3. 返回解析后的执行画像。
     *
     * @param profileCode 执行画像编码
     * @return 解析后的执行画像
     */
    ExecutionProfileVO resolve(String profileCode);
}
