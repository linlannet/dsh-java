package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.FeishuBotDTO;
import net.linlan.dsh.api.dto.FeishuBotSaveDTO;
import net.linlan.dsh.api.dto.FeishuPairingDTO;
import net.linlan.dsh.api.dto.FeishuPairingStartDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

/**
 * 飞书 Bot 用例接口（配对、CRUD、启停、测试）。
 */
public interface IBotApi {

    /**
     * 发起一键配对。
     *
     * @param request 配对入参
     * @return 配对状态
     */
    Response<FeishuPairingDTO> startPairing(FeishuPairingStartDTO request);

    /**
     * 查询配对状态。
     *
     * @param pairingId 配对会话 ID
     * @return 配对状态
     */
    Response<FeishuPairingDTO> pairingStatus(String pairingId);

    /**
     * 取消配对。
     *
     * @param pairingId 配对会话 ID
     * @return 空响应
     */
    Response<Void> cancelPairing(String pairingId);

    /**
     * 查询全部 Bot。
     *
     * @return Bot 列表
     */
    Response<List<FeishuBotDTO>> listBots();

    /**
     * 保存 Bot（新建或更新）。
     *
     * @param request 保存入参
     * @return 保存后的 Bot
     */
    Response<FeishuBotDTO> saveBot(FeishuBotSaveDTO request);

    /**
     * 更新指定 Bot（本项目扩展方法，强制以路径 botId 为准）。
     *
     * @param botId   Bot ID
     * @param request 保存入参
     * @return 更新后的 Bot
     */
    Response<FeishuBotDTO> updateBot(String botId, FeishuBotSaveDTO request);

    /**
     * 删除 Bot。
     *
     * @param botId Bot ID
     * @return 空响应
     */
    Response<Void> deleteBot(String botId);

    /**
     * 启用 Bot 并建立长连接。
     *
     * @param botId Bot ID
     * @return 启用后的 Bot
     */
    Response<FeishuBotDTO> enableBot(String botId);

    /**
     * 禁用 Bot 并断开长连接。
     *
     * @param botId Bot ID
     * @return 禁用后的 Bot
     */
    Response<FeishuBotDTO> disableBot(String botId);

    /**
     * 测试 Bot 连接。
     *
     * @param botId Bot ID
     * @return 测试后的 Bot
     */
    Response<FeishuBotDTO> testBot(String botId);
}
