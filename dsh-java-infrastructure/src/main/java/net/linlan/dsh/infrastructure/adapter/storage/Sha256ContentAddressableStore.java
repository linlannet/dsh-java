package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.attachment.ContentAddressableAttachmentStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Sha256ContentAddressableStore 使用 java.security.MessageDigest 计算 SHA-256 的内容寻址存储实现。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class Sha256ContentAddressableStore extends ContentAddressableAttachmentStore {

    public Sha256ContentAddressableStore(IStorageBackendPort backend) {
        super(backend, new Sha256HashFunction());
    }

    static final class Sha256HashFunction implements HashFunction {
        @Override
        public String hash(byte[] content) {
            if (content == null) {
                throw new IllegalArgumentException("content required");
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(content);
                StringBuilder sb = new StringBuilder(hashBytes.length * 2);
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
    }
}
