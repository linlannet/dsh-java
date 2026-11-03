package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 飞书 Bot 仓储适配器：委托 {@link IFeishuBotDao}，完成 PO 与聚合的双向映射。
 */
public class FeishuBotRepository implements IFeishuBotRepository {

    private final IFeishuBotDao feishuBotDao;

    /**
     * 构造仓储适配器。
     *
     * @param feishuBotDao 数据访问对象
     */
    public FeishuBotRepository(IFeishuBotDao feishuBotDao) {
        this.feishuBotDao = feishuBotDao;
    }

    @Override
    public void save(FeishuBotAggregate bot) {
        this.feishuBotDao.save(new FeishuBotPO(bot.getBotId(), bot.getBotName(), bot.getAppId(), bot.getSecretRef(),
                bot.isEnabled() ? 1 : 0, bot.getAgentBaseId(), bot.getChannelCode(), bot.getCwd(),
                bot.isGroupRequireMention() ? 1 : 0,
                bot.getCreatedAt() == null ? Instant.now() : bot.getCreatedAt(), Instant.now()));
    }

    @Override
    public Optional<FeishuBotAggregate> findByBotId(String botId) {
        return this.feishuBotDao.findByBotId(botId).map(this::toAggregate);
    }

    @Override
    public List<FeishuBotAggregate> findAll() {
        return this.feishuBotDao.findAll().stream().map(this::toAggregate).toList();
    }

    @Override
    public void deleteByBotId(String botId) {
        this.feishuBotDao.deleteByBotId(botId);
    }

    /**
     * PO 转聚合（运行态 connectionState/lastError 不落库）。
     *
     * @param po 持久化对象
     * @return Bot 聚合
     */
    private FeishuBotAggregate toAggregate(FeishuBotPO po) {
        FeishuBotAggregate aggregate = new FeishuBotAggregate(po.botId(), po.botName(), po.appId(), po.secretRef(),
                po.enabled() != null && po.enabled() == 1, po.agentBaseId(), po.channelCode(), po.cwd(),
                po.groupRequireMention() == null || po.groupRequireMention() == 1, po.createdAt(), po.updatedAt());
        if (!aggregate.isEnabled()) {
            aggregate.onStopped();
        }
        return aggregate;
    }
}
