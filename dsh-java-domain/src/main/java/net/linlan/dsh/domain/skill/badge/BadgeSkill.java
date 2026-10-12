package net.linlan.dsh.domain.skill.badge;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeSkill {

    private final Map<String, Badge> badges;
    private final Map<String, Award> awards;

    public BadgeSkill() {
        this.badges = new ConcurrentHashMap<>();
        this.awards = new ConcurrentHashMap<>();
    }

    public Badge createBadge(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (description == null) {
            throw new IllegalArgumentException("description required");
        }
        String id = UUID.randomUUID().toString();
        Badge badge = new Badge(id, name, description, Instant.now());
        badges.put(id, badge);
        return badge;
    }

    public Award awardBadge(String badgeId, String userId) {
        if (badgeId == null || badgeId.isBlank()) {
            throw new IllegalArgumentException("badgeId required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required");
        }
        if (!badges.containsKey(badgeId)) {
            throw new IllegalStateException("badge not found: " + badgeId);
        }
        String awardId = UUID.randomUUID().toString();
        Award award = new Award(awardId, badgeId, userId, Instant.now());
        awards.put(awardId, award);
        return award;
    }

    public Optional<Badge> getBadge(String badgeId) {
        return Optional.ofNullable(badges.get(badgeId));
    }

    public List<Badge> listBadges() {
        return List.copyOf(badges.values());
    }

    public List<Award> listAwardsByUser(String userId) {
        return awards.values().stream()
                .filter(a -> Objects.equals(a.userId(), userId))
                .toList();
    }

    public List<Award> listAwardsByBadge(String badgeId) {
        return awards.values().stream()
                .filter(a -> Objects.equals(a.badgeId(), badgeId))
                .toList();
    }

    public record Badge(String id, String name, String description, Instant createdAt) {}

    public record Award(String id, String badgeId, String userId, Instant awardedAt) {}
}
