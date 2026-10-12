package net.linlan.dsh.domain.tool.model.valobj;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPhaseGuardTest {

    @Test
    void toolPhaseDeclaresFiveStagesInFixedOrder() {
        ToolPhase[] phases = ToolPhase.values();
        assertEquals(5, phases.length);
        assertEquals(ToolPhase.PRE, phases[0]);
        assertEquals(ToolPhase.GUARD, phases[1]);
        assertEquals(ToolPhase.AROUND, phases[2]);
        assertEquals(ToolPhase.POST, phases[3]);
        assertEquals(ToolPhase.RESULT, phases[4]);
    }

    @Test
    void toolPhaseOrdinalReflectsPipelineOrder() {
        assertTrue(ToolPhase.PRE.ordinal() < ToolPhase.GUARD.ordinal());
        assertTrue(ToolPhase.GUARD.ordinal() < ToolPhase.AROUND.ordinal());
        assertTrue(ToolPhase.AROUND.ordinal() < ToolPhase.POST.ordinal());
        assertTrue(ToolPhase.POST.ordinal() < ToolPhase.RESULT.ordinal());
    }

    @Test
    void toolPhaseValueOfResolvesByName() {
        assertEquals(ToolPhase.AROUND, ToolPhase.valueOf("AROUND"));
        assertEquals(ToolPhase.RESULT, ToolPhase.valueOf("RESULT"));
    }

    @Test
    void guardAllowIsNotDenied() {
        GuardDecision decision = GuardDecision.allow();
        assertInstanceOf(GuardDecision.Allow.class, decision);
        assertFalse(decision.isDenied());
    }

    @Test
    void guardDenyWithReasonOnlyDefaultsErrorCode() {
        GuardDecision decision = GuardDecision.deny("not allowed");
        GuardDecision.Deny deny = assertInstanceOf(GuardDecision.Deny.class, decision);
        assertEquals("not allowed", deny.reason());
        assertEquals("GUARD_DENIED", deny.errorCode());
        assertTrue(decision.isDenied());
    }

    @Test
    void guardDenyWithReasonAndErrorCodePreservesBoth() {
        GuardDecision decision = GuardDecision.deny("rate limited", "RATE_LIMIT");
        GuardDecision.Deny deny = assertInstanceOf(GuardDecision.Deny.class, decision);
        assertEquals("rate limited", deny.reason());
        assertEquals("RATE_LIMIT", deny.errorCode());
        assertTrue(deny.isDenied());
    }

    @Test
    void guardDenyRejectsEmptyReason() {
        assertThrows(IllegalArgumentException.class, () -> GuardDecision.deny(""));
    }

    @Test
    void guardDenyRejectsNullReason() {
        assertThrows(IllegalArgumentException.class, () -> new GuardDecision.Deny(null, "CODE"));
    }

    @Test
    void guardDenySubstitutesDefaultErrorCodeWhenBlank() {
        GuardDecision.Deny deny = new GuardDecision.Deny("blocked", "");
        assertEquals("GUARD_DENIED", deny.errorCode());
    }

    @Test
    void guardDenySubstitutesDefaultErrorCodeWhenNull() {
        GuardDecision.Deny deny = new GuardDecision.Deny("blocked", null);
        assertEquals("GUARD_DENIED", deny.errorCode());
    }

    @Test
    void aroundPassThroughInvokesProceedAndReturnsItsResult() {
        AroundWrapper wrapper = AroundWrapper.passThrough();
        ToolExecutionResult expected = new ToolExecutionResult.Success("ok", null, null, null, false);
        Supplier<ToolExecutionResult> proceed = () -> expected;

        ToolExecutionResult actual = wrapper.wrap(proceed);

        assertEquals(expected, actual);
    }

    @Test
    void aroundPassThroughShortCircuitsWhenProceedReturnsFailure() {
        AroundWrapper wrapper = AroundWrapper.passThrough();
        ToolExecutionResult failure = new ToolExecutionResult.Failure(null, null, null, null);
        Supplier<ToolExecutionResult> proceed = () -> failure;

        ToolExecutionResult actual = wrapper.wrap(proceed);

        assertTrue(actual.isError());
        assertEquals(failure, actual);
    }
}
