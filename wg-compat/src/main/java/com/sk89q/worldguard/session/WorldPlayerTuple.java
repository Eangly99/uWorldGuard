// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session;

import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;

import java.util.Objects;

/**
 * A world and player pair, usable as a map key.
 */
public class WorldPlayerTuple {

    private final World world;
    private final LocalPlayer player;

    public WorldPlayerTuple(final World world, final LocalPlayer player) {
        this.world = world;
        this.player = player;
    }

    public World getWorld() {
        return world;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof WorldPlayerTuple tuple
            && Objects.equals(world, tuple.world)
            && Objects.equals(player, tuple.player);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(world) + Objects.hashCode(player);
    }
}
