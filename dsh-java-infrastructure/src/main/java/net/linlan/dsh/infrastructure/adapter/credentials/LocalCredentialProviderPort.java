package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * File-backed credentials provider over {@code ~/.dsh/.credentials.yaml}, layered
 * against the environment by precedence:
 * <pre>
 * inherited process environment      (read-only, wins)
 * > ~/.dsh/.credentials.yaml         (provider-managed, writable)
 * > &lt;cwd&gt;/.env                       (read-only fallback)
 * </pre>
 *
 *
 */
public class LocalCredentialProviderPort implements ICredentialProviderPort {

    private static final String CREDENTIALS_FILE = ".credentials.yaml";
    private static final String ENV_FILE = ".env";
    private final Path credentialsPath;
    private final Path envPath;
    private final ConcurrentHashMap<String, String> managedStore = new ConcurrentHashMap<>();
    private final Map<String, String> envCache = new ConcurrentHashMap<>();

    /**
     * 处理Local 凭证 模型提供方 Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param dshHome dsh Home参数。
     * @return 方法执行结果。
     */
    public LocalCredentialProviderPort(Path dshHome) {
        this.credentialsPath = dshHome.resolve(CREDENTIALS_FILE);
        this.envPath = dshHome.resolve(ENV_FILE);
        if (!Files.exists(dshHome)) {
            try {
                Files.createDirectories(dshHome);
            } catch (Exception e) {
                // non-fatal at bootstrap
            }
        }
    }

    
    /**
     * 处理init。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     */
    public void init() {
        loadManagedStore();
        loadEnvFallback();
    }

    @Override
    public Optional<ResolvedCredentialVO> resolve(CredentialRef ref) {
        String key = ref.value();
        // 1. inherited process environment (read-only, highest priority)
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(new ResolvedCredentialVO(fromEnv, "env"));
        }
        // 2. managed store (.credentials.yaml)
        String fromManaged = managedStore.get(key);
        if (fromManaged != null && !fromManaged.isBlank()) {
            return Optional.of(new ResolvedCredentialVO(fromManaged, "file"));
        }
        // 3. env file fallback
        String fromEnvFile = envCache.get(key);
        if (fromEnvFile != null && !fromEnvFile.isBlank()) {
            return Optional.of(new ResolvedCredentialVO(fromEnvFile, "project-env"));
        }
        return Optional.empty();
    }
    /**
     * describe。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param ref ref参数。
     * @return 方法执行结果。
     */


    @Override
    public CredentialInfoVO describe(CredentialRef ref) {
        Optional<ResolvedCredentialVO> resolved = resolve(ref);
        boolean configured = resolved.isPresent();
        String source = configured ? resolved.get().source() : null;
    /**
     * 设置。
     */
        boolean writable = !isShadowedByEnv(ref) && managedStore.containsKey(ref.value()) || !Files.exists(credentialsPath);
        return new CredentialInfoVO(configured, Optional.ofNullable(source), writable);
    }

    @Override
    public void set(CredentialRef ref, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("credential value must not be blank");
        }
        assertUnshadowed(ref);
        managedStore.put(ref.value(), value);
        writeCredentialsFile();
    }
    /**
     * unset。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param ref ref参数。
     */


    @Override
    public void unset(CredentialRef ref) {
        assertUnshadowed(ref);
        managedStore.remove(ref.value());
        writeCredentialsFile();
    }

    /**
     * 处理assert Unshadowed。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param ref ref参数。
     */
    private void assertUnshadowed(CredentialRef ref) {
        if (System.getenv(ref.value()) != null) {
            throw new IllegalStateException(
                    "credential \"" + ref.value() + "\" is supplied by the process environment and cannot be written");
        }
    }

    /**
     * 判断Shadowed By Env是否成立。
     */
    private boolean isShadowedByEnv(CredentialRef ref) {
        return System.getenv(ref.value()) != null;
    }

    /**
     * 加载Managed Store。
     */
    private void loadManagedStore() {
        if (!Files.exists(credentialsPath)) return;
        try {
            Map<String, String> parsed = CredentialsYamlStore.parse(credentialsPath);
            parsed.forEach(managedStore::put);
        } catch (Exception e) {
            // keep last good snapshot; log but don't block startup
        }
    }

    /**
     * 加载Env Fallback。
     */
    private void loadEnvFallback() {
        if (!Files.exists(envPath)) return;
        try {
            Map<String, String> parsed = CredentialsYamlStore.parse(envPath);
            envCache.putAll(parsed);
        } catch (Exception e) {
            // non-fatal
        }
    }

    /**
     * 写入 Credentials 文件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     */
    private void writeCredentialsFile() {
        try {
            CredentialsYamlStore.write(credentialsPath, managedStore);
        } catch (Exception e) {
            throw new RuntimeException("failed to write credentials file: " + credentialsPath, e);
        }
    }
}
