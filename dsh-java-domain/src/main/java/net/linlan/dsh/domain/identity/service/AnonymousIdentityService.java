package net.linlan.dsh.domain.identity.service;

import net.linlan.dsh.domain.identity.adapter.port.IAnonymousIdStoragePort;
import net.linlan.dsh.domain.identity.model.valobj.AnonymousUserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * AnonymousIdentityService 管理匿名标识的生命周期：首次生成、跨重启保留、删除后重新生成。
 *
 * <p>匿名 UUID 附加到遥测、反馈与 DeepSeek 请求记录上，实现安装级关联而不识别用户身份。</p>
 *
 */
@Service
public class AnonymousIdentityService {

    private final IAnonymousIdStoragePort storage;

    public AnonymousIdentityService(IAnonymousIdStoragePort storage) {
        this.storage = Objects.requireNonNull(storage, "storage 不能为空");
    }

    public AnonymousUserId getOrCreate() {
        Optional<AnonymousUserId> existing = storage.read();
        if (existing.isPresent()) {
            return existing.get();
        }
        AnonymousUserId generated = AnonymousUserId.generate();
        storage.write(generated);
        return generated;
    }

    public Optional<AnonymousUserId> current() {
        return storage.read();
    }

    public AnonymousUserId regenerate() {
        AnonymousUserId generated = AnonymousUserId.generate();
        storage.write(generated);
        return generated;
    }
}
