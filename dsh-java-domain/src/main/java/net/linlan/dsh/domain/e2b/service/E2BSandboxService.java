package net.linlan.dsh.domain.e2b.service;

import net.linlan.dsh.domain.e2b.IE2BSandboxService;
import net.linlan.dsh.domain.e2b.adapter.port.IE2BFsPort;
import net.linlan.dsh.domain.e2b.adapter.port.IE2BSubprocessPort;
import net.linlan.dsh.domain.e2b.model.valobj.E2BSandboxEnv;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * E2B sandbox service: manages sandbox lifecycle and routes to per-sandbox ports.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class E2BSandboxService implements IE2BSandboxService {

    private final ConcurrentHashMap<String, E2BSandboxEnv> sandboxes = new ConcurrentHashMap<>();
    private final IE2BFsPort fsPort;
    private final IE2BSubprocessPort subprocessPort;

    /**
     * 处理E2 B Sandbox Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsPort fs Port参数。
     * @param subprocessPort subprocess Port参数。
     * @return 方法执行结果。
     */
    public E2BSandboxService(IE2BFsPort fsPort, IE2BSubprocessPort subprocessPort) {
        this.fsPort = fsPort;
        this.subprocessPort = subprocessPort;
    }
    /**
     * 创建。
     */

    @Override
    public String create(E2BSandboxEnv env) {
        String id = "e2b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        sandboxes.put(id, env);
        return id;
    }

    @Override
    public Optional<IE2BFsPort> fs(String sandboxId) {
        return sandboxes.containsKey(sandboxId) ? Optional.of(fsPort) : Optional.empty();
    }
    /**
     * subprocess。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sandboxId sandbox Id参数。
     * @return 方法执行结果。
     */


    @Override
    public Optional<IE2BSubprocessPort> subprocess(String sandboxId) {
        return sandboxes.containsKey(sandboxId) ? Optional.of(subprocessPort) : Optional.empty();
    }
    /**
     * kill。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sandboxId sandbox Id参数。
     */


    @Override
    public void kill(String sandboxId) {
        sandboxes.remove(sandboxId);
    }
}
