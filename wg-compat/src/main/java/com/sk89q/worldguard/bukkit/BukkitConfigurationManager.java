// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldguard.config.YamlConfigurationManager;

import java.util.Collection;

/**
 * The global configuration type WorldGuard hands out on Bukkit: what
 * {@code WorldGuardPlugin.getConfigManager()} returns and what consumers cast
 * {@code getGlobalStateManager()} to. The instance is
 * {@code com.tricrotism.uworldguard.wgcompat.CompatConfigurationManager}.
 *
 * <p>CommandBook god mode integration is not present, so {@link #hasCommandBookGodMode()} is always
 * {@code false}.
 */
public abstract class BukkitConfigurationManager extends YamlConfigurationManager {

    /**
     * {@code plugin} is not used: nothing is loaded from WorldGuard's files.
     */
    public BukkitConfigurationManager(final WorldGuardPlugin plugin) {
    }

    public abstract Collection<BukkitWorldConfiguration> getWorldConfigs();

    @Override
    public abstract BukkitWorldConfiguration get(com.sk89q.worldedit.world.World world);

    public abstract BukkitWorldConfiguration get(String worldName);

    @Override
    public void copyDefaults() {
    }

    public void updateCommandBookGodMode() {
    }

    public boolean hasCommandBookGodMode() {
        return false;
    }
}
