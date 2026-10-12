package net.linlan.dsh.infrastructure.adapter.scope;

import net.linlan.dsh.domain.core.scope.adapter.port.IScopeSubscriptionPort.SubscriptionRecord;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.SubscriptionToken;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryScopeSubscriptionPortTest {

    @Test
    void recordSubscriptionThenListContainsTheEntry() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey scope = ScopeKey.root("session");
        SubscriptionToken token = SubscriptionToken.generate();

        port.recordSubscription(scope, token);
        List<SubscriptionRecord> records = port.listSubscriptions();

        assertEquals(1, records.size());
        SubscriptionRecord record = records.get(0);
        assertEquals(scope, record.scope());
        assertEquals(token, record.token());
    }

    @Test
    void recordSubscriptionForSameTokenReplacesPreviousScope() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        SubscriptionToken token = SubscriptionToken.generate();
        ScopeKey first = ScopeKey.root("session-a");
        ScopeKey second = ScopeKey.root("session-b");

        port.recordSubscription(first, token);
        port.recordSubscription(second, token);

        List<SubscriptionRecord> records = port.listSubscriptions();
        assertEquals(1, records.size());
        assertEquals(second, records.get(0).scope());
    }

    @Test
    void removeSubscriptionDropsTheEntry() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey scope = ScopeKey.root("session");
        SubscriptionToken token = SubscriptionToken.generate();
        port.recordSubscription(scope, token);

        port.removeSubscription(token);

        assertTrue(port.listSubscriptions().isEmpty());
    }

    @Test
    void removeSubscriptionWithUnknownTokenIsNoop() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey scope = ScopeKey.root("session");
        SubscriptionToken kept = SubscriptionToken.generate();
        SubscriptionToken unknown = SubscriptionToken.generate();
        port.recordSubscription(scope, kept);

        port.removeSubscription(unknown);

        assertEquals(1, port.listSubscriptions().size());
    }

    @Test
    void removeSubscriptionWithNullTokenIsNoop() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey scope = ScopeKey.root("session");
        SubscriptionToken token = SubscriptionToken.generate();
        port.recordSubscription(scope, token);

        port.removeSubscription(null);

        assertEquals(1, port.listSubscriptions().size());
    }

    @Test
    void listSubscriptionsReturnsImmutableSnapshot() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey scope = ScopeKey.root("session");
        SubscriptionToken token = SubscriptionToken.generate();
        port.recordSubscription(scope, token);

        List<SubscriptionRecord> records = port.listSubscriptions();
        assertThrows(UnsupportedOperationException.class, () -> records.add(new SubscriptionRecord(scope, token)));
    }

    @Test
    void multipleSubscriptionsAreAllListed() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        ScopeKey root = ScopeKey.root("session");
        ScopeKey child = ScopeKey.child(root, "task");
        SubscriptionToken tokenA = SubscriptionToken.generate();
        SubscriptionToken tokenB = SubscriptionToken.generate();

        port.recordSubscription(root, tokenA);
        port.recordSubscription(child, tokenB);

        List<SubscriptionRecord> records = port.listSubscriptions();
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> r.scope().equals(root) && r.token().equals(tokenA)));
        assertTrue(records.stream().anyMatch(r -> r.scope().equals(child) && r.token().equals(tokenB)));
    }

    @Test
    void emptyPortListsNothing() {
        InMemoryScopeSubscriptionPort port = new InMemoryScopeSubscriptionPort();
        assertNotNull(port.listSubscriptions());
        assertTrue(port.listSubscriptions().isEmpty());
    }

    @Test
    void subscriptionTokenRejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () -> new SubscriptionToken((UUID) null));
    }

    @Test
    void scopeKeyParentAndDescendantRelationshipsHold() {
        ScopeKey root = ScopeKey.root("session");
        ScopeKey child = ScopeKey.child(root, "task");

        assertFalse(root.isDescendantOf(child));
        assertTrue(child.isDescendantOf(root));
        assertEquals(root, child.parent());
    }
}
