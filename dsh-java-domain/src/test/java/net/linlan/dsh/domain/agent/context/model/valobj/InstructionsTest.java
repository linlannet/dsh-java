package net.linlan.dsh.domain.agent.context.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Instructions AGENTS.md 指令集合 VO")
class InstructionsTest {

    @Test
    @DisplayName("构造时 path 不能为 null")
    void pathNullRejected() {
        assertThrows(NullPointerException.class,
                () -> new Instructions(null, "c", Map.of()));
    }

    @Test
    @DisplayName("content 为 null 时归一化为空串")
    void nullContentNormalizedToEmpty() {
        var ins = new Instructions("/a/AGENTS.md", null, Map.of());
        assertEquals("", ins.content());
    }

    @Test
    @DisplayName("directives 为 null 时归一化为空 Map")
    void nullDirectivesNormalizedToEmpty() {
        var ins = new Instructions("/a/AGENTS.md", "c", null);
        assertNotNull(ins.directives());
        assertTrue(ins.directives().isEmpty());
    }

    @Test
    @DisplayName("directives 被防御性拷贝，外部修改不影响 VO")
    void directivesDefensiveCopy() {
        var src = new LinkedHashMap<String, String>();
        src.put("k", "v");
        var ins = new Instructions("/a", "c", src);
        src.put("k2", "v2");
        assertEquals(1, ins.directives().size());
        assertEquals("v", ins.directives().get("k"));
    }

    @Test
    @DisplayName("empty 工厂方法生成空指令")
    void emptyFactory() {
        var empty = Instructions.empty("/root/AGENTS.md");
        assertEquals("/root/AGENTS.md", empty.path());
        assertEquals("", empty.content());
        assertTrue(empty.directives().isEmpty());
    }

    @Test
    @DisplayName("merge：other 覆盖 this 同键指令")
    void mergeOverridesSameKey() {
        var parent = new Instructions("/p", "parent-body",
                Map.of("lang", "en", "shared", "from-parent"));
        var child = new Instructions("/c", "child-body",
                Map.of("shared", "from-child", "extra", "c-val"));
        var merged = parent.merge(child);

        assertEquals("/c", merged.path(), "merge 后 path 取 other");
        assertEquals("from-child", merged.directives().get("shared"),
                "子级应覆盖父级同键");
        assertEquals("en", merged.directives().get("lang"),
                "父级独有键保留");
        assertEquals("c-val", merged.directives().get("extra"),
                "子级独有键保留");
    }

    @Test
    @DisplayName("merge：content 以换行拼接")
    void mergeConcatenatesContent() {
        var parent = new Instructions("/p", "P", Map.of());
        var child = new Instructions("/c", "C", Map.of());
        var merged = parent.merge(child);
        assertEquals("P\nC", merged.content());
    }

    @Test
    @DisplayName("merge null other 返回 this 自身")
    void mergeNullReturnsSelf() {
        var self = new Instructions("/p", "P", Map.of("a", "b"));
        var merged = self.merge(null);
        assertSame(self, merged);
    }

    @Test
    @DisplayName("merge 汇聚父级与子级全部键")
    void mergeCombinesAllKeys() {
        var parentMap = new LinkedHashMap<String, String>();
        parentMap.put("first", "1");
        parentMap.put("second", "2");
        var childMap = new LinkedHashMap<String, String>();
        childMap.put("third", "3");
        var parent = new Instructions("/p", "P", parentMap);
        var child = new Instructions("/c", "C", childMap);
        var merged = parent.merge(child);
        var keys = merged.directives().keySet();
        assertTrue(keys.contains("first"), "应包含父级 first");
        assertTrue(keys.contains("second"), "应包含父级 second");
        assertTrue(keys.contains("third"), "应包含子级 third");
        assertEquals(3, keys.size(), "合并后应共 3 个键");
    }

    @Test
    @DisplayName("merge 链式层级：祖父→父→子逐层覆盖")
    void mergeChainedHierarchy() {
        var grand = new Instructions("/g", "G", Map.of("level", "grand", "g-only", "g"));
        var parent = new Instructions("/p", "P", Map.of("level", "parent", "p-only", "p"));
        var child = new Instructions("/c", "C", Map.of("level", "child"));

        var merged = grand.merge(parent).merge(child);
        assertEquals("child", merged.directives().get("level"),
                "最终子级应胜出");
        assertEquals("g", merged.directives().get("g-only"));
        assertEquals("p", merged.directives().get("p-only"));
        assertEquals("G\nP\nC", merged.content());
    }
}
