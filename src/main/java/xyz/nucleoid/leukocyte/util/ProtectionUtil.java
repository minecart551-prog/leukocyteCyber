package xyz.nucleoid.leukocyte.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.leukocyte.roles.PermissionAccessor;
import xyz.nucleoid.leukocyte.roles.RoleAccessor;

import java.util.Set;
import java.util.UUID;

public final class ProtectionUtil {
    /**
     * Move the check here for common usage
     */
    public static boolean check(PlayerEntity player, Set<UUID> players, Set<String> roles, Set<String> permissions) {
        if (players.contains(player.getUuid())) {
            return true;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            for (var userRole : roles) {
                if (RoleAccessor.INSTANCE.hasRole(serverPlayer, userRole)) {
                    return true;
                }
            }

            for (var excludePermission : permissions) {
                if (PermissionAccessor.INSTANCE.hasPermission(serverPlayer, excludePermission)) {
                    return true;
                }
            }
        }

        return false;
    }
}
