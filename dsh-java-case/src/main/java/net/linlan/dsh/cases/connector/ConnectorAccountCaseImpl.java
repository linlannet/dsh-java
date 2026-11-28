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
    /** 评估目录。 */
    public List<ConnectorAssessmentDTO> listAssessments() {
        return accountFactory.listAssessments();
    }

    @Override
    /** 账号状态列表。 */
    public List<ConnectorAccountStatusDTO> listAccounts() {
        return accountFactory.listAccounts();
    }

    @Override
    /** 单账号状态。 */
    public ConnectorAccountStatusDTO getAccount(String connectorId) {
        return accountFactory.getAccount(connectorId);
    }

    @Override
    /** 连接或再次授权。 */
    public ConnectorAccountStatusDTO connect(String connectorId) {
        return accountFactory.connect(connectorId);
    }

    @Override
    /** OAuth 回调换令牌。 */
    public ConnectorAccountStatusDTO completeAuthorization(String connectorId, String code, String state) {
        return accountFactory.completeAuthorization(connectorId, code, state);
    }

    @Override
    /** 断开并清理本地缓存。 */
    public ConnectorAccountStatusDTO disconnect(String connectorId) {
        return accountFactory.disconnect(connectorId);
    }

    @Override
    /** 刷新令牌。 */
    public ConnectorAccountStatusDTO refresh(String connectorId) {
        return accountFactory.refresh(connectorId);
    }
}
