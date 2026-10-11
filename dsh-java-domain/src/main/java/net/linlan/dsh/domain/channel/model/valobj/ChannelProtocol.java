package net.linlan.dsh.domain.channel.model.valobj;

import java.util.Arrays;

/**
 * 渠道协议类型。
 * <p>
 * 描述一条模型渠道使用哪种上游协议进行通信：
 * <ul>
 *   <li>{@link #OPENAI}：OpenAI 兼容协议（/chat/completions、/models），绝大多数厂商与网关兼容；</li>
 *   <li>{@link #ANTHROPIC}：Anthropic Messages 协议（/v1/messages，x-api-key 鉴权）；</li>
 *   <li>{@link #OLLAMA}：Ollama 原生协议（/api/chat、/api/tags）。</li>
 * </ul>
 * 设计参考 WaLiAPI 的 ChannelProtocol 枚举；协议与厂商解耦，
 * 同一厂商可同时以多种协议接入（如 DeepSeek 既兼容 openai 也兼容 anthropic）。
 *
 *
 */
public enum ChannelProtocol {

    /** OpenAI 兼容协议（chat/completions） */
    OPENAI("openai"),
    /** Anthropic Messages 协议 */
    ANTHROPIC("anthropic"),
    /** Ollama 原生协议 */
    OLLAMA("ollama");

    private final String code;

    ChannelProtocol(String code) {
        this.code = code;
    }

    /**
     * 协议稳定编码，用于持久化与跨端传输。
     *
     * @return 协议编码
     */
    public String code() {
        return code;
    }

    /**
     * 按编码解析协议；空值或未知编码回退为 {@link #OPENAI}（历史数据均走 OpenAI 兼容协议）。
     *
     * @param code 协议编码
     * @return 协议枚举
     */
    public static ChannelProtocol fromCode(String code) {
        if (code == null || code.isBlank()) {
            return OPENAI;
        }
        return Arrays.stream(values())
                .filter(p -> p.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(OPENAI);
    }
}
