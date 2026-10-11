package net.linlan.dsh.infrastructure.adapter.fs;

import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地文件系统适配器。
 * <p>
 * 执行流程：
 * 1. 接收文件读写或搜索请求；
 * 2. 将相对路径解析到智能体工作目录；
 * 3. 返回文件系统操作结果。
 *
 * <p>调用流程：路径规范化 → 沙箱边界校验 → 本地文件操作。</p>
 * <p>示例：读取文件前先确认路径位于允许的工作区内。</p>
 *
 */
public class LocalFsService implements IFsPort {

    private final Path baseDir;
    private final boolean restrictWritesToBase;

    /**
     * 处理Local Fs Service。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param baseDir base Dir参数。
     * @return 方法执行结果。
     */
    public LocalFsService(String baseDir) {
        this(baseDir, Boolean.parseBoolean(System.getProperty("harness.fs.restrict-writes", "true")));
    }

    /**
     * 处理Local Fs Service。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param baseDir base Dir参数。
     * @param restrictWritesToBase restrict Writes To Base参数。
     * @return 方法执行结果。
     */
    public LocalFsService(String baseDir, boolean restrictWritesToBase) {
        this.baseDir = baseDir != null
                ? Paths.get(baseDir).toAbsolutePath().normalize()
                : Paths.get(".").toAbsolutePath().normalize();
        this.restrictWritesToBase = restrictWritesToBase;
    }

    @Override
    public String read(String path, int offset, int limit) {
        Path resolved = resolve(path);
        try {
            List<String> lines = Files.readAllLines(resolved, StandardCharsets.UTF_8);
            int total = lines.size();

            int start = offset > 0 ? offset - 1 : 0; // 1-indexed → 0-indexed
            if (start >= total) {
                return "[empty: offset " + offset + " exceeds " + total + " total lines]";
            }

            int end = limit > 0 ? Math.min(start + limit, total) : total;
            List<String> slice = lines.subList(start, end);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slice.size(); i++) {
                sb.append(slice.get(i));
                if (i < slice.size() - 1) sb.append("\n");
            }
            return sb.toString();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("File not found: " + resolved);
        } catch (IOException e) {
            throw new RuntimeException("Read error: " + e.getMessage(), e);
        }
    }
    /**
     * 写入目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @param content 内容文本。
     */


    @Override
    public void write(String path, String content) {
        Path resolved = resolveForWrite(path);
        try {
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Write error: " + e.getMessage(), e);
        }
    }
    /**
     * search。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param directory directory参数。
     * @param pattern pattern参数。
     * @param maxResults max Results参数。
     * @return 方法执行结果。
     */


    @Override
    public List<String> search(String directory, String pattern, int maxResults) {
        Path root = resolve(directory);
        if (!Files.isDirectory(root)) {
            throw new RuntimeException("Not a directory: " + root);
        }

        // 将 glob 转为 PathMatcher
        String glob = pattern.startsWith("glob:") ? pattern : "glob:" + pattern;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);

        List<String> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(root.relativize(p)) || matcher.matches(p.getFileName()))
                    .limit(maxResults)
                    .forEach(p -> results.add(p.toString()));
        } catch (IOException e) {
            throw new RuntimeException("Search error: " + e.getMessage(), e);
        }
        return results;
    }
    /**
     * exists。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */


    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }
    /**
     * 列出Dir。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param directory directory参数。
     * @return 方法执行结果。
     */


    @Override
    public List<DirEntry> listDir(String directory) {
        Path root = resolve(directory);
        if (!Files.isDirectory(root)) {
            throw new RuntimeException("Not a directory: " + root);
        }

        List<DirEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    String type = attrs.isDirectory() ? "directory" : "file";
                    long size = attrs.isDirectory() ? 0 : attrs.size();
                    entries.add(new DirEntry(p.getFileName().toString(), type, size));
                } catch (IOException ignored) {
                    // 跳过无法读取的条目
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("List error: " + e.getMessage(), e);
        }

        entries.sort((a, b) -> {
            if (!a.type().equals(b.type())) {
                return a.type().equals("directory") ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        return entries;
    }

    /**
     * 解析e。
     */
    private Path resolve(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return baseDir.resolve(p).normalize();
    }

    /**
     * 解析并校验沙箱边界（写操作专用）。
     * <p>
     * restrictWritesToBase=true 时，目标路径必须落在 baseDir 之内，
     * 否则抛 SecurityException（对应原版 workspace-write 模式的策略拒绝）。
     */
    private Path resolveForWrite(String path) {
        Path resolved = resolve(path);
        if (restrictWritesToBase && !resolved.startsWith(baseDir)) {
            throw new SecurityException(
                    "[sandbox: file access denied under workspace-write mode] " + resolved);
        }
        return resolved;
    }

    /**
     * 解析并校验沙箱边界（写操作专用，带额外可写根）。
     * <p>
     * 当 allowedRoots 非空时，目标路径只需落在任一可写根内即放行。
     */
    public Path resolveForWrite(String path, List<Path> allowedRoots) {
        Path resolved = resolve(path);
        boolean allowed = false;
        if (allowedRoots != null) {
            allowed = allowedRoots.stream().anyMatch(resolved::startsWith);
        }
        if (!allowed) {
            throw new SecurityException(
                    "[sandbox: file access denied under workspace-write mode] " + resolved);
        }
        return resolved;
    }

    /**
     * 写入文件（带额外可写根）。
     * <p>流程：解析并校验路径 → 创建父目录 → 写入内容。</p>
     */
    public void write(String path, String content, List<Path> allowedRoots) {
        Path resolved = resolveForWrite(path, allowedRoots);
        try {
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Write error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
        Path root = resolve(directory);
        if (!Files.isDirectory(root)) {
            throw new RuntimeException("Not a directory: " + root);
        }
        java.util.regex.Pattern compiled;
        try {
            compiled = isRegex
                    ? java.util.regex.Pattern.compile(pattern)
                    : java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(pattern));
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new RuntimeException("Invalid regex: " + e.getMessage(), e);
        }
        PathMatcher includeMatcher = include == null || include.isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher(include.startsWith("glob:") ? include : "glob:" + include);

        List<String> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(p -> includeMatcher == null
                            || includeMatcher.matches(root.relativize(p))
                            || includeMatcher.matches(p.getFileName()))
                    .filter(LocalFsService::looksText)
                    .toList();
            outer:
            for (Path file : files) {
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (compiled.matcher(lines.get(i)).find()) {
                            results.add(file + ":" + (i + 1) + ":" + lines.get(i));
                            if (results.size() >= maxResults) break outer;
                        }
                    }
                } catch (IOException ignored) {
                    // 跳过不可读文件
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Grep error: " + e.getMessage(), e);
        }
        return results;
    }
    /**
     * 读取Bytes。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */


    @Override
    public byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(resolve(path));
        } catch (IOException e) {
            throw new RuntimeException("Read error: " + e.getMessage(), e);
        }
    }

    /** 粗判文本文件：跳过常见二进制/图片/压缩格式，避免 grep 扫爆。 */
    private static boolean looksText(Path p) {
        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return true;
        String ext = n.substring(dot);
        return switch (ext) {
            case ".class", ".jar", ".zip", ".gz", ".tar", ".7z", ".rar",
                 ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico", ".svg",
                 ".mp3", ".mp4", ".mov", ".avi", ".wav", ".flac",
                 ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                 ".so", ".dylib", ".dll", ".exe", ".bin", ".dat", ".db",
                 ".woff", ".woff2", ".ttf", ".otf", ".eot" -> false;
            default -> true;
        };
    }
}
