package net.linlan.dsh.trigger.service;

import net.linlan.dsh.api.IConnectorApi;
import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.dto.ConnectorAssessmentDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.connector.IConnectorAccountCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/** 连接器账号 API 适配器。 */
@Service
public class ConnectorApi implements IConnectorApi {
    private final IConnectorAccountCase accountCase;

    public ConnectorApi(IConnectorAccountCase accountCase) {
        this.accountCase = accountCase;
    }

    @Override
    public Response<List<ConnectorAssessmentDTO>> listAssessments() {
        return execute(accountCase::listAssessments);
    }

    @Override
    public Response<List<ConnectorAccountStatusDTO>> listAccounts() {
        return execute(accountCase::listAccounts);
    }

    @Override
    public Response<ConnectorAccountStatusDTO> getAccount(String connectorId) {
        return execute(() -> accountCase.getAccount(connectorId));
    }

    @Override
    public Response<ConnectorAccountStatusDTO> connect(String connectorId) {
        return execute(() -> accountCase.connect(connectorId));
    }

    @Override
    public Response<ConnectorAccountStatusDTO> completeAuthorization(String connectorId, String code, String state) {
        return execute(() -> accountCase.completeAuthorization(connectorId, code, state));
    }

    @Override
    public Response<ConnectorAccountStatusDTO> disconnect(String connectorId) {
        return execute(() -> accountCase.disconnect(connectorId));
    }

    @Override
    public Response<ConnectorAccountStatusDTO> refresh(String connectorId) {
        return execute(() -> accountCase.refresh(connectorId));
    }

    private static <T> Response<T> execute(Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError("连接器账号操作失败");
        }
    }
}
