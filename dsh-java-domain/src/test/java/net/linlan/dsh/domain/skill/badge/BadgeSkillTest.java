package net.linlan.dsh.domain.skill.badge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BadgeSkillTest {

    @Test
    void shouldCreateBadge() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge badge = skill.createBadge("gold", "first place");

        assertNotNull(badge);
        assertNotNull(badge.id());
        assertEquals("gold", badge.name());
        assertEquals("first place", badge.description());
        assertNotNull(badge.createdAt());
    }

    @Test
    void shouldRejectNullNameOnCreate() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.createBadge(null, "desc"));
    }

    @Test
    void shouldRejectBlankNameOnCreate() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.createBadge("  ", "desc"));
    }

    @Test
    void shouldRejectNullDescriptionOnCreate() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.createBadge("name", null));
    }

    @Test
    void shouldAwardBadge() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge badge = skill.createBadge("silver", "second");

        BadgeSkill.Award award = skill.awardBadge(badge.id(), "user-1");

        assertNotNull(award);
        assertEquals(badge.id(), award.badgeId());
        assertEquals("user-1", award.userId());
        assertNotNull(award.awardedAt());
    }

    @Test
    void shouldRejectAwardForUnknownBadge() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalStateException.class, () -> skill.awardBadge("unknown-badge", "user-1"));
    }

    @Test
    void shouldRejectNullBadgeIdOnAward() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.awardBadge(null, "user-1"));
    }

    @Test
    void shouldRejectBlankBadgeIdOnAward() {
        BadgeSkill skill = new BadgeSkill();
        assertThrows(IllegalArgumentException.class, () -> skill.awardBadge("  ", "user-1"));
    }

    @Test
    void shouldRejectNullUserIdOnAward() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge badge = skill.createBadge("b", "d");
        assertThrows(IllegalArgumentException.class, () -> skill.awardBadge(badge.id(), null));
    }

    @Test
    void shouldRejectBlankUserIdOnAward() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge badge = skill.createBadge("b", "d");
        assertThrows(IllegalArgumentException.class, () -> skill.awardBadge(badge.id(), "  "));
    }

    @Test
    void shouldGetBadgeById() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge badge = skill.createBadge("bronze", "third");

        var found = skill.getBadge(badge.id());
        assertTrue(found.isPresent());
        assertEquals("bronze", found.get().name());
    }

    @Test
    void shouldReturnEmptyForMissingBadge() {
        BadgeSkill skill = new BadgeSkill();
        assertTrue(skill.getBadge("missing").isEmpty());
    }

    @Test
    void shouldListBadges() {
        BadgeSkill skill = new BadgeSkill();
        skill.createBadge("a", "desc-a");
        skill.createBadge("b", "desc-b");

        List<BadgeSkill.Badge> badges = skill.listBadges();
        assertEquals(2, badges.size());
    }

    @Test
    void shouldListAwardsByUser() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge b1 = skill.createBadge("a", "d");
        BadgeSkill.Badge b2 = skill.createBadge("b", "d");
        skill.awardBadge(b1.id(), "user-x");
        skill.awardBadge(b2.id(), "user-x");
        skill.awardBadge(b1.id(), "user-y");

        List<BadgeSkill.Award> awards = skill.listAwardsByUser("user-x");
        assertEquals(2, awards.size());
    }

    @Test
    void shouldListAwardsByBadge() {
        BadgeSkill skill = new BadgeSkill();
        BadgeSkill.Badge b1 = skill.createBadge("a", "d");
        skill.awardBadge(b1.id(), "u1");
        skill.awardBadge(b1.id(), "u2");

        List<BadgeSkill.Award> awards = skill.listAwardsByBadge(b1.id());
        assertEquals(2, awards.size());
    }
}
