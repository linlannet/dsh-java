package net.linlan.dsh.cases.connector;

import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;

import java.util.List;

/** 连接器账号用例入口。 */
public interface IConnectorAccountCase {
    List<ConnectorAssessmentDTO> listAssessments();

    List<ConnectorAccountStatusDTO> listAccounts();

    ConnectorAccountStatusDTO getAccount(String connectorId);

    ConnectorAccountStatusDTO connect(String connectorId);

    ConnectorAccountStatusDTO completeAuthorization(String connectorId, String code, String state);

    ConnectorAccountStatusDTO disconnect(String connectorId);

    ConnectorAccountStatusDTO refresh(String connectorId);
}
