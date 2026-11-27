package net.linlan.dsh.cases.connector;

import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;
import net.linlan.dsh.api.dto.ConnectorDiagnosticDTO;
import net.linlan.dsh.domain.connector.ConnectorAccountService;
import net.linlan.dsh.domain.connector.ConnectorAccountStatus;
import net.linlan.dsh.domain.connector.ConnectorAssessment;
import net.linlan.dsh.domain.connector.ConnectorDiagnostic;
import org.springframework.stereotype.Service;

import java.util.List;

/** 调用连接器领域服务并映射为不暴露令牌的 API DTO。 */
@Service
public class ConnectorAccountFactory {
    private final ConnectorAccountService accountService;

    public ConnectorAccountFactory(ConnectorAccountService accountService) {
        this.accountService = accountService;
    }

    public List<ConnectorAssessmentDTO> listAssessments() {
        return accountService.assessments().stream().map(ConnectorAccountFactory::toDto).toList();
    }

    public List<ConnectorAccountStatusDTO> listAccounts() {
        return accountService.listStatus().stream().map(ConnectorAccountFactory::toDto).toList();
    }

    public ConnectorAccountStatusDTO getAccount(String connectorId) {
        return toDto(accountService.status(connectorId));
    }

    public ConnectorAccountStatusDTO connect(String connectorId) {
        return toDto(accountService.connect(connectorId));
    }

    public ConnectorAccountStatusDTO completeAuthorization(String connectorId, String code, String state) {
        return toDto(accountService.completeAuthorization(connectorId, code, state));
    }

    public ConnectorAccountStatusDTO disconnect(String connectorId) {
        return toDto(accountService.disconnect(connectorId));
    }

    public ConnectorAccountStatusDTO refresh(String connectorId) {
        return toDto(accountService.refresh(connectorId));
    }

    private static ConnectorAccountStatusDTO toDto(ConnectorAccountStatus status) {
        return new ConnectorAccountStatusDTO(status.connectorId(), status.state().name(), status.connected(),
                status.authorizationUrl(), status.expiresAt(), status.updatedAt(), toDto(status.diagnostic()));
    }

    private static ConnectorDiagnosticDTO toDto(ConnectorDiagnostic diagnostic) {
        return diagnostic == null ? null
                : new ConnectorDiagnosticDTO(diagnostic.code(), diagnostic.message(), diagnostic.retryable());
    }

    private static ConnectorAssessmentDTO toDto(ConnectorAssessment assessment) {
        return new ConnectorAssessmentDTO(assessment.connectorId(), assessment.displayName(), assessment.upstreamForm(),
                assessment.javaStrategy(), assessment.decision(), assessment.alternative(), assessment.brandIcon(),
                assessment.localizedNames(), assessment.featurePreviews());
    }
}
