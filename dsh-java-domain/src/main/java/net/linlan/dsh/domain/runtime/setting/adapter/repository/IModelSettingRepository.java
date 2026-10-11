package net.linlan.dsh.domain.runtime.setting.adapter.repository;

import net.linlan.dsh.domain.runtime.setting.model.entity.ModelSettingEntity;
import java.util.List;
import java.util.Optional;

/**
 * 模型设置仓储接口。
 * <p>
 * 执行流程：
 * 1. 领域服务发起模型设置读写；
 * 2. 基础设施层负责持久化访问；
 * 3. 返回领域实体或完成写入。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IModelSettingRepository {

    /**
     * 保存模型设置。
     * <p>
     * 执行流程：
     * 1. 接收领域实体；
     * 2. 映射并写入持久层；
     * 3. 完成后返回。
     */
    void save(ModelSettingEntity setting);

    /**
     * 查询全部模型设置。
     * <p>
     * 执行流程：
     * 1. 从持久层读取全部记录；
     * 2. 转换为领域实体；
     * 3. 返回实体列表。
     */
    List<ModelSettingEntity> queryAll();

    /**
     * 按渠道编码查询模型设置。
     * <p>
     * 执行流程：
     * 1. 根据 channelCode 定位记录；
     * 2. 转换为领域实体；
     * 3. 返回可选结果。
     */
    Optional<ModelSettingEntity> findByChannelCode(String channelCode);

    /**
     * 查询当前激活的模型渠道。
     * <p>
     * 执行流程：
     * 1. 根据 active 标记定位记录；
     * 2. 转换为领域实体；
     * 3. 返回可选结果。
     */
    Optional<ModelSettingEntity> findActive();

    /**
     * 将指定渠道置为当前生效项（其他渠道取消激活）。
     * <p>
     * 执行流程：
     * 1. 清除全部行的激活标记；
     * 2. 激活目标行；
     * 3. 返回是否存在目标行。
     */
    boolean activate(String channelCode);

    /**
     * 删除指定模型渠道。
     * <p>
     * 执行流程：
     * 1. 读取 channelCode；
     * 2. 删除持久化记录；
     * 3. 返回删除结果。
     */
    void deleteByChannelCode(String channelCode);
}
