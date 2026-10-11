package net.linlan.dsh.domain.runtime.model.adapter.port;

import net.linlan.dsh.domain.runtime.model.model.valobj.ModelProviderProfileVO;

/**
 * 模型提供方端口。
 * <p>
 * 执行流程：
 * 1. 领域层提交可选模型渠道编码；
 * 2. 基础设施层定位具体模型渠道；
 * 3. 返回模型渠道画像。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IModelProviderPort {

    /**
     * 解析模型渠道对应的模型提供方画像。
     * <p>
     * 执行流程：
     * 1. 读取 channelCode；
     * 2. 调用具体适配器完成路由；
     * 3. 返回模型渠道画像。
     *
     * @param channelCode 模型渠道编码；为空时使用当前激活渠道
     * @return 解析后的模型渠道画像
     */
    ModelProviderProfileVO resolve(String channelCode);

}
