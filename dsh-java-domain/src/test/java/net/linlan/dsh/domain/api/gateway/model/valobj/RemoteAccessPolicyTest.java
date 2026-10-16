package net.linlan.dsh.domain.api.gateway.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteAccessPolicy 访问策略")
class RemoteAccessPolicyTest {

    @Test
    @DisplayName("owner 全部允许")
    void ownerAllowsAll() {
        var policy = RemoteAccessPolicy.owner();
        assertTrue(policy.allows("session"));
        assertTrue(policy.allows("workspace"));
        assertTrue(policy.allows("anything"));
    }

    @Test
    @DisplayName("非 owner 按资源绑定判定")
    void nonOwnerResourceBinding() {
        var policy = RemoteAccessPolicy.of("guest", Set.of("session", "workspace"));
        assertTrue(policy.allows("session"));
        assertTrue(policy.allows("workspace"));
        assertFalse(policy.allows("settings"));
    }

    @Test
    @DisplayName("通配符 * 允许全部")
    void wildcardAllowsAll() {
        var policy = RemoteAccessPolicy.of("admin", Set.of("*"));
        assertTrue(policy.allows("anything"));
    }

    @Test
    @DisplayName("空资源绑定拒绝全部非 owner")
    void emptyBindingDeniesAll() {
        var policy = RemoteAccessPolicy.of("guest", Set.of());
        assertFalse(policy.allows("session"));
    }
}
