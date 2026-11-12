package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.IMiniAppApi;
import net.linlan.dsh.api.dto.MiniAppLoginRequestDTO;
import net.linlan.dsh.api.dto.MiniAppLoginResponseDTO;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序登录 REST 触发层。
 */
@RestController
@RequestMapping(value = {"/api/miniapp"})
public class MiniAppController {

    private final IMiniAppApi miniAppApi;

    /**
     * 构造控制器。
     *
     * @param miniAppApi 小程序用例接口
     */
    public MiniAppController(IMiniAppApi miniAppApi) {
        this.miniAppApi = miniAppApi;
    }

    /**
     * 用 wx.login 的 code 换取短期会话令牌。
     *
     * @param request 登录请求
     * @return 会话令牌
     */
    @PostMapping(value = {"/login"})
    public Response<MiniAppLoginResponseDTO> login(@RequestBody(required = false) MiniAppLoginRequestDTO request) {
        return this.miniAppApi.login(request == null ? new MiniAppLoginRequestDTO(null) : request);
    }
}