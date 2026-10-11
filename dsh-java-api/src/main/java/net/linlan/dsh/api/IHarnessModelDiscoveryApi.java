package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessModelDiscoveryApi {

    Response<List<String>> discover(DiscoverModelsRequestDTO request);
}
