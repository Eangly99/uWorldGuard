// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldguard.protection.regions.RegionContainer;

/**
 * The region container type WorldGuard hands out on Bukkit, which consumers cast
 * {@code getRegionContainer()} to. The instance is
 * {@code com.tricrotism.uworldguard.wgcompat.CompatRegionContainer}; uWorldGuard owns the container
 * lifecycle, so {@link #initialize()} and {@link #shutdown()} do nothing.
 */
public abstract class BukkitRegionContainer extends RegionContainer {

    /**
     * {@code plugin} is not used: uWorldGuard's container is already bound.
     */
    public BukkitRegionContainer(final WorldGuardPlugin plugin) {
    }

    @Override
    public void initialize() {
    }

    public void shutdown() {
    }
}
