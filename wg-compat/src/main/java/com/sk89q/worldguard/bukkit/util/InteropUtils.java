// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class InteropUtils {

    private InteropUtils() {
    }

    /**
     * Whether {@code player} is a server-side stand-in (an NPC or a mod's fake player) rather than
     * a connected client. Such players are not in the online player list under their own UUID.
     */
    public static boolean isFakePlayer(final Player player) {
        return player.hasMetadata("NPC") || Bukkit.getPlayer(player.getUniqueId()) != player;
    }
}
