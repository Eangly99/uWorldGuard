// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.sk89q.worldguard.util.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Resolves players who are online right now. Field reads only, safe from any thread.
 */
public class BukkitPlayerService extends SingleRequestService {

    private static final BukkitPlayerService INSTANCE = new BukkitPlayerService();

    private BukkitPlayerService() {
    }

    public static BukkitPlayerService getInstance() {
        return INSTANCE;
    }

    @Override
    public int getIdealRequestLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Profile findByName(final String name) {
        final Player player = Bukkit.getPlayerExact(name);
        return player == null ? null : new Profile(player.getUniqueId(), player.getName());
    }

    @Override
    public Profile findByUuid(final UUID uuid) {
        final Player player = Bukkit.getPlayer(uuid);
        return player == null ? null : new Profile(uuid, player.getName());
    }
}
