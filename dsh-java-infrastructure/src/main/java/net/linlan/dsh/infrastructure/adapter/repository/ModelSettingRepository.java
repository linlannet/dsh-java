package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.runtime.setting.adapter.repository.IModelSettingRepository;
import net.linlan.dsh.domain.runtime.setting.model.entity.ModelSettingEntity;
import net.linlan.dsh.infrastructure.dao.IHarnessModelSettingDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessModelSettingPO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ModelSettingRepository implements IModelSettingRepository {

    private final IHarnessModelSettingDao harnessModelSettingDao;

    public ModelSettingRepository(IHarnessModelSettingDao harnessModelSettingDao) {
        this.harnessModelSettingDao = harnessModelSettingDao;
    }

    @Override
    @Transactional
    public void save(ModelSettingEntity setting) {
        if (setting.isActive()) {
            harnessModelSettingDao.clearActive();
        }
        harnessModelSettingDao.save(new HarnessModelSettingPO(
                setting.getChannelCode(),
                setting.getDisplayName(),
                setting.getProviderCode(),
                setting.getModelCode(),
                setting.getBaseUrl(),
                setting.getApiKeyRef(),
                setting.getProtocol(),
                setting.isEnabled(),
                setting.isActive(),
                setting.getUpdatedAt()
        ));
    }

    @Override
    public List<ModelSettingEntity> queryAll() {
        return harnessModelSettingDao.queryAll().stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<ModelSettingEntity> findByChannelCode(String channelCode) {
        return harnessModelSettingDao.findByChannelCode(channelCode).map(this::toEntity);
    }

    @Override
    public Optional<ModelSettingEntity> findActive() {
        return harnessModelSettingDao.findActive().map(this::toEntity);
    }

    @Override
    @Transactional
    public boolean activate(String channelCode) {
        harnessModelSettingDao.clearActive();
        return harnessModelSettingDao.activate(channelCode, java.time.Instant.now()) > 0;
    }

    @Override
    @Transactional
    public void deleteByChannelCode(String channelCode) {
        harnessModelSettingDao.deleteByChannelCode(channelCode);
    }

    private ModelSettingEntity toEntity(HarnessModelSettingPO po) {
        return new ModelSettingEntity(
                po.channelCode(),
                po.displayName(),
                po.providerCode(),
                po.modelCode(),
                po.baseUrl(),
                po.apiKeyRef(),
                po.protocol(),
                po.enabled(),
                po.active(),
                po.updatedAt()
        );
    }
}
