package net.linlan.dsh.domain.bot.adapter.repository;

import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import java.util.List;
import java.util.Optional;

/**
 * 飞书 Bot 仓储端口（由基础设施层适配）。
 */
public interface IFeishuBotRepository {

    /**
     * 保存或更新 Bot（upsert）。
     *
     * @param bot Bot 聚合
     */
    void save(FeishuBotAggregate bot);

    /**
     * 按 botId 查询。
     *
     * @param botId Bot ID
     * @return Bot 聚合（不存在时为空）
     */
    Optional<FeishuBotAggregate> findByBotId(String botId);

    /**
     * 查询全部 Bot。
     *
     * @return Bot 聚合列表
     */
    List<FeishuBotAggregate> findAll();

    /**
     * 按 botId 删除。
     *
     * @param botId Bot ID
     */
    void deleteByBotId(String botId);
}
