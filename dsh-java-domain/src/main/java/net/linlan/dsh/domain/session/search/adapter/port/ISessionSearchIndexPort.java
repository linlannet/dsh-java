package net.linlan.dsh.domain.session.search.adapter.port;

import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;

import java.util.List;

/**
 * ISessionSearchIndexPort 是全文索引的出站端口。
 * <p>领域层定义此接口，基础设施层提供具体实现（如 Lucene 嵌入式索引）。
 */
public interface ISessionSearchIndexPort extends AutoCloseable {

    /**
     * 索引一段会话内容。
     *
     * @param sessionId 会话 ID
     * @param seq       事件序列号
     * @param content   待索引的文本内容
     */
    void index(String sessionId, long seq, String content);

    /**
     * 搜索匹配的会话片段。
     *
     * @param query 查询关键词
     * @param limit 最大返回条数
     * @return 匹配的搜索命中列表
     */
    List<SearchHit> search(String query, int limit);

    /**
     * 清空索引。
     */
    void clear();

    @Override
    default void close() {}
}
