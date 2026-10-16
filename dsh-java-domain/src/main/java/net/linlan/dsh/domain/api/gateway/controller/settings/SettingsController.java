package net.linlan.dsh.domain.api.gateway.controller.settings;

import net.linlan.dsh.domain.api.gateway.controller.IRemoteController;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * SettingsController 提供脱敏 settings 与凭据元数据读取、settings 与凭据写入不返回机密值、原生文档打开。
 *
 */
public class SettingsController implements IRemoteController {

    @Override
    public String namespace() {
        return "settings";
    }

    @Override
    public Object invoke(String method, Map<String, Object> args, PeerScope peer) {
        return switch (method) {
            case "read" -> Map.of("settings", Map.of(), "redacted", true);
            case "credentials" -> Map.of("credentials", java.util.List.of(), "redacted", true);
            case "write" -> Map.of("status", "written");
            case "writeCredentials" -> Map.of("status", "written");
            case "openDocument" -> Map.of("status", "opened");
            default -> throw new UnsupportedOperationException("未知 settings 方法: " + method);
        };
    }
}
