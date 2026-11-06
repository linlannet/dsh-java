package net.linlan.dsh.domain.llm.model.valobj;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 辅助 LLM 请求用途的声明表（对应 TS 侧 {@code LlmRequestPurposeMap}）。
 *
 * <p>职责：共享的 LLM 核心不再内置任何用途键；每个构造辅助请求的生产方在自己的类型入口里
 * 通过 {@link #declare(String, String)} 声明用途，未声明的键由 {@link #require(String)} 拒绝。</p>
 *
 * <p>调用流程：生产方类初始化时声明用途 → 构造 {@link GenerateOptions} 时校验 → 适配器按用途微调请求。</p>
 * <p>示例：压缩引擎声明 {@code compaction}，协作规划器声明 {@code collaboration-planning}。</p>
 */
public final class LlmRequestPurposes {

    /** 上下文压缩请求用途，由压缩引擎声明。 */
    public static final String COMPACTION = "compaction";

    /** 会话标题生成请求用途，由标题生成生产方声明。 */
    public static final String SESSION_TITLE = "session-title";

    /** 多智能体协作规划请求用途，由协作规划器声明。 */
    public static final String COLLABORATION_PLANNING = "collaboration-planning";

    private static final Map<String, String> DECLARED = Collections.synchronizedMap(new LinkedHashMap<>());

    private LlmRequestPurposes() {
    }

    /**
     * 声明一个请求用途。重复声明同一键且归属不同时保留首个归属，避免运行期互相覆盖。
     *
     * @param purpose 用途键，不能为空白
     * @param owner 生产方标识（类名或模块名）
     */
    public static void declare(String purpose, String owner) {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("purpose owner must not be blank");
        }
        DECLARED.putIfAbsent(purpose.trim(), owner.trim());
    }

    /**
     * 判断用途是否已被生产方声明。
     *
     * @param purpose 用途键
     * @return 已声明返回 true
     */
    public static boolean isDeclared(String purpose) {
        return purpose != null && DECLARED.containsKey(purpose.trim());
    }

    /**
     * 返回某用途的声明归属。
     *
     * @param purpose 用途键
     * @return 归属标识；未声明时返回 null
     */
    public static String ownerOf(String purpose) {
        return purpose == null ? null : DECLARED.get(purpose.trim());
    }

    /**
     * 当前已声明的用途键集合。
     *
     * @return 不可变快照
     */
    public static Set<String> declared() {
        synchronized (DECLARED) {
            return Set.copyOf(DECLARED.keySet());
        }
    }

    /**
     * 校验用途：{@code null} 表示无辅助用途，允许通过；空白或未声明则拒绝。
     *
     * @param purpose 用途键
     * @return 规范化后的用途键（可能为 null）
     */
    public static String require(String purpose) {
        if (purpose == null) {
            return null;
        }
        String normalized = purpose.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        if (!DECLARED.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "undeclared request purpose: " + normalized + "; declared=" + declared()
                            + "; call LlmRequestPurposes.declare(...) from the producer package");
        }
        return normalized;
    }
}
