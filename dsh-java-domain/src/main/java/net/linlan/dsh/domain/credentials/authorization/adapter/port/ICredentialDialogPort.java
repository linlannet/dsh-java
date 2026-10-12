package net.linlan.dsh.domain.credentials.authorization.adapter.port;

import java.util.Optional;

public interface ICredentialDialogPort {

    Optional<String> promptCredential(String service, String scope);
}
