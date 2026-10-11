package net.linlan.dsh.infrastructure.adapter.fs;

import net.linlan.dsh.domain.sandbox.ISandboxPolicyService;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxPolicyRequest;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 沙箱感知的文件系统适配器。
 * <p>
 * 在 {@link LocalFsService} 的路径解析之外，叠加 {@link ISandboxPolicyService}
 * 的三档模式（read-only / workspace-write / danger-full-access）：
 * <ul>
 *   <li>{@code READ_ONLY}：读/搜索/list 放行；写一律拒绝；</li>
 *   <li>{@code WORKSPACE_WRITE}：读写都允许，但写必须落在工作区内
 *       （复用 {@code LocalFsService.resolveForWrite} 的边界检查）；</li>
 *   <li>{@code DANGER_FULL_ACCESS}：不限制。</li>
 * </ul>
 *
 *
 */
public class SandboxedFsService implements IFsPort {

    private final LocalFsService delegate;
    private final ISandboxPolicyService sandboxPolicyService;
    private final Path baseDir;
    private final Supplier<List<Path>> extraRootsSupplier;

    /**
     * 处理Sandboxed Fs Service。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param baseDir base Dir参数。
     * @param sandboxPolicyService sandbox 策略 Service参数。
     * @return 方法执行结果。
     */
    public SandboxedFsService(String baseDir, ISandboxPolicyService sandboxPolicyService) {
        this(baseDir, sandboxPolicyService, List::of);
    }

    /**
     * 处理Sandboxed Fs Service。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param baseDir base Dir参数。
     * @param sandboxPolicyService sandbox 策略 Service参数。
     * @param extraRootsSupplier 额外可写根 Supplier参数。
     * @return 方法执行结果。
     */
    public SandboxedFsService(String baseDir, ISandboxPolicyService sandboxPolicyService, Supplier<List<Path>> extraRootsSupplier) {
        this.baseDir = Paths.get(baseDir != null ? baseDir : ".").toAbsolutePath().normalize();
        this.delegate = new LocalFsService(baseDir);
        this.sandboxPolicyService = sandboxPolicyService;
        this.extraRootsSupplier = extraRootsSupplier != null ? extraRootsSupplier : List::of;
    }

    /**
     * 处理策略。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    private SandboxExecutionPolicy policy() {
        return sandboxPolicyService.resolveWithExtraRoots(new SandboxPolicyRequest(
                Optional.of(baseDir),
                Optional.empty()
        ), extraRootsSupplier.get());
    }

    @Override
    public String read(String path, int offset, int limit) {
        return delegate.read(path, offset, limit);
    }
    /**
     * 写入目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @param content 内容文本。
     */


    @Override
    public void write(String path, String content) {
        SandboxExecutionPolicy policy = policy();
        switch (policy.mode()) {
            case READ_ONLY -> throw new SecurityException(
                    "[sandbox: file write denied under read-only mode] " + path);
            // delegate.write 内部 resolveForWrite 已限制在可写根内
            case WORKSPACE_WRITE -> delegate.write(path, content, policy.workspaceRoots());
            case DANGER_FULL_ACCESS -> writeUnrestricted(path, content);
        }
    }

    /** danger-full-access 模式下的不受限写入。 */
    private void writeUnrestricted(String path, String content) {
        Path p = Paths.get(path);
        Path resolved = p.isAbsolute() ? p.normalize() : baseDir.resolve(p).normalize();
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
        return delegate.search(directory, pattern, maxResults);
    }
    /**
     * exists。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */


    @Override
    public boolean exists(String path) {
        return delegate.exists(path);
    }
    /**
     * 列出Dir。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param directory directory参数。
     * @return 方法执行结果。
     */


    @Override
    public List<DirEntry> listDir(String directory) {
        return delegate.listDir(directory);
    }
    /**
     * grep。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param directory directory参数。
     * @param pattern pattern参数。
     * @param include include参数。
     * @param isRegex is Regex参数。
     * @param maxResults max Results参数。
     * @return 方法执行结果。
     */


    @Override
    public List<String> grep(String directory, String pattern, String include, boolean isRegex, int maxResults) {
        return delegate.grep(directory, pattern, include, isRegex, maxResults);
    }
    /**
     * 读取Bytes。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */


    @Override
    public byte[] readBytes(String path) {
        return delegate.readBytes(path);
    }
}
