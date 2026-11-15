package net.linlan.dsh.domain.storage.attachment;

import net.linlan.dsh.domain.storage.model.valobj.StorageErrorCode;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;

/**
 * AttachmentError 表示附件读取或解析失败。
 *
 * <p>调用流程：附件存储/读取校验 → 抛出本错误 → 工具执行管线记录附件错误码。</p>
 * <p>示例：读取不存在的附件哈希返回 {@code ATTACHMENT_NOT_FOUND}。</p>
 */
public class AttachmentError extends StorageException {

    /**
     * 使用错误码与消息创建。
     *
     * @param code    附件错误码
     * @param message 可读消息
     */
    public AttachmentError(StorageErrorCode code, String message) {
        super(code, message);
    }

    /**
     * 使用错误码、消息与原因创建。
     *
     * @param code    附件错误码
     * @param message 可读消息
     * @param cause   底层原因
     */
    public AttachmentError(StorageErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 创建附件不存在错误。
     *
     * @param contentHash 内容哈希
     * @return 附件错误
     */
    public static AttachmentError notFound(String contentHash) {
        return new AttachmentError(StorageErrorCode.ATTACHMENT_NOT_FOUND,
                "attachment not found: " + contentHash);
    }
}
