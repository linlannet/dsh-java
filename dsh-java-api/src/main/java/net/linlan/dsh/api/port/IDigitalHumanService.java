package net.linlan.dsh.api.port;

import java.util.List;
import java.util.Map;

/**
 * 数字人管理端口契约。
 */
public interface IDigitalHumanService {

    List<Map<String, Object>> list();

    Map<String, Object> get(String id);

    Map<String, Object> create(Map<String, Object> body);

    Map<String, Object> update(String id, Map<String, Object> body);

    void delete(String id);

    Map<String, Object> discover(String baseUrl, String endpointType, String credentialRef, String token);

    Map<String, Object> healthCheck(String id, String token);
}
