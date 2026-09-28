// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.sk89q.worldguard.bukkit.BukkitRegionContainer;
import com.sk89q.worldguard.protection.regions.RegionContainer;

/**
 * The concrete {@link RegionContainer} the shim hands to consumers. All behaviour lives in
 * {@link RegionContainer}; extending {@link BukkitRegionContainer} lets a consumer cast to the type
 * WorldGuard returns on Bukkit.
 */
public final class CompatRegionContainer extends BukkitRegionContainer {

    public static final CompatRegionContainer INSTANCE = new CompatRegionContainer();

    private CompatRegionContainer() {
        super(null);
    }
}
