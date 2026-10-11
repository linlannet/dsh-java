package net.linlan.dsh.domain.model.valobj;

/** 附件服务持有的不可变附件元数据。 */
public record ImageAttachmentRef(byte[] data, String mimeType, int width, int height) {
    public ImageAttachmentRef {
        data = data == null ? new byte[0] : data.clone();
    }
}
