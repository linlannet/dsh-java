package cn.walioffice.plugin.sample;

import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文件摘要工具。
 * <p>
 * 继承 {@link AbstractTool}，演示一个有文件系统副作用的工具（非并发安全）。
 *
 * <p>调用流程：接收输入 → 执行本类职责 → 返回结果。</p>
 * <p>示例：根据调用方传入参数完成一次独立操作。</p>
 * 
 */
public class FileSummaryTool extends AbstractTool {

    private static final int MAX_PREVIEW_LINES = 20;

    @Override
    public String name() {
        return "file_summary";
    }

    @Override
    public String description() {
        return "读取指定文件并返回摘要：行数、字符数、前几行内容预览。";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("path", stringSchema("文件绝对路径"))
                .prop("maxLines", integerSchema("预览行数上限（默认 20）"))
                .required("path")
                .build();
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return objectSchema()
                .prop("path", stringSchema(null))
                .prop("totalLines", integerSchema(null))
                .prop("totalChars", integerSchema(null))
                .prop("preview", stringSchema(null))
                .build();
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) {
        return false;
    }
    /**
     * 返回 timeout Ms。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public long timeoutMs() {
        return 10_000;
    }
    /**
     * 运行目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param Map<String Map String参数。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */


    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String pathStr = str(args, "path");
        int maxLines = intVal(args, "maxLines", MAX_PREVIEW_LINES);

        if (pathStr.isBlank()) {
            return fail("path 参数不能为空", "MISSING_PATH");
        }

        Path filePath = Path.of(pathStr);
        if (!Files.exists(filePath)) {
            return fail("文件不存在: " + pathStr, "FILE_NOT_FOUND");
        }
        if (Files.isDirectory(filePath)) {
            return fail("路径是目录，不是文件: " + pathStr, "IS_DIRECTORY");
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            int totalLines = lines.size();
            int totalChars = lines.stream().mapToInt(String::length).sum();
            int previewCount = Math.min(maxLines, totalLines);
            List<String> previewLines = lines.subList(0, previewCount);

            StringBuilder sb = new StringBuilder();
            sb.append("文件: ").append(pathStr).append("\n");
            sb.append("总行数: ").append(totalLines).append("\n");
            sb.append("总字符数: ").append(totalChars).append("\n");
            sb.append("前 ").append(previewCount).append(" 行预览:\n");
            sb.append("```\n");
            for (int i = 0; i < previewLines.size(); i++) {
                sb.append(String.format("%4d | %s%n", i + 1, previewLines.get(i)));
            }
            if (totalLines > previewCount) {
                sb.append("... (还有 ").append(totalLines - previewCount).append(" 行未显示)\n");
            }
            sb.append("```");

            return ok(sb.toString());
        } catch (IOException e) {
            return fail("读取文件失败: " + e.getMessage(), "READ_ERROR");
        }
    }
}
