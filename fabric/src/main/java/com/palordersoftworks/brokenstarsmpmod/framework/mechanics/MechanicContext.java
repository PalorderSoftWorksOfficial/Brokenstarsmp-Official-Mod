package com.palordersoftworks.brokenstarsmpmod.framework.mechanics;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MechanicContext {
    private final MechanicScope scope;
    private final String dimension;
    private final String world;
    private final String region;
    private final String block;
    private final String permission;
    private final String rank;
    private final String team;
    private final String player;
    private final String entity;
    private final String item;
    private final String profile;
    private final String event;
    private final Set<String> tags;

    private MechanicContext(Builder builder) {
        scope = builder.scope == null ? inferScope(builder) : builder.scope;
        dimension = clean(builder.dimension);
        world = clean(builder.world);
        region = clean(builder.region);
        block = clean(builder.block);
        permission = clean(builder.permission);
        rank = clean(builder.rank);
        team = clean(builder.team);
        player = clean(builder.player);
        entity = clean(builder.entity);
        item = clean(builder.item);
        profile = clean(builder.profile);
        event = clean(builder.event);
        tags = Set.copyOf(builder.tags);
    }

    public static MechanicContext global() {
        return builder().build();
    }

    public static MechanicContext forDimension(String dimension) {
        return builder().dimension(dimension).build();
    }

    public static MechanicContext forWorld(String dimension, String world) {
        return builder().dimension(dimension).world(world).build();
    }

    public static MechanicContext forBlock(String block) {
        return builder().block(block).build();
    }

    public static MechanicContext forPlayer(String player) {
        return builder().player(player).build();
    }

    public static MechanicContext forPlayer(UUID player) {
        return forPlayer(player == null ? null : player.toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public MechanicScope scope() {
        return scope;
    }

    public String dimension() {
        return dimension;
    }

    public String world() {
        return world;
    }

    public String region() {
        return region;
    }

    public String block() {
        return block;
    }

    public String permission() {
        return permission;
    }

    public String rank() {
        return rank;
    }

    public String team() {
        return team;
    }

    public String player() {
        return player;
    }

    public String entity() {
        return entity;
    }

    public String item() {
        return item;
    }

    public String profile() {
        return profile;
    }

    public String event() {
        return event;
    }

    public Set<String> tags() {
        return tags;
    }

    public int specificity() {
        int value = scope.specificity();
        value += dimension == null ? 0 : 1;
        value += world == null ? 0 : 1;
        value += region == null ? 0 : 1;
        value += block == null ? 0 : 1;
        value += permission == null ? 0 : 1;
        value += rank == null ? 0 : 1;
        value += team == null ? 0 : 1;
        value += player == null ? 0 : 1;
        value += entity == null ? 0 : 1;
        value += item == null ? 0 : 1;
        return value;
    }

    public boolean matches(MechanicContext other) {
        if (other == null) {
            return false;
        }
        if (this.isGlobal()) {
            return true;
        }
        if (other.isGlobal()) {
            return false;
        }
        return matchesValue(dimension, other.dimension)
                && matchesValue(world, other.world)
                && matchesValue(region, other.region)
                && matchesValue(block, other.block)
                && matchesValue(permission, other.permission)
                && matchesValue(rank, other.rank)
                && matchesValue(team, other.team)
                && matchesValue(player, other.player)
                && matchesValue(entity, other.entity)
                && matchesValue(item, other.item)
                && matchesValue(profile, other.profile)
                && matchesValue(event, other.event)
                && (tags.isEmpty() || other.tags.containsAll(tags));
    }

    public boolean isGlobal() {
        return scope == MechanicScope.GLOBAL
                && dimension == null && world == null && region == null && block == null
                && permission == null && rank == null && team == null && player == null
                && entity == null && item == null && profile == null && event == null
                && tags.isEmpty();
    }

    public Builder toBuilder() {
        return new Builder()
                .scope(scope)
                .dimension(dimension)
                .world(world)
                .region(region)
                .block(block)
                .permission(permission)
                .rank(rank)
                .team(team)
                .player(player)
                .entity(entity)
                .item(item)
                .profile(profile)
                .event(event)
                .tags(tags);
    }

    private static boolean matchesValue(String expected, String actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    private static MechanicScope inferScope(Builder builder) {
        if (builder.item != null) return MechanicScope.ITEM;
        if (builder.entity != null) return MechanicScope.ENTITY;
        if (builder.player != null) return MechanicScope.PLAYER;
        if (builder.team != null) return MechanicScope.TEAM;
        if (builder.rank != null) return MechanicScope.RANK;
        if (builder.permission != null) return MechanicScope.PERMISSION;
        if (builder.region != null) return MechanicScope.REGION;
        if (builder.block != null) return MechanicScope.BLOCK;
        if (builder.world != null) return MechanicScope.WORLD;
        if (builder.dimension != null) return MechanicScope.DIMENSION;
        return MechanicScope.GLOBAL;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MechanicContext context)) return false;
        return scope == context.scope
                && Objects.equals(dimension, context.dimension)
                && Objects.equals(world, context.world)
                && Objects.equals(region, context.region)
                && Objects.equals(block, context.block)
                && Objects.equals(permission, context.permission)
                && Objects.equals(rank, context.rank)
                && Objects.equals(team, context.team)
                && Objects.equals(player, context.player)
                && Objects.equals(entity, context.entity)
                && Objects.equals(item, context.item)
                && Objects.equals(profile, context.profile)
                && Objects.equals(event, context.event)
                && Objects.equals(tags, context.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, dimension, world, region, block, permission, rank, team, player, entity, item, profile, event, tags);
    }

    @Override
    public String toString() {
        return "MechanicContext{" + scope + ", dimension=" + dimension + ", world=" + world + ", region=" + region
                + ", block=" + block + ", permission=" + permission + ", rank=" + rank + ", team=" + team + ", player=" + player
                + ", entity=" + entity + ", item=" + item + ", profile=" + profile + ", event=" + event + '}';
    }

    public static final class Builder {
        private MechanicScope scope;
        private String dimension;
        private String world;
        private String region;
        private String block;
        private String permission;
        private String rank;
        private String team;
        private String player;
        private String entity;
        private String item;
        private String profile;
        private String event;
        private final Set<String> tags = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder scope(MechanicScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder block(String block) {
            this.block = block;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder rank(String rank) {
            this.rank = rank;
            return this;
        }

        public Builder team(String team) {
            this.team = team;
            return this;
        }

        public Builder player(String player) {
            this.player = player;
            return this;
        }

        public Builder player(UUID player) {
            this.player = player == null ? null : player.toString();
            return this;
        }

        public Builder entity(String entity) {
            this.entity = entity;
            return this;
        }

        public Builder item(String item) {
            this.item = item;
            return this;
        }

        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder tag(String tag) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim());
            }
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags.clear();
            if (tags != null) {
                tags.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).forEach(this.tags::add);
            }
            return this;
        }

        public MechanicContext build() {
            return new MechanicContext(this);
        }
    }
}
