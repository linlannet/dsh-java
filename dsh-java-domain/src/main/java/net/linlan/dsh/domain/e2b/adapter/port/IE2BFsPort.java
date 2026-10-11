package net.linlan.dsh.domain.e2b.adapter.port;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Port for E2B filesystem operations within a sandbox.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IE2BFsPort {

    /**
     * 处理readFile。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    String readFile(String sandboxId, String path);

    /**
     * 处理writeFile。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @param content 内容。
     * @return 领域处理结果。
     */
    void writeFile(String sandboxId, String path, String content);

    /**
     * 处理listDir。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    List<String> listDir(String sandboxId, String dir);

    /**
     * 处理delete。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    void delete(String sandboxId, String path);
}
