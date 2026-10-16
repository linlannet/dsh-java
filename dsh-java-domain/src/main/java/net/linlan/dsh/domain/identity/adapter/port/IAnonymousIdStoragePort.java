package net.linlan.dsh.domain.identity.adapter.port;

import net.linlan.dsh.domain.identity.model.valobj.AnonymousUserId;
import java.util.Optional;

/**
 * IAnonymousIdStoragePort 匿名标识存储出站端口。
 *
 */
public interface IAnonymousIdStoragePort {

    Optional<AnonymousUserId> read();

    void write(AnonymousUserId id);

    boolean exists();
}
