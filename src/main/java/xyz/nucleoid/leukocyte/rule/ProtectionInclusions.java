package xyz.nucleoid.leukocyte.rule;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.leukocyte.roles.PermissionAccessor;
import xyz.nucleoid.leukocyte.roles.RoleAccessor;
import xyz.nucleoid.leukocyte.util.ProtectionUtil;
import xyz.nucleoid.stimuli.filter.EventFilter;

import java.util.*;
import java.util.logging.Logger;

/**
 * Essentially the opposite of an exclusion, these are the people that ONLY get access to the rules in the claim.
 */
public final class ProtectionInclusions {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<ProtectionInclusions> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.listOf().fieldOf("roles").forGetter(inclusions -> new ArrayList<>(inclusions.roles)),
                Codec.STRING.listOf().optionalFieldOf("permissions", Collections.emptyList()).forGetter(inclusions -> new ArrayList<>(inclusions.permissions)),
                UUID_CODEC.listOf().fieldOf("players").forGetter(inclusions -> new ArrayList<>(inclusions.players)),
                Codec.BOOL.fieldOf("include_operators").forGetter(inclusions -> inclusions.includeOperators)
        ).apply(instance, ProtectionInclusions::new);
    });

    private final Set<String> roles;
    private final Set<UUID> players;

    private boolean includeOperators;
    private Set<String> permissions;

    public ProtectionInclusions() {
        this.roles = new ObjectOpenHashSet<>();
        this.permissions = new ObjectOpenHashSet<>();
        this.players = new ObjectOpenHashSet<>();
    }

    private ProtectionInclusions(Collection<String> roles, Collection<String> permissions, Collection<UUID> players, boolean includeOperators) {
        this.roles = new ObjectOpenHashSet<>(roles);
        this.players = new ObjectOpenHashSet<>(players);
        this.permissions = new ObjectOpenHashSet<>(permissions);
        this.includeOperators = includeOperators;
    }

    public void includeOperators() {
        this.includeOperators = true;
    }

    public boolean addRole(String role) {
        return this.roles.add(role);
    }

    public boolean removeRole(String role) {
        return this.roles.remove(role);
    }

    public boolean addPermission(String permission) { return this.permissions.add(permission); }

    public boolean removePermission(String permission) { return this.permissions.remove(permission); }

    public boolean addPlayer(GameProfile profile) {
        return this.players.add(profile.getId());
    }

    public boolean removePlayer(GameProfile profile) {
        return this.players.remove(profile.getId());
    }

    public boolean isIncluded(PlayerEntity player) {
        return ProtectionUtil.check(player, this.players, this.roles, this.permissions);
    }


    public ProtectionInclusions copy() {
        return new ProtectionInclusions(this.roles, this.permissions, this.players, this.includeOperators);
    }

    public boolean isEmpty() {
        return this.players.isEmpty() && this.permissions.isEmpty() && this.roles.isEmpty();
    }

    public Text displayList() {
        MutableText text = Text.literal("");
        for (var entry : this.players) {
            text = text.append(Text.literal("  " + entry).formatted(Formatting.AQUA)).append("\n");
        }

        return text;
    }
}
