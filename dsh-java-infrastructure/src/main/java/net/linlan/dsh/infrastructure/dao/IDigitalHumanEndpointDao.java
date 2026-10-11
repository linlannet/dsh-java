package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.DigitalHumanEndpointPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IDigitalHumanEndpointDao {
    @Insert(value={"INSERT INTO digital_human_endpoint (id, digital_human_id, endpoint_type, base_url, credential_ref, protocol_version, agent_card, health_state, last_checked_at, latency_ms)\nVALUES (#{id}, #{digitalHumanId}, #{endpointType}, #{baseUrl}, #{credentialRef}, #{protocolVersion}, #{agentCard}, #{healthState}, #{lastCheckedAt}, #{latencyMs})\n"})
    public void insert(DigitalHumanEndpointPO var1);

    @Update(value={"UPDATE digital_human_endpoint\nSET endpoint_type = #{endpointType}, base_url = #{baseUrl}, credential_ref = #{credentialRef},\n    protocol_version = #{protocolVersion}, agent_card = #{agentCard}, health_state = #{healthState},\n    last_checked_at = #{lastCheckedAt}, latency_ms = #{latencyMs}\nWHERE digital_human_id = #{digitalHumanId}\n"})
    public int updateByHumanId(DigitalHumanEndpointPO var1);

    @Update(value={"UPDATE digital_human_endpoint\nSET health_state = #{healthState}, last_checked_at = #{lastCheckedAt}, latency_ms = #{latencyMs}\nWHERE digital_human_id = #{digitalHumanId}\n"})
    public int updateHealth(@Param(value="digitalHumanId") String var1, @Param(value="healthState") String var2, @Param(value="lastCheckedAt") Instant var3, @Param(value="latencyMs") Integer var4);

    @Select(value={"SELECT id, digital_human_id, endpoint_type, base_url, credential_ref, protocol_version, agent_card, health_state, last_checked_at, latency_ms\nFROM digital_human_endpoint WHERE digital_human_id = #{digitalHumanId}\n"})
    public Optional<DigitalHumanEndpointPO> findByHumanId(@Param(value="digitalHumanId") String var1);

    @Select(value={"SELECT id, digital_human_id, endpoint_type, base_url, credential_ref, protocol_version, agent_card, health_state, last_checked_at, latency_ms\nFROM digital_human_endpoint\n"})
    public List<DigitalHumanEndpointPO> findAll();

    @Delete(value={"DELETE FROM digital_human_endpoint WHERE digital_human_id = #{digitalHumanId}"})
    public int deleteByHumanId(@Param(value="digitalHumanId") String var1);
}

