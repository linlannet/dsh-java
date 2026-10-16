package net.linlan.dsh.domain.api.gateway.controller;

import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * ControllerRegistry 管理所有 Remote 控制器 namespace。
 *
 * <p>控制器 namespace 缺失时返回可操作的配置错误，不影响其他 namespace。</p>
 *
 */
@Service
public class ControllerRegistry {

    private final Map<String, IRemoteController> controllers = new ConcurrentHashMap<>();

    public ControllerRegistry(List<IRemoteController> controllerList) {
        for (IRemoteController controller : controllerList) {
            controllers.put(controller.namespace(), controller);
        }
    }

    public Object invoke(String namespace, String method, Map<String, Object> args, PeerScope peer) {
        Objects.requireNonNull(namespace, "namespace 不能为空");
        IRemoteController controller = controllers.get(namespace);
        if (controller == null) {
            throw new ControllerNotFoundException("控制器 namespace 不存在: " + namespace + "，请检查配置");
        }
        return controller.invoke(method, args, peer);
    }

    public boolean hasNamespace(String namespace) {
        return controllers.containsKey(namespace);
    }

    public List<String> namespaces() {
        return List.copyOf(controllers.keySet());
    }

    public static class ControllerNotFoundException extends RuntimeException {
        public ControllerNotFoundException(String message) {
            super(message);
        }
    }
}
