package net.linlan.dsh.domain.api.gateway.model.valobj;

import java.util.Objects;

/**
 * GatewayStreamItem 表示多路复用流中的一个流项。
 *
 * <p>每个流项携带序列号、负载与确认状态，重连时按序列号恢复并重发未确认流项。</p>
 *
 */
public record GatewayStreamItem(
        long sequence,
        Object payload,
        boolean acknowledged
) {
    public GatewayStreamItem {
        Objects.requireNonNull(payload, "payload 不能为空");
    }

    public GatewayStreamItem ack() {
        return new GatewayStreamItem(sequence, payload, true);
    }

    public static GatewayStreamItem of(long sequence, Object payload) {
        return new GatewayStreamItem(sequence, payload, false);
    }
}
