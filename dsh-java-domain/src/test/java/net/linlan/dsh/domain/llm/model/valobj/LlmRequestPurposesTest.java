package net.linlan.dsh.domain.llm.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmRequestPurposes 声明式校验")
class LlmRequestPurposesTest {

    @Test
    @DisplayName("生产方声明后 require 通过")
    void declaredPurposePasses() {
        LlmRequestPurposes.declare("test-purpose", "TestProducer");
        assertTrue(LlmRequestPurposes.isDeclared("test-purpose"));
        assertEquals("TestProducer", LlmRequestPurposes.ownerOf("test-purpose"));
        assertEquals("test-purpose", LlmRequestPurposes.require("test-purpose"));
    }

    @Test
    @DisplayName("未声明用途被拒绝")
    void undeclaredPurposeRejected() {
        assertFalse(LlmRequestPurposes.isDeclared("never-declared"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LlmRequestPurposes.require("never-declared"));
        assertTrue(ex.getMessage().contains("undeclared request purpose"));
    }

    @Test
    @DisplayName("空白用途被拒绝，null 透传")
    void blankRejectedNullPasses() {
        assertThrows(IllegalArgumentException.class, () -> LlmRequestPurposes.require("  "));
        assertNull(LlmRequestPurposes.require(null));
    }

    @Test
    @DisplayName("重复声明保留首个归属")
    void duplicateKeepsFirstOwner() {
        LlmRequestPurposes.declare("test-dup", "First");
        LlmRequestPurposes.declare("test-dup", "Second");
        assertEquals("First", LlmRequestPurposes.ownerOf("test-dup"));
    }
}
