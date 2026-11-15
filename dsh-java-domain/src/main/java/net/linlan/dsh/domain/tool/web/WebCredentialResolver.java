package net.linlan.dsh.domain.tool.web;

import net.linlan.dsh.domain.tool.web.model.error.WebBillingError;
import net.linlan.dsh.domain.tool.web.model.valobj.WebBillingMode;
import net.linlan.dsh.domain.tool.web.model.valobj.WebCredentialSource;

import java.util.List;

/**
 * WebCredentialResolver 按计费模式与有序来源解析凭据。
 *
 * <p>调用流程：过滤可用来源 → 按模式选择 → 返回凭据或抛出 {@link WebBillingError}。</p>
 * <p>示例：auto 模式返回列表中的第一个可用来源。</p>
 */
public final class WebCredentialResolver {

    /**
     * 解析后的凭据。
     *
     * @param type  来源类型
     * @param value 凭据值
     */
    public record ResolvedCredential(String type, String value) {
    }

    /**
     * 解析凭据。
     *
     * @param mode    计费模式
     * @param sources 有序凭据来源
     * @return 选中的凭据
     */
    public ResolvedCredential resolve(WebBillingMode mode, List<WebCredentialSource> sources) {
        WebBillingMode billingMode = mode == null ? WebBillingMode.AUTO : mode;
        List<WebCredentialSource> usable = sources == null ? List.of() : sources.stream()
                .filter(WebCredentialSource::usable)
                .toList();

        return switch (billingMode) {
            case AUTO -> {
                if (usable.isEmpty()) {
                    throw throwMissing("auto");
                }
                yield new ResolvedCredential(usable.get(0).type(), usable.get(0).value());
            }
            case ACCOUNT -> usable.stream()
                    .filter(source -> WebCredentialSource.ACCOUNT.equals(source.type()))
                    .findFirst()
                    .map(source -> new ResolvedCredential(source.type(), source.value()))
                    .orElseThrow(() -> WebBillingError.accountUnavailable(null));
            case API_KEY -> usable.stream()
                    .filter(source -> WebCredentialSource.API_KEY.equals(source.type()))
                    .findFirst()
                    .map(source -> new ResolvedCredential(source.type(), source.value()))
                    .orElseThrow(() -> WebBillingError.credentialMissing("api-key", null));
        };
    }

    private static WebBillingError throwMissing(String mode) {
        return WebBillingError.credentialMissing(mode, null);
    }
}
