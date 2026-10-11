package net.linlan.dsh.domain.lsp.adapter.port;

import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;

/**
 * Port for one LSP provider that serves transient-open queries.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ILspProviderPort {

    /**
     * 处理query。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param queryType queryType类型。
     * @param fileContent fileContent内容。
     * @param character character。
     * @return 领域处理结果。
     */
    LspQueryResult query(LspQueryType queryType, String filePath, String fileContent, int line, int character);
}
