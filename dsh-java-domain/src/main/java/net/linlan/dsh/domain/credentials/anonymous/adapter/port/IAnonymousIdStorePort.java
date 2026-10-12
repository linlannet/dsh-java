package net.linlan.dsh.domain.credentials.anonymous.adapter.port;

import java.util.Optional;

public interface IAnonymousIdStorePort {

    Optional<String> findById(String fingerprint);

    void save(String fingerprint, String anonymousId);
}
