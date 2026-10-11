package net.linlan.dsh.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionMatrixVO;
import net.linlan.dsh.domain.task.permission.service.PermissionPolicyService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionPolicyServiceTest {

    @Test
    void splitsEnabledToolsIntoApprovalRequiredAndAutoApproved() {
        PermissionPolicyService service = new PermissionPolicyService(
                () -> new PermissionMatrixVO(Set.of("shell.exec", "fs.write")));

        var result = service.assess(List.of("fs.read", "web.search", "shell.exec", "fs.write"));

        assertTrue(result.approvalRequired());
        assertEquals(List.of("shell.exec", "fs.write"), result.approvalRequiredTools());
        assertEquals(List.of("fs.read", "web.search"), result.autoApprovedTools());
        assertEquals(List.of("fs.read", "web.search", "shell.exec", "fs.write"), result.enabledTools());
    }

    @Test
    void doesNotRequireApprovalWhenNoEnabledToolIsGated() {
        PermissionPolicyService service = new PermissionPolicyService(
                () -> new PermissionMatrixVO(Set.of("shell.exec")));

        var result = service.assess(List.of("fs.read"));

        assertFalse(result.approvalRequired());
        assertEquals(List.of("fs.read"), result.autoApprovedTools());
    }
}
