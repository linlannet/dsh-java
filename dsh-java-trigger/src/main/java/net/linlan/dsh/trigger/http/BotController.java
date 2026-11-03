package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.IBotApi;
import net.linlan.dsh.api.condition.FeishuEnabledCondition;
import net.linlan.dsh.api.dto.FeishuBotDTO;
import net.linlan.dsh.api.dto.FeishuBotSaveDTO;
import net.linlan.dsh.api.dto.FeishuPairingDTO;
import net.linlan.dsh.api.dto.FeishuPairingStartDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书 Bot 管理 REST 触发层。
 *
 * <p>自动纳入既有 {@code ApiKeyAuthInterceptor}（{@code /**}）鉴权范围。
 */
@RestController
@RequestMapping(value = {"/api/harness/bots"})
@Conditional(FeishuEnabledCondition.class)
public class BotController {

    private final IBotApi botApi;

    /**
     * 构造控制器。
     *
     * @param botApi 飞书 Bot 用例接口
     */
    public BotController(IBotApi botApi) {
        this.botApi = botApi;
    }

    /**
     * 查询全部 Bot。
     *
     * @return Bot 列表
     */
    @GetMapping
    public Response<List<FeishuBotDTO>> list() {
        return this.botApi.listBots();
    }

    /**
     * 发起一键配对。
     *
     * @param request 配对入参
     * @return 配对状态
     */
    @PostMapping(value = {"/pairing"})
    public Response<FeishuPairingDTO> startPairing(@RequestBody(required = false) FeishuPairingStartDTO request) {
        return this.botApi.startPairing(request == null ? new FeishuPairingStartDTO(null, null) : request);
    }

    /**
     * 查询配对状态。
     *
     * @param pairingId 配对会话 ID
     * @return 配对状态
     */
    @GetMapping(value = {"/pairing/{pairingId}"})
    public Response<FeishuPairingDTO> pairingStatus(@PathVariable(value = "pairingId") String pairingId) {
        return this.botApi.pairingStatus(pairingId);
    }

    /**
     * 取消配对。
     *
     * @param pairingId 配对会话 ID
     * @return 空响应
     */
    @DeleteMapping(value = {"/pairing/{pairingId}"})
    public Response<Void> cancelPairing(@PathVariable(value = "pairingId") String pairingId) {
        return this.botApi.cancelPairing(pairingId);
    }

    /**
     * 新建 Bot。
     *
     * @param request 保存入参
     * @return 保存后的 Bot
     */
    @PostMapping
    public Response<FeishuBotDTO> save(@RequestBody FeishuBotSaveDTO request) {
        return this.botApi.saveBot(request);
    }

    /**
     * 更新 Bot。
     *
     * @param botId   Bot ID
     * @param request 保存入参
     * @return 更新后的 Bot
     */
    @PutMapping(value = {"/{botId}"})
    public Response<FeishuBotDTO> update(@PathVariable(value = "botId") String botId,
                                         @RequestBody FeishuBotSaveDTO request) {
        return this.botApi.updateBot(botId, request);
    }

    /**
     * 删除 Bot。
     *
     * @param botId Bot ID
     * @return 空响应
     */
    @DeleteMapping(value = {"/{botId}"})
    public Response<Void> delete(@PathVariable(value = "botId") String botId) {
        return this.botApi.deleteBot(botId);
    }

    /**
     * 启用 Bot。
     *
     * @param botId Bot ID
     * @return 启用后的 Bot
     */
    @PostMapping(value = {"/{botId}/enable"})
    public Response<FeishuBotDTO> enable(@PathVariable(value = "botId") String botId) {
        return this.botApi.enableBot(botId);
    }

    /**
     * 禁用 Bot。
     *
     * @param botId Bot ID
     * @return 禁用后的 Bot
     */
    @PostMapping(value = {"/{botId}/disable"})
    public Response<FeishuBotDTO> disable(@PathVariable(value = "botId") String botId) {
        return this.botApi.disableBot(botId);
    }

    /**
     * 测试 Bot 连接。
     *
     * @param botId Bot ID
     * @return 测试后的 Bot
     */
    @PostMapping(value = {"/{botId}/test"})
    public Response<FeishuBotDTO> test(@PathVariable(value = "botId") String botId) {
        return this.botApi.testBot(botId);
    }
}