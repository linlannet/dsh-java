package net.linlan.dsh.domain.sdk.model.valobj;

import java.time.ZoneId;

/**
 * SDK prompt 的运行时上下文。
 *
 * @param timeZone 上报给模型的时区；缺省时使用当前进程时区
 */
public record SdkPromptContext(String timeZone) {

    public SdkPromptContext {
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = ZoneId.systemDefault().getId();
        }
    }

    public static SdkPromptContext processDefault() {
        return new SdkPromptContext(ZoneId.systemDefault().getId());
    }
}
