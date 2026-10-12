package net.linlan.dsh.domain.tool.web.search.model.valobj;

/**
 * SearchResult 表示一条Web搜索结果。
 *
 * @param title 标题
 * @param url 链接
 * @param snippet 摘要
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record SearchResult(String title, String url, String snippet) {}
