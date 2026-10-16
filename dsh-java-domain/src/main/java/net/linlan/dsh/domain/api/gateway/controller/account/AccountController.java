package net.linlan.dsh.domain.api.gateway.controller.account;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * AccountController 提供登录状态快照流，不返回 token 或 PKCE 私密数据。
 *
 */
public class AccountController implements IRemoteController {

    @Override
    public String namespace() {
        return "account";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "status" -> Map.of("loggedIn", false, "redacted", true);
            case "snapshot" -> Map.of("snapshot", Map.of(), "redacted", true);
            default -> throw new UnsupportedOperationException("未知 account 方法: " + method);
        };
    }
}
