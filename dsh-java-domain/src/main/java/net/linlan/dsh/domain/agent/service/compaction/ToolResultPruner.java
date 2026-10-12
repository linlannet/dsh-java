package net.linlan.dsh.domain.agent.service.compaction;

/**
 * ToolResultPruner 对超阈值的工具结果进行 head/middle/tail 剪枝。
 * <p>保留首尾片段，中间部分替换为摘要，总长度低于阈值。
 */
public final class ToolResultPruner {

    private static final String ELLIPSIS = "\n... [pruned %d chars] ...\n";
    private static final int DEFAULT_HEAD = 500;
    private static final int DEFAULT_TAIL = 500;

    /**
     * 按阈值剪枝工具结果。
     *
     * @param result    工具结果文本
     * @param threshold 最大长度阈值
     * @return 剪枝后的文本（不超过 threshold）
     */
    public String prune(String result, int threshold) {
        if (result == null || result.length() <= threshold) {
            return result;
        }
        return headMiddleTail(result, threshold);
    }

    /**
     * head/middle/tail 剪枝：保留首尾片段，中间替换为摘要。
     *
     * @param result    工具结果文本
     * @param threshold 最大长度阈值
     * @return 剪枝后的文本
     */
    public String headMiddleTail(String result, int threshold) {
        if (result == null || result.length() <= threshold) {
            return result;
        }
        int headLen = Math.min(DEFAULT_HEAD, threshold / 3);
        int tailLen = Math.min(DEFAULT_TAIL, threshold / 3);
        int summaryOverhead = 40;

        if (headLen + tailLen + summaryOverhead > threshold) {
            headLen = Math.max(threshold / 4, 1);
            tailLen = Math.max(threshold / 4, 1);
        }

        int prunedChars = result.length() - headLen - tailLen;
        String head = result.substring(0, headLen);
        String tail = result.substring(result.length() - tailLen);
        String summary = String.format(ELLIPSIS, prunedChars);

        return head + summary + tail;
    }

    /**
     * 剪枝工具结果（使用默认阈值）。
     *
     * @param result 工具结果文本
     * @return 剪枝后的文本
     */
    public String prune(String result) {
        return prune(result, 2000);
    }
}
