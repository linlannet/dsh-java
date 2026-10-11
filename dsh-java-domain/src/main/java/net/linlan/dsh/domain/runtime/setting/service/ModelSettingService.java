package net.linlan.dsh.domain.runtime.setting.service;

import net.linlan.dsh.domain.runtime.setting.IModelSettingCommandService;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.adapter.repository.IModelSettingRepository;
import net.linlan.dsh.domain.runtime.setting.model.entity.ModelSettingEntity;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 模型设置领域服务。
 * <p>
 * 执行流程：
 * 1. 从仓储读取或保存模型设置；
 * 2. 将实体转换为值对象；
 * 3. 供查询和命令用例共同使用。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class ModelSettingService implements IModelSettingQueryService, IModelSettingCommandService {

    private final IModelSettingRepository modelSettingRepository;

    /**
     * 处理模型 Setting Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param modelSettingRepository 模型 Setting Repository参数。
     * @return 方法执行结果。
     */
    public ModelSettingService(IModelSettingRepository modelSettingRepository) {
        this.modelSettingRepository = modelSettingRepository;
    }

    /**
     * 查询全部模型设置。
     * <p>
     * 执行流程：
     * 1. 从仓储读取实体列表；
     * 2. 映射为值对象；
     * 3. 返回列表。
     */
    @Override
    public List<ModelSettingVO> queryAll() {
        return modelSettingRepository.queryAll().stream().map(this::toValue).toList();
    }

    /**
     * 按渠道编码查询模型设置。
     * <p>
     * 执行流程：
     * 1. 调用仓储按渠道定位实体；
     * 2. 转换为值对象；
     * 3. 返回可选结果。
     */
    @Override
    public Optional<ModelSettingVO> findByChannelCode(String channelCode) {
        return modelSettingRepository.findByChannelCode(channelCode).map(this::toValue);
    }

    @Override
    public Optional<ModelSettingVO> findActive() {
        return modelSettingRepository.findActive().map(this::toValue);
    }

    /**
     * 保存模型设置。
     * <p>
     * 执行流程：
     * 1. 构造模型设置实体；
     * 2. 若当前尚无激活渠道，则本次保存自动置为当前生效渠道；
     * 3. 写入仓储；
     * 4. 返回保存后的值对象。
     */
    @Override
    public ModelSettingVO save(String channelCode, String displayName, String providerCode, String modelCode,
                               String baseUrl, String apiKeyRef, String protocol, boolean enabled) {
        Optional<ModelSettingEntity> existing = modelSettingRepository.findByChannelCode(channelCode);
        boolean hasActive = modelSettingRepository.queryAll().stream()
                .anyMatch(setting -> setting.isActive() && setting.isEnabled());
        boolean active = enabled && existing.map(setting -> setting.isActive() || !hasActive).orElse(!hasActive);
        ModelSettingEntity entity = ModelSettingEntity.create(
                channelCode, displayName, providerCode, modelCode, baseUrl, apiKeyRef, protocol, enabled, active);
        modelSettingRepository.save(entity);
        if (existing.map(ModelSettingEntity::isActive).orElse(false) && !enabled) {
            modelSettingRepository.queryAll().stream()
                    .filter(ModelSettingEntity::isEnabled)
                    .findFirst()
                    .ifPresent(next -> modelSettingRepository.activate(next.getChannelCode()));
        }
        return toValue(entity);
    }

    /**
     * 删除指定模型渠道。
     * <p>
     * 执行流程：
     * 1. 读取 channelCode；
     * 2. 调用仓储删除；
     * 3. 结束处理。
     */
    @Override
    public void deleteByChannelCode(String channelCode) {
        boolean wasActive = modelSettingRepository.findByChannelCode(channelCode)
                .map(ModelSettingEntity::isActive)
                .orElse(false);
        modelSettingRepository.deleteByChannelCode(channelCode);
        if (!wasActive) {
            return;
        }
        modelSettingRepository.queryAll().stream()
                .filter(ModelSettingEntity::isEnabled)
                .findFirst()
                .ifPresent(next -> modelSettingRepository.activate(next.getChannelCode()));
    }

    /**
     * 切换当前生效模型渠道。
     * <p>
     * 执行流程：
     * 1. 校验目标配置存在；
     * 2. 调用仓储完成激活（同事务先清除全部激活标记）；
     * 3. 返回激活后的值对象。
     */
    @Override
    public ModelSettingVO activate(String channelCode) {
        ModelSettingEntity target = modelSettingRepository.findByChannelCode(channelCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "model setting not found: channel=" + channelCode));
        if (!target.isEnabled()) {
            throw new IllegalArgumentException("model setting is disabled: channel=" + channelCode);
        }
        modelSettingRepository.activate(channelCode);
        return modelSettingRepository.findByChannelCode(target.getChannelCode())
                .map(this::toValue)
                .orElseThrow(() -> new IllegalStateException(
                        "activated model setting disappeared: channel=" + channelCode));
    }

    /**
     * 将实体转换为值对象。
     * <p>
     * 执行流程：
     * 1. 读取实体字段；
     * 2. 封装为 VO；
     * 3. 返回给调用方。
     */
    private ModelSettingVO toValue(ModelSettingEntity entity) {
        return new ModelSettingVO(
                entity.getChannelCode(),
                entity.getDisplayName(),
                entity.getProviderCode(),
                entity.getModelCode(),
                entity.getBaseUrl(),
                entity.getApiKeyRef(),
                entity.getProtocol(),
                entity.isEnabled(),
                entity.isActive(),
                entity.getUpdatedAt()
        );
    }

}
