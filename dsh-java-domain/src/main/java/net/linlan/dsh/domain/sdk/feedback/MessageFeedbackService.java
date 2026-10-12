package net.linlan.dsh.domain.sdk.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageFeedbackService {

    private final Map<String, FeedbackEntry> store = new ConcurrentHashMap<>();

    public String rate(String messageId, int rating) {
        Objects.requireNonNull(messageId, "messageId");
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        FeedbackEntry existing = store.get(messageId);
        String note = existing == null ? "" : existing.note();
        Instant createdAt = existing == null ? Instant.now() : existing.createdAt();
        store.put(messageId, new FeedbackEntry(messageId, rating, note, createdAt));
        return "rated: messageId=" + messageId + ", rating=" + rating;
    }

    public String note(String messageId, String note) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(note, "note");
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        FeedbackEntry existing = store.get(messageId);
        int rating = existing == null ? 0 : existing.rating();
        Instant createdAt = existing == null ? Instant.now() : existing.createdAt();
        store.put(messageId, new FeedbackEntry(messageId, rating, note, createdAt));
        return "noted: messageId=" + messageId + ", note=" + note;
    }

    public FeedbackEntry getFeedback(String messageId) {
        Objects.requireNonNull(messageId, "messageId");
        return store.get(messageId);
    }

    public List<FeedbackEntry> listAll() {
        return new ArrayList<>(store.values());
    }

    public int size() {
        return store.size();
    }

    public static final class FeedbackEntry {
        private final String messageId;
        private final int rating;
        private final String note;
        private final Instant createdAt;

        public FeedbackEntry(String messageId, int rating, String note, Instant createdAt) {
            this.messageId = messageId;
            this.rating = rating;
            this.note = note;
            this.createdAt = createdAt;
        }

        public String messageId() { return messageId; }
        public int rating() { return rating; }
        public String note() { return note; }
        public Instant createdAt() { return createdAt; }
    }
}
