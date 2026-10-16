package net.linlan.dsh.domain.session.search.adapter.port;

import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;

import java.time.Instant;
import java.util.List;

/**
 * ISessionSearchIndexPort 是全文索引的出站端口。
 * <p>领域层定义此接口，基础设施层提供具体实现（如 Lucene 嵌入式索引）。
 */
public interface ISessionSearchIndexPort extends AutoCloseable {

    void index(String sessionId, long seq, String content);

    List<SearchHit> search(String query, int limit);

    void clear();

    void remove(String sessionId);

    void removeOlderThan(Instant threshold);

    void removeOldest(int count);

    int documentCount();

    @Override
    default void close() {}
}
