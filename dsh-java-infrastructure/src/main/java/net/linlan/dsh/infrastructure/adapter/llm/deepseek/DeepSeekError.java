package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;

/** DeepSeek 请求、文件与图片错误。 */
public class DeepSeekError extends HarnessError {

    public static final String CONTENT_REFUSED = LlmErrorCodes.CONTENT_REFUSED;
    public static final String FILE_POLICY_INVALID = "DEEPSEEK_FILE_POLICY_INVALID";
    public static final String IMAGE_TARGET_BYTES_EXCEEDED = "IMAGE_TARGET_BYTES_EXCEEDED";

    public DeepSeekError(String errorCode, String message) {
        super(errorCode, message);
    }

    public DeepSeekError(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static DeepSeekError missingFilePolicy() {
        return new DeepSeekError(FILE_POLICY_INVALID,
                "A resolved DeepSeekFilePolicy must be passed to DeepSeekFileStore for every call.");
    }
}
