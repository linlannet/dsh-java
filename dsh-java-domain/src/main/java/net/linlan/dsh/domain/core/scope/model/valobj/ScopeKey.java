package net.linlan.dsh.domain.core.scope.model.valobj;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ScopeKey 是不透明的作用域键值对象，封装 {@code List<String>} segments。
 * <p>消费方无法解析内部结构，仅可通过 {@link #parent()}、{@link #isDescendantOf(ScopeKey)} 判断层级关系。</p>
 * <p>用于 Scope 路由：事件分发时按 scope 父链过滤订阅者——事件在更具体的 scope 发生时，
 * 订阅在更宽泛的 scope 的订阅者也能收到（事件接纳向上扩展）。</p>
 */
public final class ScopeKey {

    private final List<String> segments;

    private ScopeKey(List<String> segments) {
        this.segments = List.copyOf(segments);
    }

    /**
     * 创建根作用域。
     * @param name 根作用域名称，非空
     */
    public static ScopeKey root(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("root scope name must not be empty");
        }
        return new ScopeKey(List.of(name));
    }

    /**
     * 在父作用域下创建子作用域。
     * @param parent 父作用域，非空
     * @param name 子作用域名称，非空
     */
    public static ScopeKey child(ScopeKey parent, String name) {
        if (parent == null) {
            throw new IllegalArgumentException("parent scope must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("child scope name must not be empty");
        }
        var segs = new ArrayList<String>(parent.segments.size() + 1);
        segs.addAll(parent.segments);
        segs.add(name);
        return new ScopeKey(segs);
    }

    /**
     * 返回父作用域；根作用域返回 null。
     */
    public ScopeKey parent() {
        if (segments.size() <= 1) {
            return null;
        }
        return new ScopeKey(segments.subList(0, segments.size() - 1));
    }

    /**
     * 判断 this 是否是 ancestor 的严格后代（不包含自身）。
     * <p>用于 scope-filtered 事件分发：事件的 scope 是订阅者 scope 的后代时，订阅者收到事件。</p>
     */
    public boolean isDescendantOf(ScopeKey ancestor) {
        if (ancestor == null) {
            return false;
        }
        if (this.segments.size() <= ancestor.segments.size()) {
            return false;
        }
        for (int i = 0; i < ancestor.segments.size(); i++) {
            if (!this.segments.get(i).equals(ancestor.segments.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 this 是否是 ancestor 的后代或相等。
     */
    public boolean isDescendantOfOrEquals(ScopeKey ancestor) {
        return this.equals(ancestor) || this.isDescendantOf(ancestor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScopeKey that)) return false;
        return this.segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments);
    }

    @Override
    public String toString() {
        return "ScopeKey" + segments;
    }
}
