package net.linlan.dsh.domain.typert.model.valobj;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.service.ToolResultRenderer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void truncationUsesUtf8BytesAndOmitsLaterOutput() {
        PtcProgramOutput output = new PtcProgramOutput(5);

        output.inspect("ab", "中国", "after");

        assertTrue(output.isTruncated());
        assertEquals(3, output.blocks().size());
        assertEquals("ab", ((TextBlock) output.blocks().get(0)).text());
        assertEquals("中", ((TextBlock) output.blocks().get(1)).text());
        String notice = ((TextBlock) output.blocks().get(2)).text();
        assertTrue(notice.contains("truncated"));
        assertTrue(notice.contains("omitted"));
        assertEquals(5, ("ab" + "中").getBytes(StandardCharsets.UTF_8).length);
        assertFalse(output.blocks().toString().contains("after"));
    }

    @Test
    void exactByteLimitDoesNotTruncate() {
        PtcProgramOutput output = new PtcProgramOutput(6);

        output.inspect("中国");

        assertFalse(output.isTruncated());
        assertEquals("中国", ((TextBlock) output.blocks().get(0)).text());
    }

    @Test
    void maxRenderBytesMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PtcProgramOutput(0));
        assertThrows(IllegalArgumentException.class, () -> new PtcProgramOutput(-1));
    }

    @Test
    void unchangedToolObjectAndArrayUseToolRenderedText() {
        PtcProgramOutput objectOutput = new PtcProgramOutput();
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("status", "ok");
        ToolExecutionResult.Success objectResult = new ToolExecutionResult.Success(
                object, List.of(new TextBlock("first"), new TextBlock("second")), null, List.of(), false);

        Object objectValue = objectOutput.bindToolResult(objectResult);
        objectOutput.inspect(objectValue);

        assertSame(object, objectValue);
        assertEquals(ToolResultRenderer.render(objectResult),
                ((TextBlock) objectOutput.blocks().get(0)).text());

        PtcProgramOutput arrayOutput = new PtcProgramOutput();
        List<Object> array = new ArrayList<>(List.of("a", "b"));
        ToolExecutionResult.Success arrayResult = new ToolExecutionResult.Success(
                array, List.of(new TextBlock("rendered array")), null, List.of(), false);

        Object arrayValue = arrayOutput.bindToolResult(arrayResult);
        arrayOutput.inspect(arrayValue);

        assertSame(array, arrayValue);
        assertEquals("rendered array", ((TextBlock) arrayOutput.blocks().get(0)).text());
    }

    @Test
    void modifiedToolObjectFallsBackToObjectRendering() {
        PtcProgramOutput output = new PtcProgramOutput();
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("status", "ok");
        ToolExecutionResult.Success result = new ToolExecutionResult.Success(
                object, List.of(new TextBlock("tool rendered text")), null, List.of(), false);
        Object value = output.bindToolResult(result);

        object.put("status", "changed");
        output.inspect(value);

        String rendered = ((TextBlock) output.blocks().get(0)).text();
        assertTrue(rendered.contains("status=changed"));
        assertNotEquals(ToolResultRenderer.render(result), rendered);
    }
}
