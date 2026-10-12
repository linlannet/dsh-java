package net.linlan.dsh.infrastructure.adapter.lsp;

import net.linlan.dsh.domain.lsp.adapter.port.ILspProviderPort;
import net.linlan.dsh.domain.lsp.model.valobj.LspLocation;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;
import net.linlan.dsh.domain.lsp.stdio.LspStdioProvider;
import java.io.IOException;
import java.util.List;

/**
 * StdioLspProvider 表示通过 stdio 启动 LSP server 的基础设施适配器。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class StdioLspProvider implements ILspProviderPort {

    private final LspStdioProvider delegate;
    private final String serverCommand;

    /**
     * 处理Stdio Lsp Provider。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param serverCommand server Command参数。
     * @return 方法执行结果。
     */
    public StdioLspProvider(String serverCommand) {
        this.serverCommand = serverCommand;
        this.delegate = new LspStdioProvider();
    }

    /**
     * 处理start。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public void start() throws IOException {
        delegate.initialize(serverCommand);
    }

    @Override
    public LspQueryResult query(LspQueryType queryType, String filePath, String fileContent,
                                int line, int character) {
        if (!delegate.isInitialized()) {
            try {
                delegate.initialize(serverCommand);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize LSP server: " + e.getMessage(), e);
            }
        }
        return switch (queryType) {
            case DEFINITION -> {
                var loc = delegate.definition(filePath, line, character);
                yield loc.map(l -> LspQueryResult.ofLocations(List.of(l)))
                        .orElse(LspQueryResult.ofLocations(List.of()));
            }
            case REFERENCES -> LspQueryResult.ofLocations(delegate.references(filePath, line, character));
            case IMPLEMENTATION -> {
                var loc = delegate.definition(filePath, line, character);
                yield loc.map(l -> LspQueryResult.ofLocations(List.of(l)))
                        .orElse(LspQueryResult.ofLocations(List.of()));
            }
            case HOVER -> {
                List<String> diags = delegate.diagnostics(filePath);
                yield LspQueryResult.ofHover(String.join("\n", diags));
            }
        };
    }

    /**
     * 返回delegate。
     * @return 方法执行结果。
     */
    public LspStdioProvider getDelegate() { return delegate; }
}
