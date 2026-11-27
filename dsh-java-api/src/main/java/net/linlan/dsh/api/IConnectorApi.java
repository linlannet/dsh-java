package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;
import net.linlan.dsh.api.response.Response;

import java.util.List;

/** 连接器插件页契约：只交换状态、诊断与 OAuth 回调参数，不交换令牌。 */
public interface IConnectorApi {

    Response<List<ConnectorAssessmentDTO>> listAssessments();

    Response<List<ConnectorAccountStatusDTO>> listAccounts();

    Response<ConnectorAccountStatusDTO> getAccount(String connectorId);

    Response<ConnectorAccountStatusDTO> connect(String connectorId);

    Response<ConnectorAccountStatusDTO> completeAuthorization(String connectorId, String code, String state);

    Response<ConnectorAccountStatusDTO> disconnect(String connectorId);

    Response<ConnectorAccountStatusDTO> refresh(String connectorId);
}
