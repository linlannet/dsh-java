package net.linlan.dsh.domain.sdk.service;

import net.linlan.dsh.domain.sdk.adapter.port.ISdkTransportPort;
import net.linlan.dsh.domain.sdk.model.valobj.SdkPromptContext;
import net.linlan.dsh.domain.sdk.model.valobj.SessionPromptParams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdkClientServiceTest {

    @Test
    void promptWithoutTimeZoneReportsProcessZone() {
        AtomicReference<Object> requestBody = new AtomicReference<>();
        ISdkTransportPort transport = (ISdkTransportPort) Proxy.newProxyInstance(
                SdkClientServiceTest.class.getClassLoader(),
                new Class<?>[]{ISdkTransportPort.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("request")) {
                        requestBody.set(args[1]);
                        return Map.of("messageId", "message-1");
                    }
                    return null;
                });

        new SdkClientService(transport).sessionPrompt(new SessionPromptParams("sdk-1", List.of()));

        SessionPromptParams body = (SessionPromptParams) requestBody.get();
        assertEquals(ZoneId.systemDefault().getId(), body.context().timeZone());
    }

    @Test
    void explicitTimeZoneIsPreserved() {
        SessionPromptParams params = new SessionPromptParams(
                "sdk-1", List.of(), new SdkPromptContext("UTC"));
        assertEquals("UTC", params.context().timeZone());
    }
}
