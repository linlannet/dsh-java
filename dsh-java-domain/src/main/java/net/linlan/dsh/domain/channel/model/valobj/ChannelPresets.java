package net.linlan.dsh.domain.channel.model.valobj;

import java.util.List;
import java.util.Optional;

/**
 * 渠道模板注册表（单一可信源）。
 * <p>
 * 覆盖国内外主流大模型渠道，前端下拉、协议路由、模型同步共用此注册表。
 * 每个模板提供：协议类型、默认 base_url、鉴权方式、推荐模型。
 * {@code custom} 模板恒存在，作为完全自定义渠道的兜底（参考 WaLiAPI channel_presets.rs）。
 *
 * <p>注意：模板只是「快捷填充」来源，运行时的权威配置仍然是 harness_model_setting 表。</p>
 *
 *
 */
public final class ChannelPresets {

    private ChannelPresets() {
    }

    /**
     * 全部渠道模板。
     *
     * @return 模板列表（只读）
     */
    public static List<ChannelPreset> all() {
        return PRESETS;
    }

    /**
     * 按模板标识查找。
     *
     * @param id 模板标识（如 deepseek、qwen、custom）
     * @return 模板（可选）
     */
    public static Optional<ChannelPreset> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return PRESETS.stream()
                .filter(p -> p.id().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    /**
     * 推断某个 providerCode 的默认协议；未知 provider 按 OpenAI 兼容处理。
     *
     * @param providerCode provider 编码
     * @return 协议编码
     */
    public static String defaultProtocolOf(String providerCode) {
        return findById(providerCode)
                .map(p -> p.protocol().code())
                .orElse(ChannelProtocol.OPENAI.code());
    }

    private static ChannelPreset preset(String id, String displayName, ChannelProtocol protocol,
                                        String baseUrl, AuthScheme authScheme,
                                        String regionGroup, String... models) {
        return new ChannelPreset(id, displayName, protocol, baseUrl, authScheme, List.of(models), regionGroup);
    }

    private static final List<ChannelPreset> PRESETS = List.of(
            // ── OpenAI 兼容协议：国际 ─────────────────────────────
            preset("openai", "OpenAI", ChannelProtocol.OPENAI,
                    "https://api.openai.com/v1", AuthScheme.BEARER, "international",
                    "gpt-4o", "gpt-4o-mini", "gpt-4.1", "o4-mini"),
            preset("gemini", "Google Gemini（OpenAI 兼容）", ChannelProtocol.OPENAI,
                    "https://generativelanguage.googleapis.com/v1beta/openai", AuthScheme.BEARER, "international",
                    "gemini-2.5-pro", "gemini-2.5-flash"),
            preset("grok", "xAI Grok", ChannelProtocol.OPENAI,
                    "https://api.x.ai/v1", AuthScheme.BEARER, "international",
                    "grok-4", "grok-3"),
            // ── OpenAI 兼容协议：国内 ─────────────────────────────
            preset("deepseek", "DeepSeek 深度求索", ChannelProtocol.OPENAI,
                    "https://api.deepseek.com/v1", AuthScheme.BEARER, "domestic",
                    "deepseek-chat", "deepseek-reasoner"),
            preset("qwen", "阿里通义千问", ChannelProtocol.OPENAI,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", AuthScheme.BEARER, "domestic",
                    "qwen-max", "qwen-plus", "qwen-turbo", "qwen3-235b-a22b"),
            preset("zhipu", "智谱 GLM", ChannelProtocol.OPENAI,
                    "https://open.bigmodel.cn/api/paas/v4", AuthScheme.BEARER, "domestic",
                    "glm-4.6", "glm-4.5", "glm-4.5-air"),
            preset("doubao", "字节豆包（火山方舟）", ChannelProtocol.OPENAI,
                    "https://ark.cn-beijing.volces.com/api/v3", AuthScheme.BEARER, "domestic",
                    "doubao-seed-1-6-250615", "doubao-seed-1-6-flash-250615"),
            preset("moonshot", "Moonshot Kimi", ChannelProtocol.OPENAI,
                    "https://api.moonshot.cn/v1", AuthScheme.BEARER, "domestic",
                    "kimi-k2-0905-preview", "moonshot-v1-128k"),
            preset("siliconflow", "SiliconFlow 硅基流动", ChannelProtocol.OPENAI,
                    "https://api.siliconflow.cn/v1", AuthScheme.BEARER, "domestic",
                    "deepseek-ai/DeepSeek-V3.2", "Qwen/Qwen3-235B-A22B"),
            // ── Anthropic 协议 ──────────────────────────────────
            preset("anthropic", "Anthropic Claude", ChannelProtocol.ANTHROPIC,
                    "https://api.anthropic.com", AuthScheme.X_API_KEY, "international",
                    "claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"),
            // ── Ollama 本地 ─────────────────────────────────────
            preset("ollama", "Ollama（本地）", ChannelProtocol.OLLAMA,
                    "http://127.0.0.1:11434", AuthScheme.NONE, "local",
                    "qwen3:8b", "deepseek-r1:8b", "llama3.1:8b"),
            // ── 自定义兜底 ──────────────────────────────────────
            preset("custom", "自定义渠道（OpenAI 兼容）", ChannelProtocol.OPENAI,
                    "", AuthScheme.BEARER, "custom")
    );
}
