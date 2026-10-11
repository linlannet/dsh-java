package net.linlan.dsh.domain.channel.model.valobj;

/**
 * 渠道鉴权方式。
 * <p>
 * 描述访问上游渠道时如何携带 API Key：
 * <ul>
 *   <li>{@link #BEARER}：{@code Authorization: Bearer <key>}，OpenAI 兼容协议默认；</li>
 *   <li>{@link #X_API_KEY}：{@code x-api-key: <key>}，Anthropic 协议默认；</li>
 *   <li>{@link #NONE}：不鉴权（本地服务如 Ollama）。</li>
 * </ul>
 * 设计参考 WaLiAPI 的 AuthScheme 枚举。
 *
 *
 */
public enum AuthScheme {

    /** Authorization: Bearer <key> */
    BEARER,
    /** x-api-key: <key> */
    X_API_KEY,
    /** 无需鉴权 */
    NONE
}
