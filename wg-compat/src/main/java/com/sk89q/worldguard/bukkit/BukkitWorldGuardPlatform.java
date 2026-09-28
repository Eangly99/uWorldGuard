// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldguard.internal.platform.StringMatcher;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;

/**
 * The platform type WorldGuard installs on Bukkit, which consumers cast
 * {@code WorldGuard.getInstance().getPlatform()} to. The instance is
 * {@code com.tricrotism.uworldguard.wgcompat.UwgPlatform}.
 *
 * <p>{@code getDebugHandler()} and {@code addPlatformReports(...)} are not shipped: they reference
 * WorldGuard's command and report types, which this layer does not provide.
 */
public abstract class BukkitWorldGuardPlatform implements WorldGuardPlatform {

    private static final StringMatcher MATCHER = new BukkitStringMatcher();

    public BukkitWorldGuardPlatform() {
    }

    @Override
    public abstract BukkitConfigurationManager getGlobalStateManager();

    public StringMatcher getMatcher() {
        return MATCHER;
    }
}
