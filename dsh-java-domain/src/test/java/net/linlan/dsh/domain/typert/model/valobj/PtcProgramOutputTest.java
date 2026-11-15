package net.linlan.dsh.domain.typert.model.valobj;

import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PtcProgramOutputTest {

    @Test
    void inspectKeepsDeclarationOrder() {
        PtcProgramOutput output = new PtcProgramOutput();

        output.inspect("first", Map.of("status", "ok"), "third");

        assertEquals(3, output.blocks().size());
        assertEquals("first", ((TextBlock) output.blocks().get(0)).text());
        assertTrue(((TextBlock) output.blocks().get(1)).text().contains("status=ok"));
        assertEquals("third", ((TextBlock) output.blocks().get(2)).text());
    }

    @Test
    void imageIsAttachedInInspectedPosition() {
        PtcProgramOutput output = new PtcProgramOutput();
        ImageAttachmentRef image = new ImageAttachmentRef(new byte[]{1, 2}, "image/png", 10, 20);

        output.inspect("before", image, "after");

        assertEquals(3, output.blocks().size());
        assertInstanceOf(TextBlock.class, output.blocks().get(0));
        assertInstanceOf(ImageBlock.class, output.blocks().get(1));
        assertInstanceOf(TextBlock.class, output.blocks().get(2));
    }

    @Test
    void returnValueAndTrailingExpressionAreNotAutoIncluded() {
        PtcProgramOutput output = new PtcProgramOutput();

        assertTrue(output.isEmpty());
        assertEquals(List.of(), output.blocks());

        output.inspect("explicit");
        assertFalse(output.isEmpty());
    }
}
