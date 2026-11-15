package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeFormat;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.document.office.service.OfficeKitOutputStore;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Office 工具公共参数与错误处理。
 *
 * <p>调用流程：读取参数 → 校验格式 → 准备输出目录 → 统一失败映射。</p>
 * <p>示例：不支持的扩展名返回 {@code OFFICE_UNSUPPORTED_FORMAT}。</p>
 */
final class OfficeToolSupport {

    private OfficeToolSupport() {
    }

    static String requiredString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ToolArgsError(key + " is required");
        }
        return String.valueOf(value);
    }

    static String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    static String requireOfficeFile(String path) {
        String extension = extension(path);
        if (OfficeFormat.fromExtension(extension) == null) {
            throw new OfficeKitError(OfficeKitError.UNSUPPORTED_FORMAT,
                    "unsupported office format: " + path, null);
        }
        return extension;
    }

    static String extension(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return dot > slash && dot >= 0 ? path.substring(dot + 1) : "";
    }

    static String resolveOutputDir(String requested, OfficeKitConfig config) {
        String dir = requested == null ? config.outputDirectory() : requested;
        try {
            Files.createDirectories(Path.of(dir));
            return dir;
        } catch (IOException e) {
            throw OfficeKitError.conversionFailed("cannot create output dir: " + dir, e);
        }
    }

    static void pruneOutputs(OfficeKitConfig config) {
        try {
            new OfficeKitOutputStore(config).prune();
        } catch (RuntimeException ignored) {
            // 治理失败不影响工具结果
        }
    }

    static ToolExecutionResult failure(Throwable error) {
        if (error instanceof HarnessError harnessError) {
            return ToolExecutionResult.fail(harnessError.toFailure());
        }
        return ToolExecutionResult.fail(
                OfficeKitError.conversionFailed(String.valueOf(error.getMessage()), error).toFailure());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> args(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ToolArgsError("arguments must be an object");
        }
        return (Map<String, Object>) map;
    }
}
