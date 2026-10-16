package net.linlan.dsh.domain.api.gateway.controller;

import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.Map;

/**
 * IRemoteController 表示一个 Remote 控制器 namespace 的契约。
 *
 * <p>每个控制器负责一个 namespace 的方法分派，返回 JSON 结果。</p>
 *
 */
public interface IRemoteController {

    String namespace();

    Object invoke(String method, Map<String, Object> args, PeerScope peer);
}
