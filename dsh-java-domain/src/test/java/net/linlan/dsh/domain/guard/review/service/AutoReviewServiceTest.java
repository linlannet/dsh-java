package net.linlan.dsh.domain.guard.review.service;

import net.linlan.dsh.domain.guard.review.model.valobj.AutoReviewDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoReviewService 自动审查")
class AutoReviewServiceTest {
    @Test
    @DisplayName("默认审查允许所有动作")
    void defaultAllow() {
        var service = new AutoReviewService();
        var decision = service.review("execute_tool");
        assertTrue(decision.allowed());
    }

    @Test
    @DisplayName("自定义审查函数拒绝危险动作")
    void customDeny() {
        var service = new AutoReviewService(action ->
            action.contains("dangerous") ? AutoReviewDecision.deny(action, "危险动作") : AutoReviewDecision.allow(action)
        );
        assertFalse(service.review("dangerous_action").allowed());
        assertTrue(service.review("safe_action").allowed());
    }

    @Test
    @DisplayName("审查函数异常时降级为允许")
    void exceptionDegrades() {
        var service = new AutoReviewService(action -> { throw new RuntimeException("模型不可用"); });
        assertTrue(service.review("any_action").allowed());
    }
}
