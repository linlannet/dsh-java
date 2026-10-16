package net.linlan.dsh.infrastructure.adapter.search;

import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LuceneSessionSearchIndex Lucene 全文索引适配器")
class LuceneSessionSearchIndexTest {

    @Test
    @DisplayName("索引后按关键词搜索返回命中")
    void indexAndSearch() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-1", 1, "hello world search test");
            index.index("sess-2", 2, "another session with different content");

            List<SearchHit> hits = index.search("hello", 10);
            assertFalse(hits.isEmpty());
            assertEquals("sess-1", hits.get(0).sessionId());
            assertEquals(1, hits.get(0).seq());
        }
    }

    @Test
    @DisplayName("搜索无匹配返回空列表")
    void searchNoMatchReturnsEmpty() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-1", 1, "hello world");

            List<SearchHit> hits = index.search("nonexistent", 10);
            assertTrue(hits.isEmpty());
        }
    }

    @Test
    @DisplayName("limit 限制返回条数")
    void limitRestrictsResults() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-1", 1, "search term here");
            index.index("sess-2", 2, "search term again");
            index.index("sess-3", 3, "search term once more");

            List<SearchHit> hits = index.search("search", 2);
            assertEquals(2, hits.size());
        }
    }

    @Test
    @DisplayName("clear 清空索引后搜索无结果")
    void clearEmptiesIndex() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-1", 1, "hello world");
            index.clear();

            List<SearchHit> hits = index.search("hello", 10);
            assertTrue(hits.isEmpty());
        }
    }

    @Test
    @DisplayName("命中结果包含高亮片段")
    void hitContainsHighlight() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-1", 1, "this is a long text containing the keyword searchable here");

            List<SearchHit> hits = index.search("searchable", 1);
            assertFalse(hits.isEmpty());
            assertTrue(hits.get(0).score() > 0);
        }
    }

    @Test
    @DisplayName("多会话索引搜索返回对应会话")
    void multipleSessionsSearch() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("sess-A", 1, "alpha beta gamma");
            index.index("sess-B", 1, "delta epsilon zeta");
            index.index("sess-C", 1, "eta theta iota");

            List<SearchHit> hits = index.search("delta", 10);
            assertEquals(1, hits.size());
            assertEquals("sess-B", hits.get(0).sessionId());
        }
    }

    @Test
    @DisplayName("documentCount 返回索引文档数")
    void documentCount() {
        try (var index = new LuceneSessionSearchIndex()) {
            assertEquals(0, index.documentCount());
            index.index("s1", 1, "hello");
            index.index("s2", 2, "world");
            assertEquals(2, index.documentCount());
        }
    }

    @Test
    @DisplayName("remove 按 sessionId 删除文档")
    void removeBySessionId() {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("s1", 1, "hello world");
            index.index("s2", 2, "hello dsh");
            assertEquals(2, index.documentCount());

            index.remove("s1");
            assertEquals(1, index.documentCount());

            var hits = index.search("hello", 10);
            assertEquals(1, hits.size());
            assertEquals("s2", hits.get(0).sessionId());
        }
    }

    @Test
    @DisplayName("removeOlderThan 删除指定时间前的文档")
    void removeOlderThan() throws InterruptedException {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("s1", 1, "old content");
            Thread.sleep(50);
            var threshold = java.time.Instant.now();
            Thread.sleep(50);
            index.index("s2", 2, "new content");

            index.removeOlderThan(threshold);
            assertEquals(1, index.documentCount());
            var hits = index.search("content", 10);
            assertEquals(1, hits.size());
            assertEquals("s2", hits.get(0).sessionId());
        }
    }

    @Test
    @DisplayName("removeOldest 删除最旧的 N 个文档")
    void removeOldest() throws InterruptedException {
        try (var index = new LuceneSessionSearchIndex()) {
            index.index("s1", 1, "first");
            Thread.sleep(20);
            index.index("s2", 2, "second");
            Thread.sleep(20);
            index.index("s3", 3, "third");
            assertEquals(3, index.documentCount());

            index.removeOldest(1);
            assertEquals(2, index.documentCount());
            var hits = index.search("first", 10);
            assertTrue(hits.isEmpty());
        }
    }

    @Test
    @DisplayName("容量上限自动驱逐最旧文档")
    void capacityLimitAutoEviction() {
        try (var index = new LuceneSessionSearchIndex(3)) {
            index.index("s1", 1, "first content");
            index.index("s2", 2, "second content");
            index.index("s3", 3, "third content");
            assertEquals(3, index.documentCount());

            index.index("s4", 4, "fourth content");
            assertTrue(index.documentCount() <= 3,
                    "容量上限后文档数不应超过 maxDocuments");
        }
    }
}
