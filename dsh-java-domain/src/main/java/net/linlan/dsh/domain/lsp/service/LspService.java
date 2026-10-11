package net.linlan.dsh.domain.lsp.service;

import net.linlan.dsh.domain.lsp.ILspService;
import net.linlan.dsh.domain.lsp.adapter.port.ILspProviderPort;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * LSP service: routes queries to the appropriate provider by file extension.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class LspService implements ILspService {

    private final Map<String, ILspProviderPort> providersByExtension = new HashMap<>();

    /**
     * 注册 模型提供方。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fileExtension 文件 Extension参数。
     * @param provider 模型提供方。
     */
    public void registerProvider(String fileExtension, ILspProviderPort provider) {
        providersByExtension.put(fileExtension, provider);
    }

    @Override
    public Optional<LspQueryResult> query(LspQueryType queryType, String filePath, String fileContent,
                                          int line, int character) {
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String ext = filePath.substring(dot);
        ILspProviderPort provider = providersByExtension.get(ext);
        if (provider == null) return Optional.empty();
        try {
            return Optional.of(provider.query(queryType, filePath, fileContent, line, character));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
