package net.linlan.dsh.cases.connector;

import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/** 连接器账号用例只负责委托编排工厂。 */
@Service
public class ConnectorAccountCaseImpl implements IConnectorAccountCase {
    private final ConnectorAccountFactory accountFactory;

    public ConnectorAccountCaseImpl(ConnectorAccountFactory accountFactory) {
        this.accountFactory = accountFactory;
    }

    @Override
    public List<ConnectorAssessmentDTO> listAssessments() {
        return accountFactory.listAssessments();
    }

    @Override
    public List<ConnectorAccountStatusDTO> listAccounts() {
        return accountFactory.listAccounts();
    }

    @Override
    public ConnectorAccountStatusDTO getAccount(String connectorId) {
        return accountFactory.getAccount(connectorId);
    }

    @Override
    public ConnectorAccountStatusDTO connect(String connectorId) {
        return accountFactory.connect(connectorId);
    }

    @Override
    public ConnectorAccountStatusDTO completeAuthorization(String connectorId, String code, String state) {
        return accountFactory.completeAuthorization(connectorId, code, state);
    }

    @Override
    public ConnectorAccountStatusDTO disconnect(String connectorId) {
        return accountFactory.disconnect(connectorId);
    }

    @Override
    public ConnectorAccountStatusDTO refresh(String connectorId) {
        return accountFactory.refresh(connectorId);
    }
}
