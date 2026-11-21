package net.linlan.dsh.plugin.emis;

/** 验签失败：只携带稳定错误码与安全文案，不携带令牌内容。 */
public class TokenException extends RuntimeException {

    private final String code;

    public TokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
