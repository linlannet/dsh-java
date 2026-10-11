package net.linlan.dsh.domain.session.event.model.valobj;

import java.util.Objects;

/**
 * SessionFormatVersion 封装会话格式版本号。
 * <p>当前版本为 {@value #CURRENT}。旧版本（< CURRENT）需要经迁移链升级。
 * <p>该值对象是不可变的，通过 {@link #of(int)} 工厂方法创建。
 */
public record SessionFormatVersion(int value) {

    /** 当前会话格式版本。 */
    public static final int CURRENT = 4;

    public SessionFormatVersion {
        if (value < 0) {
            throw new IllegalArgumentException("format version must be non-negative: " + value);
        }
    }

    /**
     * 工厂方法：创建版本号值对象。
     * @param version 版本号（≥ 0）
     * @return 版本号值对象
     */
    public static SessionFormatVersion of(int version) {
        return new SessionFormatVersion(version);
    }

    /**
     * 返回当前版本号。
     */
    public int value() {
        return value;
    }

    /**
     * 判断是否需要迁移。
     * @return version < {@link #CURRENT} 时返回 true
     */
    public boolean needsMigration() {
        return value < CURRENT;
    }

    /**
     * 判断是否为当前版本。
     */
    public boolean isCurrent() {
        return value == CURRENT;
    }
}
