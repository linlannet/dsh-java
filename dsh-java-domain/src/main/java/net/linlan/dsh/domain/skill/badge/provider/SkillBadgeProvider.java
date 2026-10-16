package net.linlan.dsh.domain.skill.badge.provider;

import java.util.Optional;

/**
 * SkillBadgeProvider 复用既有 BadgeSkill，提供内置「powered by dsh」徽章 skill 加载。
 * 实验性扩展，默认关闭。
 */
public class SkillBadgeProvider {

    public String markdownSnippet() {
        return "![powered by dsh](dsh-badge.png)";
    }

    public Optional<byte[]> pngResource() {
        return Optional.empty();
    }

    public BadgeContent load() {
        var png = pngResource();
        if (png.isEmpty()) {
            return new BadgeContent(markdownSnippet(), null);
        }
        return new BadgeContent(markdownSnippet(), png.get());
    }

    public record BadgeContent(String markdown, byte[] png) { }
}
