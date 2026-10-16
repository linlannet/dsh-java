package net.linlan.dsh.domain.skill.badge.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillBadgeProvider 技能徽章")
class SkillBadgeProviderTest {
    @Test
    @DisplayName("markdownSnippet 返回 Markdown 片段")
    void markdownSnippet() {
        var provider = new SkillBadgeProvider();
        assertTrue(provider.markdownSnippet().contains("powered by dsh"));
    }

    @Test
    @DisplayName("pngResource 缺失时降级为仅 Markdown")
    void pngMissingDegrade() {
        var provider = new SkillBadgeProvider();
        var content = provider.load();
        assertNotNull(content.markdown());
        assertNull(content.png());
    }
}
