package com.masuary.masucraftfixes.permissions;

import com.mojang.authlib.GameProfile;

/**
 * Vanilla refuses a full server in {@code PlayerList.canPlayerLogin} unless {@code canBypassPlayerLimit} allows the
 * profile, which on a dedicated server only reads {@code ops.json}. LuckPerms Forge loads the user during login
 * negotiation, which completes before that check.
 */
public final class PlayerLimitBypass {

    private PlayerLimitBypass() {
    }

    public static boolean canBypass(GameProfile profile) {
        return LuckPermsAccess.loadedUserHasPermission(profile, LuckPermsAccess.IGNORES_PLAYER_LIMIT_PERMISSION);
    }
}
