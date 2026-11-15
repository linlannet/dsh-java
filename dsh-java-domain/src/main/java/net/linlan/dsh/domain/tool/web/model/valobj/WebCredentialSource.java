package net.linlan.dsh.domain.tool.web.model.valobj;

/**
 * WebCredentialSource 定义一条 Web 凭据来源。
 *
 * <p>调用流程：配置列表按顺序解析 → 选中首个可用来源。</p>
 * <p>示例：{@code new WebCredentialSource("api-key", "sk-...")}。</p>
 *
 * @param type  来源类型：api-key/account
 * @param value 凭据值
 */
public record WebCredentialSource(String type, String value) {

    public static final String API_KEY = "api-key";
    public static final String ACCOUNT = "account";

    public WebCredentialSource {
        type = type == null ? "" : type.trim().toLowerCase();
        if (!API_KEY.equals(type) && !ACCOUNT.equals(type)) {
            throw new IllegalArgumentException("unsupported web credential type: " + type);
        }
    }

    /**
     * 判断凭据值是否可用。
     *
     * @return true 表示非空
     */
    public boolean usable() {
        return value != null && !value.isBlank();
    }
}
