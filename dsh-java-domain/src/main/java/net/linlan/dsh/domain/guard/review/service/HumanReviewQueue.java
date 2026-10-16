package net.linlan.dsh.domain.guard.review.service;

import net.linlan.dsh.domain.guard.review.model.valobj.AutoReviewDecision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * HumanReviewQueue 误判人工复核闭环队列。
 */
public final class HumanReviewQueue {

    public enum ReviewStatus { PENDING, APPROVED, REJECTED }

    public record ReviewItem(
            String id,
            String action,
            AutoReviewDecision autoDecision,
            Instant submittedAt,
            ReviewStatus status,
            String reviewer,
            String reviewNote
    ) {
        public ReviewItem {
            Objects.requireNonNull(id, "id 不能为空");
            Objects.requireNonNull(action, "action 不能为空");
            Objects.requireNonNull(autoDecision, "autoDecision 不能为空");
            submittedAt = submittedAt == null ? Instant.now() : submittedAt;
            status = status == null ? ReviewStatus.PENDING : status;
        }

        public ReviewItem approve(String reviewer, String note) {
            return new ReviewItem(id, action, autoDecision, submittedAt, ReviewStatus.APPROVED, reviewer, note);
        }

        public ReviewItem reject(String reviewer, String note) {
            return new ReviewItem(id, action, autoDecision, submittedAt, ReviewStatus.REJECTED, reviewer, note);
        }
    }

    private final Queue<ReviewItem> queue = new ConcurrentLinkedQueue<>();
    private int nextId = 1;

    public ReviewItem submit(String action, AutoReviewDecision autoDecision) {
        ReviewItem item = new ReviewItem("review-" + nextId++, action, autoDecision, Instant.now(), ReviewStatus.PENDING, null, null);
        queue.add(item);
        return item;
    }

    public ReviewItem approve(String id, String reviewer, String note) {
        for (ReviewItem item : queue) {
            if (item.id().equals(id) && item.status() == ReviewStatus.PENDING) {
                ReviewItem approved = item.approve(reviewer, note);
                queue.remove(item);
                queue.add(approved);
                return approved;
            }
        }
        return null;
    }

    public ReviewItem reject(String id, String reviewer, String note) {
        for (ReviewItem item : queue) {
            if (item.id().equals(id) && item.status() == ReviewStatus.PENDING) {
                ReviewItem rejected = item.reject(reviewer, note);
                queue.remove(item);
                queue.add(rejected);
                return rejected;
            }
        }
        return null;
    }

    public List<ReviewItem> pending() {
        List<ReviewItem> result = new ArrayList<>();
        for (ReviewItem item : queue) {
            if (item.status() == ReviewStatus.PENDING) {
                result.add(item);
            }
        }
        return result;
    }

    public List<ReviewItem> all() {
        return new ArrayList<>(queue);
    }

    public ReviewItem next() {
        for (ReviewItem item : queue) {
            if (item.status() == ReviewStatus.PENDING) {
                return item;
            }
        }
        return null;
    }

    public int pendingCount() {
        return pending().size();
    }

    public void clear() {
        queue.clear();
    }
}
