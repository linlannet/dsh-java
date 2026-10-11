package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 图片读取工具（多模态）。
 * <p>
 * 执行流程：
 * 1. 校验扩展名与大小；
 * 2. 读取字节并解析宽高（PNG/JPEG/GIF/BMP/WEBP 常见头）；
 * 3. 以 ImageBlock 返回给多模态模型。
 * <p>
 * Mirrors TS {@code read_image}.
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class FsReadImageTool implements ToolDefinition {

    private static final String NAME = "fs_read_image";
    private static final String DESCRIPTION =
            "Read an image file (png/jpg/jpeg/gif/bmp/webp) and return it as visual content " +
            "for the model to see. Use this when the user asks you to look at a screenshot or picture.";

    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20MB

    private final IFsPort fsService;

    /**
     * 处理Fs Read Image 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public FsReadImageTool(IFsPort fsService) {
        this.fsService = fsService;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Path to the image file. Relative paths resolve against the agent's working directory."
                        )
                ),
                "required", List.of("path")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "mime", Map.of("type", "string"),
                        "bytes", Map.of("type", "integer")
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        try {
            Map<String, Object> argMap = (Map<String, Object>) args;
            String path = String.valueOf(argMap.get("path"));
            if (path == null || path.isBlank() || "null".equals(path)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("path parameter is required", "MISSING_PARAM"));
            }
            String mime = mimeTypeOf(path);
            if (mime == null) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail(
                                "unsupported image type (supported: png/jpg/jpeg/gif/bmp/webp): " + path,
                                "UNSUPPORTED_TYPE"));
            }
            if (!fsService.exists(path)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("file not found: " + path, "NOT_FOUND"));
            }
            byte[] data = fsService.readBytes(path);
            if (data.length == 0) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("empty file: " + path, "EMPTY_FILE"));
            }
            if (data.length > MAX_BYTES) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail(
                                "image too large (" + data.length + " bytes, max " + MAX_BYTES + "): " + path,
                                "TOO_LARGE"));
            }
            int[] dims = probeDimensions(data, mime);
            ImageBlock image = new ImageBlock(new ImageAttachmentRef(data, mime, dims[0], dims[1]));
            TextBlock caption = new TextBlock(
                    "[image] " + path + " (" + mime + ", " + data.length + " bytes"
                            + (dims[0] > 0 ? ", " + dims[0] + "x" + dims[1] : "") + ")");
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(caption, image)));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("fs_read_image failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }

    /**
     * 处理mime 类型 Of。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */
    private static String mimeTypeOf(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return null;
        return switch (n.substring(dot)) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            default -> null;
        };
    }

    /** 轻量解析常见图片格式的宽高，失败返回 {0,0}。 */
    private static int[] probeDimensions(byte[] d, String mime) {
        try {
            switch (mime) {
                case "image/png" -> {
                    if (d.length > 24 && (d[0] & 0xFF) == 0x89 && d[1] == 'P') {
                        return new int[]{be32(d, 16), be32(d, 20)};
                    }
                }
                case "image/gif" -> {
                    if (d.length > 10 && d[0] == 'G') {
                        return new int[]{le16(d, 6), le16(d, 8)};
                    }
                }
                case "image/bmp" -> {
                    if (d.length > 26 && d[0] == 'B' && d[1] == 'M') {
                        return new int[]{le32(d, 18), Math.abs(le32(d, 22))};
                    }
                }
                case "image/jpeg" -> {
                    int i = 2;
                    while (i + 9 < d.length) {
                        if ((d[i] & 0xFF) != 0xFF) { i++; continue; }
                        int marker = d[i + 1] & 0xFF;
                        if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                            return new int[]{be16(d, i + 7), be16(d, i + 5)}; // w, h
                        }
                        int len = be16(d, i + 2);
                        i += 2 + Math.max(len, 1);
                    }
                }
                default -> { }
            }
        } catch (Exception ignored) { }
        return new int[]{0, 0};
    }

    /**
     * 处理be16。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param d d参数。
     * @param o o参数。
     * @return 方法执行结果。
     */
    private static int be16(byte[] d, int o) { return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF); }
    /**
     * 处理be32。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param d d参数。
     * @param o o参数。
     * @return 方法执行结果。
     */
    private static int be32(byte[] d, int o) { return (be16(d, o) << 16) | be16(d, o + 2); }
    /**
     * 处理le16。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param d d参数。
     * @param o o参数。
     * @return 方法执行结果。
     */
    private static int le16(byte[] d, int o) { return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8); }
    /**
     * 处理le32。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param d d参数。
     * @param o o参数。
     * @return 方法执行结果。
     */
    private static int le32(byte[] d, int o) { return le16(d, o) | (le16(d, o + 2) << 16); }
}
