package net.linlan.dsh.domain.tool.mcp.resource.model.valobj;

/**
 * McpResource 表示一个MCP资源描述。
 *
 * @param uri 资源URI
 * @param name 资源名称
 * @param description 资源描述
 * @param scope 资源作用域
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record McpResource(String uri, String name, String description, String scope) {}
