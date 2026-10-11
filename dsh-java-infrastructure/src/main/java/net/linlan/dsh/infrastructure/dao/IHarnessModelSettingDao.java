package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessModelSettingPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 模型渠道 DAO。表主键为 channel_code，active 标记全局当前生效渠道。
 */
@Mapper
public interface IHarnessModelSettingDao {

    @Insert("""
            INSERT INTO harness_model_setting (channel_code, display_name, provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at)
            VALUES (#{channelCode}, #{displayName}, #{providerCode}, #{modelCode}, #{baseUrl}, #{apiKeyRef}, #{protocol}, #{enabled}, #{active}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                provider_code = VALUES(provider_code),
                model_code = VALUES(model_code),
                base_url = VALUES(base_url),
                api_key_ref = VALUES(api_key_ref),
                protocol = VALUES(protocol),
                enabled = VALUES(enabled),
                active = VALUES(active),
                updated_at = VALUES(updated_at)
            """)
    void save(HarnessModelSettingPO modelSettingPO);

    @Select("""
            SELECT channel_code, display_name, provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at
            FROM harness_model_setting
            ORDER BY active DESC, updated_at ASC
            """)
    List<HarnessModelSettingPO> queryAll();

    @Select("""
            SELECT channel_code, display_name, provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at
            FROM harness_model_setting
            WHERE channel_code = #{channelCode}
            """)
    Optional<HarnessModelSettingPO> findByChannelCode(@Param("channelCode") String channelCode);

    @Select("""
            SELECT channel_code, display_name, provider_code, model_code, base_url, api_key_ref, protocol, enabled, active, updated_at
            FROM harness_model_setting
            WHERE active = TRUE
            """)
    Optional<HarnessModelSettingPO> findActive();

    @Update("""
            UPDATE harness_model_setting SET active = FALSE, updated_at = updated_at
            """)
    void clearActive();

    @Update("""
            UPDATE harness_model_setting SET active = TRUE, updated_at = #{updatedAt}
            WHERE channel_code = #{channelCode}
            """)
    int activate(@Param("channelCode") String channelCode,
                 @Param("updatedAt") java.time.Instant updatedAt);

    @Delete("""
            DELETE FROM harness_model_setting WHERE channel_code = #{channelCode}
            """)
    void deleteByChannelCode(@Param("channelCode") String channelCode);
}
