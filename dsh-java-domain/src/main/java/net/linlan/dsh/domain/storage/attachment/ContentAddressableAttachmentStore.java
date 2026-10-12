package net.linlan.dsh.domain.storage.attachment;

import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageErrorCode;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;
import java.util.Base64;
import java.util.Optional;

/**
 * ContentAddressableAttachmentStore 以 SHA-256(content) 为存储键，相同内容去重。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class ContentAddressableAttachmentStore {

    private static final String TABLE = "attachments";
    private static final String UNIT_NAME = "content-addressable-attachments";
    private static final KvUnitDescriptor DESCRIPTOR = new KvUnitDescriptor(UNIT_NAME, java.util.Set.of(TABLE));

    private final IStorageBackendPort backend;
    private final HashFunction hashFunction;

    public ContentAddressableAttachmentStore(IStorageBackendPort backend) {
        this(backend, new Sha256HashFunction());
    }

    public ContentAddressableAttachmentStore(IStorageBackendPort backend, HashFunction hashFunction) {
        if (backend == null) {
            throw new IllegalArgumentException("backend required");
        }
        if (hashFunction == null) {
            throw new IllegalArgumentException("hash function required");
        }
        this.backend = backend;
        this.hashFunction = hashFunction;
    }

    public String store(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content required");
        }
        String contentHash = hashFunction.hash(content);
        String existing = backend.load(DESCRIPTOR).tables().getOrDefault(TABLE, java.util.Map.of()).get(contentHash);
        if (existing == null) {
            String encoded = Base64.getEncoder().encodeToString(content);
            backend.put(DESCRIPTOR, TABLE, contentHash, encoded);
        }
        return contentHash;
    }

    public byte[] retrieve(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("content hash required");
        }
        java.util.Map<String, String> table = backend.load(DESCRIPTOR).tables().get(TABLE);
        if (table == null) {
            throw new StorageException(StorageErrorCode.UNIT_NOT_FOUND, "attachment not found: " + contentHash);
        }
        String encoded = table.get(contentHash);
        if (encoded == null) {
            throw new StorageException(StorageErrorCode.UNIT_NOT_FOUND, "attachment not found: " + contentHash);
        }
        return Base64.getDecoder().decode(encoded);
    }

    public boolean exists(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return false;
        }
        java.util.Map<String, String> table = backend.load(DESCRIPTOR).tables().get(TABLE);
        return table != null && table.containsKey(contentHash);
    }

    public interface HashFunction {
        String hash(byte[] content);
    }

    static final class Sha256HashFunction implements HashFunction {
        @Override
        public String hash(byte[] content) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(content);
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new StorageException(StorageErrorCode.MALFORMED_MEDIUM, "SHA-256 not available", e);
            }
        }
    }
}
