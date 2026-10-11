package net.linlan.dsh.domain.credentials.model.valobj;

import java.util.regex.Pattern;

/**
 * Nominal reference to one credential: a POSIX-style environment-variable name.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record CredentialRef(String value) {

    private static final Pattern REF_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public CredentialRef {
        if (value == null || !REF_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "credential ref \"" + value + "\" must match " + REF_PATTERN);
        }
    }

    /**
     * 创建目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param value 参数值。
     * @return 创建的领域对象。
     */
    public static CredentialRef of(String value) {
        return new CredentialRef(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
