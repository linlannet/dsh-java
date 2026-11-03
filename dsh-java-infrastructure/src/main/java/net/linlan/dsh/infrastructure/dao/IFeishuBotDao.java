package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * feishu_bot 表数据访问对象（MyBatis 注解 SQL）。
 */
@Mapper
public interface IFeishuBotDao {

    /**
     * 新增或更新 Bot。
     *
     * @param po 持久化对象
     */
    @Insert(value = {"INSERT INTO feishu_bot (bot_id, bot_name, app_id, secret_ref, enabled, agent_base_id, channel_code, cwd, group_require_mention, created_at, updated_at)\n"
            + "VALUES (#{botId}, #{botName}, #{appId}, #{secretRef}, #{enabled}, #{agentBaseId}, #{channelCode}, #{cwd}, #{groupRequireMention}, #{createdAt}, #{updatedAt})\n"
            + "ON DUPLICATE KEY UPDATE\n"
            + "    bot_name = VALUES(bot_name),\n"
            + "    app_id = VALUES(app_id),\n"
            + "    secret_ref = VALUES(secret_ref),\n"
            + "    enabled = VALUES(enabled),\n"
            + "    agent_base_id = VALUES(agent_base_id),\n"
            + "    channel_code = VALUES(channel_code),\n"
            + "    cwd = VALUES(cwd),\n"
            + "    group_require_mention = VALUES(group_require_mention),\n"
            + "    updated_at = VALUES(updated_at)\n"})
    public void save(FeishuBotPO po);

    /**
     * 按 botId 查询。
     *
     * @param botId Bot ID
     * @return 持久化对象（不存在时为空）
     */
    @Select(value = {"SELECT bot_id, bot_name, app_id, secret_ref, enabled, agent_base_id, channel_code, cwd, group_require_mention, created_at, updated_at\n"
            + "FROM feishu_bot\n"
            + "WHERE bot_id = #{botId}\n"})
    public Optional<FeishuBotPO> findByBotId(@Param(value = "botId") String botId);

    /**
     * 查询全部 Bot。
     *
     * @return 持久化对象列表
     */
    @Select(value = {"SELECT bot_id, bot_name, app_id, secret_ref, enabled, agent_base_id, channel_code, cwd, group_require_mention, created_at, updated_at\n"
            + "FROM feishu_bot\n"
            + "ORDER BY created_at ASC\n"})
    public List<FeishuBotPO> findAll();

    /**
     * 按 botId 删除。
     *
     * @param botId Bot ID
     */
    @Delete(value = {"DELETE FROM feishu_bot WHERE bot_id = #{botId}"})
    public void deleteByBotId(@Param(value = "botId") String botId);
}
