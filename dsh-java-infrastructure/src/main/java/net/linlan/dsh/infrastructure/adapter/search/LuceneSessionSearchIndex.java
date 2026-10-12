package net.linlan.dsh.infrastructure.adapter.search;

import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;
import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * LuceneSessionSearchIndex 是基于 Apache Lucene 的嵌入式全文索引适配器。
 * <p>实现 {@link ISessionSearchIndexPort}，使用内存目录（{@link ByteBuffersDirectory}）存储索引。
 */
public final class LuceneSessionSearchIndex implements ISessionSearchIndexPort {

    private static final String F_SESSION_ID = "sessionId";
    private static final String F_SEQ = "seq";
    private static final String F_CONTENT = "content";

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;

    public LuceneSessionSearchIndex() {
        this(new ByteBuffersDirectory(), new StandardAnalyzer());
    }

    LuceneSessionSearchIndex(Directory directory, Analyzer analyzer) {
        this.directory = directory;
        this.analyzer = analyzer;
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Lucene IndexWriter", e);
        }
    }

    @Override
    public void index(String sessionId, long seq, String content) {
        try {
            Document doc = new Document();
            doc.add(new StringField(F_SESSION_ID, sessionId, Field.Store.YES));
            doc.add(new LongField(F_SEQ, seq, Field.Store.YES));
            doc.add(new TextField(F_CONTENT, content, Field.Store.YES));
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to index document", e);
        }
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        try {
            writer.commit();
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                QueryParser parser = new QueryParser(F_CONTENT, analyzer);
                Query parsed = parser.parse(query);

                TopDocs topDocs = searcher.search(parsed, Math.max(limit, 1));
                Highlighter highlighter = new Highlighter(
                        new SimpleHTMLFormatter("<b>", "</b>"), new QueryScorer(parsed));

                List<SearchHit> hits = new ArrayList<>();
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = searcher.doc(sd.doc);
                    String sessionId = doc.get(F_SESSION_ID);
                    long seq = doc.getField(F_SEQ).numericValue().longValue();
                    String content = doc.get(F_CONTENT);

                    String fragment = highlighter.getBestFragment(analyzer, F_CONTENT, content);
                    List<String> highlights = fragment != null
                            ? List.of(fragment)
                            : List.of();

                    hits.add(new SearchHit(sessionId, seq, content, highlights, sd.score));
                }
                return hits;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Search failed: " + query, e);
        }
    }

    @Override
    public void clear() {
        try {
            writer.deleteAll();
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clear index", e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close IndexWriter", e);
        } finally {
            try {
                directory.close();
            } catch (IOException ignored) {}
            analyzer.close();
        }
    }
}
