package net.linlan.dsh.domain.guard.review.service;

import net.linlan.dsh.domain.guard.review.model.valobj.AutoReviewDecision;
import java.util.Objects;
import java.util.function.Function;

/**
 * AutoReviewService 每次工具调用前由当前 agent 的 provider 与模型审查待执行动作。
 * 实验性扩展，默认关闭，需在 app 模块显式配置条件装配启用。
 */
public class AutoReviewService {

    private final Function<String, AutoReviewDecision> reviewFunction;

    public AutoReviewService() {
        this.reviewFunction = action -> AutoReviewDecision.allow(action);
    }

    public AutoReviewService(Function<String, AutoReviewDecision> reviewFunction) {
        this.reviewFunction = Objects.requireNonNull(reviewFunction);
    }

    public AutoReviewDecision review(String action) {
        Objects.requireNonNull(action, "action 不能为空");
        try {
            return reviewFunction.apply(action);
        } catch (Exception e) {
            return AutoReviewDecision.allow(action);
        }
    }
}
