package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.DigitalHumanPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IDigitalHumanDao {
    @Insert(value={"INSERT INTO digital_human (id, display_name, avatar_ref, purpose, role_tags, theme_color, approval_policy, concurrency_limit, project_path, created_at, updated_at)\nVALUES (#{id}, #{displayName}, #{avatarRef}, #{purpose}, #{roleTags}, #{themeColor}, #{approvalPolicy}, #{concurrencyLimit}, #{projectPath}, #{createdAt}, #{updatedAt})\n"})
    public void insert(DigitalHumanPO var1);

    @Update(value={"UPDATE digital_human\nSET display_name = #{displayName}, avatar_ref = #{avatarRef}, purpose = #{purpose},\n    role_tags = #{roleTags}, theme_color = #{themeColor}, approval_policy = #{approvalPolicy},\n    concurrency_limit = #{concurrencyLimit}, project_path = #{projectPath}, updated_at = #{updatedAt}\nWHERE id = #{id}\n"})
    public int update(DigitalHumanPO var1);

    @Select(value={"SELECT id, display_name, avatar_ref, purpose, role_tags, theme_color, approval_policy, concurrency_limit, project_path, created_at, updated_at\nFROM digital_human ORDER BY created_at ASC\n"})
    public List<DigitalHumanPO> listAll();

    @Select(value={"SELECT id, display_name, avatar_ref, purpose, role_tags, theme_color, approval_policy, concurrency_limit, project_path, created_at, updated_at\nFROM digital_human WHERE id = #{id}\n"})
    public Optional<DigitalHumanPO> findById(@Param(value="id") String var1);

    @Delete(value={"DELETE FROM digital_human WHERE id = #{id}"})
    public int deleteById(@Param(value="id") String var1);
}

