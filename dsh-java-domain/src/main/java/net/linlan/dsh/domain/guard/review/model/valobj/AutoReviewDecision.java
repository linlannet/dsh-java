package net.linlan.dsh.domain.guard.review.model.valobj;

import java.util.Objects;

public record AutoReviewDecision(String action, String verdict, String reason, int tokenCost) {
    public AutoReviewDecision {
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(verdict, "verdict 不能为空");
    }
    public boolean allowed() { return "ALLOW".equals(verdict); }
    public static AutoReviewDecision allow(String action) { return new AutoReviewDecision(action, "ALLOW", "", 0); }
    public static AutoReviewDecision deny(String action, String reason) { return new AutoReviewDecision(action, "DENY", reason, 0); }
}
