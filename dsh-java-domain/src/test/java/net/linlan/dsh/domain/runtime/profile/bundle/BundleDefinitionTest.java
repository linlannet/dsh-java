package net.linlan.dsh.domain.runtime.profile.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BundleDefinitionTest {

    @Test
    void shouldCreateBundleWithProvidedValues() {
        BundleDefinition definition = new BundleDefinition(
                "custom",
                List.of("core", "fs"),
                Map.of("key", "value")
        );

        assertEquals("custom", definition.name());
        assertEquals(List.of("core", "fs"), definition.plugins());
        assertEquals(Map.of("key", "value"), definition.config());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new BundleDefinition("  ", List.of(), Map.of()));
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new BundleDefinition(null, List.of(), Map.of()));
    }

    @Test
    void shouldDefaultNullPluginsToEmpty() {
        BundleDefinition definition = new BundleDefinition("empty", null, null);

        assertTrue(definition.plugins().isEmpty());
        assertTrue(definition.config().isEmpty());
    }

    @Test
    void shouldDefensivelyCopyPlugins() {
        BundleDefinition definition = new BundleDefinition(
                "copy", List.of("a"), Map.of()
        );

        assertEquals(1, definition.plugins().size());
    }

    @Test
    void shouldProvideBaseConstant() {
        assertNotNull(BundleDefinition.BASE);
        assertEquals("base", BundleDefinition.BASE.name());
        assertTrue(BundleDefinition.BASE.plugins().contains("core"));
    }

    @Test
    void shouldProvideHeadlessConstant() {
        assertEquals("headless", BundleDefinition.HEADLESS.name());
        assertTrue(BundleDefinition.HEADLESS.config().containsKey("ui.mode"));
        assertEquals("headless", BundleDefinition.HEADLESS.config().get("ui.mode"));
    }

    @Test
    void shouldProvideSdkAppConstant() {
        assertEquals("sdk-app", BundleDefinition.SDK_APP.name());
        assertTrue(BundleDefinition.SDK_APP.plugins().contains("sdk"));
    }

    @Test
    void shouldProvideSdkMinimalConstant() {
        assertEquals("sdk-minimal", BundleDefinition.SDK_MINIMAL.name());
        assertTrue(BundleDefinition.SDK_MINIMAL.plugins().contains("sdk"));
    }

    @Test
    void shouldProvideAcpAppConstant() {
        assertEquals("acp-app", BundleDefinition.ACP_APP.name());
        assertTrue(BundleDefinition.ACP_APP.plugins().contains("acp"));
    }

    @Test
    void shouldProvideWebAppConstant() {
        assertEquals("web-app", BundleDefinition.WEB_APP.name());
        assertTrue(BundleDefinition.WEB_APP.plugins().contains("web"));
    }
}
