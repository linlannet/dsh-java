package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FileReferenceExpander 将消息中的 @path/to/file 引用内联展开为文件内容。
 * <p>消息预处理阶段调用，使模型可见引用文件的实际内容。
 */
public final class FileReferenceExpander {

    private static final Pattern AT_FILE_PATTERN = Pattern.compile("@(\\S+)");

    private final IFsPort fsPort;

    public FileReferenceExpander(IFsPort fsPort) {
        if (fsPort == null) {
            throw new IllegalArgumentException("fsPort must not be null");
        }
        this.fsPort = fsPort;
    }

    /**
     * 展开文本中的 @path/to/file 引用。
     *
     * @param text 原始文本
     * @param cwd  当前工作目录
     * @return 展开后的文本
     */
    public String expand(String text, String cwd) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = AT_FILE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String ref = matcher.group(1);
            String fullPath = resolvePath(ref, cwd);
            String replacement = resolveFileContent(fullPath);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 展开文本中的 @file 引用（无 cwd）。
     *
     * @param text 原始文本
     * @return 展开后的文本
     */
    public String expand(String text) {
        return expand(text, "");
    }

    /**
     * 解析 @file 引用为文件内容，文件不存在时保留原引用。
     */
    private String resolveFileContent(String fullPath) {
        if (fsPort.exists(fullPath)) {
            String content = fsPort.read(fullPath, 0, Integer.MAX_VALUE);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return "@" + fullPath;
    }

    /**
     * 将引用路径解析为绝对路径。
     */
    private String resolvePath(String ref, String cwd) {
        if (ref.startsWith("/") || cwd == null || cwd.isBlank()) {
            return ref;
        }
        String separator = cwd.endsWith("/") ? "" : "/";
        return cwd + separator + ref;
    }
}
