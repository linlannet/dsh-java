package net.linlan.dsh.domain.runtime.setting;

import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.util.List;
import java.util.Optional;

/**
 * 模型设置查询服务接口。
 * <p>
 * 执行流程：
 * 1. 上层请求模型配置；
 * 2. 领域层从仓储中装配视图对象；
 * 3. 返回给运行时路由或前端展示。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IModelSettingQueryService {

    /**
     * 查询全部模型设置。
     * <p>
     * 执行流程：
     * 1. 从仓储读取全部设置；
     * 2. 转成领域视图对象；
     * 3. 返回列表。
     */
    List<ModelSettingVO> queryAll();

    /**
     * 按渠道编码查询模型设置。
     * <p>
     * 执行流程：
     * 1. 读取 channelCode；
     * 2. 定位对应设置；
     * 3. 返回可选值对象。
     */
    Optional<ModelSettingVO> findByChannelCode(String channelCode);

    /**
     * 查询当前激活的模型渠道。
     */
    Optional<ModelSettingVO> findActive();
}
