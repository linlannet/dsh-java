package net.linlan.dsh.infrastructure.adapter.identity;

import net.linlan.dsh.domain.identity.adapter.port.IAnonymousIdStoragePort;
import net.linlan.dsh.domain.identity.model.valobj.AnonymousUserId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FileAnonymousIdStorage 存储匿名标识于 $DSH_HOME/.anonymous-user-id。
 *
 * <p>跨重启保留，仅在文件被删除后重新生成；home 目录不可写时记录告警并降级（不附加 UUID）。</p>
 *
 */
@Component
public class FileAnonymousIdStorage implements IAnonymousIdStoragePort {

    private static final Logger log = LoggerFactory.getLogger(FileAnonymousIdStorage.class);
    private final Path storagePath;

    @Autowired
    public FileAnonymousIdStorage(@Value("${dsh.home:#{systemProperties['user.home']}}") String dshHome) {
        this.storagePath = Path.of(dshHome, ".anonymous-user-id");
    }

    public FileAnonymousIdStorage(Path storagePath) {
        this.storagePath = storagePath;
    }

    @Override
    public Optional<AnonymousUserId> read() {
        try {
            if (!Files.exists(storagePath)) {
                return Optional.empty();
            }
            String value = Files.readString(storagePath).trim();
            if (value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(AnonymousUserId.of(value));
        } catch (IOException e) {
            log.warn("读取匿名标识失败，降级不附加 UUID: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void write(AnonymousUserId id) {
        try {
            Files.createDirectories(storagePath.getParent());
            Files.writeString(storagePath, id.value());
        } catch (IOException e) {
            log.warn("写入匿名标识失败，降级不附加 UUID: {}", e.getMessage());
        }
    }

    @Override
    public boolean exists() {
        return Files.exists(storagePath);
    }
}
