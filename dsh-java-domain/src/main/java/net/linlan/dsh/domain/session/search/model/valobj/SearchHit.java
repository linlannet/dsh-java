package net.linlan.dsh.domain.session.search.model.valobj;

import java.util.List;

/**
 * SearchHit 表示一次搜索命中的结果片段。
 *
 * @param sessionId 命中所在的会话 ID
 * @param seq       命中事件在会话中的序列号
 * @param snippet   命中片段原文
 * @param highlights 高亮片段列表（关键词被标记的子串）
 * @param score     相关性得分
 */
public record SearchHit(
        String sessionId,
        long seq,
        String snippet,
        List<String> highlights,
        float score
) {
    public SearchHit {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be null or empty");
        }
        if (snippet == null) {
            throw new IllegalArgumentException("snippet must not be null");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }

    public static SearchHit of(String sessionId, long seq, String snippet, float score) {
        return new SearchHit(sessionId, seq, snippet, List.of(), score);
    }
}
