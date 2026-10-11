package net.linlan.dsh.domain.credentials.model.valobj;

import java.util.Optional;

/**
 * CredentialInfoVO 表示CredentialInfo的领域值对象。
 */
public record CredentialInfoVO(boolean configured, Optional<String> source, boolean writable) {
}
