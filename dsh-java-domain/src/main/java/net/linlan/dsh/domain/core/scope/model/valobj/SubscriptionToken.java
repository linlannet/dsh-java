package net.linlan.dsh.domain.core.scope.model.valobj;

import java.util.UUID;

/**
 * SubscriptionToken 是不透明的订阅令牌，用于取消订阅。
 * <p>消费方仅可持有和传递 token，无法解析其内部值。</p>
 */
public record SubscriptionToken(UUID value) {

    public SubscriptionToken {
        if (value == null) {
            throw new IllegalArgumentException("token value must not be null");
        }
    }

    /**
     * 生成新的随机令牌。
     */
    public static SubscriptionToken generate() {
        return new SubscriptionToken(UUID.randomUUID());
    }
}
