package net.linlan.dsh.domain.runtime.profile.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BundleAssemblerTest {

    @Test
    void shouldAssembleKnownBaseBundle() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.BASE);

        assertEquals("base", result.name());
        assertTrue(result.plugins().contains("core"));
    }

    @Test
    void shouldAssembleHeadlessBundleWithConfig() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.HEADLESS);

        assertEquals("headless", result.name());
        assertEquals("headless", result.config().get("ui.mode"));
    }

    @Test
    void shouldAssembleSdkAppBundle() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.SDK_APP);

        assertEquals("sdk-app", result.name());
        assertTrue(result.plugins().contains("sdk"));
        assertTrue(result.plugins().contains("plugin"));
    }

    @Test
    void shouldAssembleSdkMinimalBundle() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.SDK_MINIMAL);

        assertEquals("sdk-minimal", result.name());
        assertTrue(result.plugins().contains("sdk"));
    }

    @Test
    void shouldAssembleAcpAppBundle() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.ACP_APP);

        assertEquals("acp-app", result.name());
        assertTrue(result.plugins().contains("acp"));
    }

    @Test
    void shouldAssembleWebAppBundle() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assemble(BundleDefinition.WEB_APP);

        assertEquals("web-app", result.name());
        assertTrue(result.plugins().contains("web"));
    }

    @Test
    void shouldAssembleCustomDefinitionWhenNotPredefined() {
        BundleAssembler assembler = new BundleAssembler();
        BundleDefinition custom = new BundleDefinition(
                "custom",
                List.of("foo"),
                Map.of("k", "v")
        );

        BundleAssembler.AssembledBundle result = assembler.assemble(custom);

        assertEquals("custom", result.name());
        assertEquals(List.of("foo"), result.plugins());
        assertEquals("v", result.config().get("k"));
    }

    @Test
    void shouldRejectNullDefinition() {
        BundleAssembler assembler = new BundleAssembler();

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(null));
    }

    @Test
    void shouldAssembleByName() {
        BundleAssembler assembler = new BundleAssembler();

        BundleAssembler.AssembledBundle result = assembler.assembleByName("headless");

        assertNotNull(result);
        assertEquals("headless", result.name());
    }

    @Test
    void shouldRejectUnknownName() {
        BundleAssembler assembler = new BundleAssembler();

        assertThrows(IllegalStateException.class, () -> assembler.assembleByName("nope"));
    }

    @Test
    void shouldRejectBlankName() {
        BundleAssembler assembler = new BundleAssembler();

        assertThrows(IllegalArgumentException.class, () -> assembler.assembleByName("  "));
    }

    @Test
    void shouldSupportCustomBundleList() {
        BundleDefinition custom = new BundleDefinition(
                "only",
                List.of("x"),
                Map.of()
        );
        BundleAssembler assembler = new BundleAssembler(List.of(custom));

        BundleAssembler.AssembledBundle result = assembler.assembleByName("only");

        assertEquals("only", result.name());
        assertEquals(List.of("x"), result.plugins());
    }
}
