package net.linlan.dsh.infrastructure.adapter.extension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ExtensionConfigStore {
    private final ObjectMapper objectMapper;
    private final Path file;
    private final Object lock = new Object();
    private volatile StoredConfig cached;

    public ExtensionConfigStore(ObjectMapper objectMapper, Path file) {
        this.objectMapper = objectMapper;
        this.file = file;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void init() {
        Object object = this.lock;
        synchronized (object) {
            this.cached = this.load();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public StoredConfig snapshot() {
        StoredConfig config = this.cached;
        if (config != null) {
            return config;
        }
        Object object = this.lock;
        synchronized (object) {
            if (this.cached == null) {
                this.cached = this.load();
            }
            return this.cached;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void update(Consumer<StoredConfig> mutator) {
        Object object = this.lock;
        synchronized (object) {
            StoredConfig config = this.cached != null ? this.cached : this.load();
            mutator.accept(config);
            this.save(config);
            this.cached = config;
        }
    }

    private StoredConfig load() {
        try {
            if (Files.isRegularFile(this.file, new LinkOption[0])) {
                return (StoredConfig)this.objectMapper.readValue(Files.readString(this.file), StoredConfig.class);
            }
        }
        catch (IOException e) {
            try {
                Files.copy(this.file, this.file.resolveSibling("extensions.json.corrupt"), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        return new StoredConfig();
    }

    private void save(StoredConfig config) {
        try {
            Files.createDirectories(this.file.getParent(), new FileAttribute[0]);
            Path tmp = this.file.resolveSibling(String.valueOf(this.file.getFileName()) + ".tmp");
            Files.writeString(tmp, (CharSequence)this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString((Object)config), new OpenOption[0]);
            Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
            throw new IllegalStateException("\u4fdd\u5b58\u6269\u5c55\u914d\u7f6e\u5931\u8d25: " + String.valueOf(this.file) + " - " + e.getMessage(), e);
        }
    }

    public Path file() {
        return this.file;
    }

    public static <T> List<T> copyList(List<T> list) {
        return list == null ? new ArrayList() : new ArrayList<T>(list);
    }

    public static Map<String, String> copyMap(Map<String, String> map) {
        return map == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(map);
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, new LinkOption[0])) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root, new FileVisitOption[0]);){
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class StoredConfig {
        public List<String> disabledSkills = new ArrayList<String>();
        public List<StoredMcpServer> mcpServers = new ArrayList<StoredMcpServer>();
        public Map<String, String> cli = new LinkedHashMap<String, String>();
    }

    public static class UncheckedIOException
    extends RuntimeException {
        public UncheckedIOException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    public record StoredMcpServer(String name, String transport, String command, List<String> args, Map<String, String> env, String cwd, String url, Map<String, String> headers) {
    }
}

