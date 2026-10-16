package net.linlan.dsh.domain.identity.model.valobj;

import java.util.Objects;
import java.util.UUID;

/**
 * AnonymousUserId 表示一个匿名用户标识值对象。
 *
 * <p>每 home 一个匿名 UUID，不含机器或账户数据，不同 harness home 拥有不同标识。</p>
 *
 */
public record AnonymousUserId(String value) {

    public AnonymousUserId {
        Objects.requireNonNull(value, "value 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value 不能为空白");
        }
    }

    public static AnonymousUserId generate() {
        return new AnonymousUserId(UUID.randomUUID().toString());
    }

    public static AnonymousUserId of(String value) {
        return new AnonymousUserId(value);
    }
}
