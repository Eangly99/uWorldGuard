// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.sk89q.worldguard.bukkit.BukkitConfigurationManager;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * The concrete configuration manager the shim hands out. Every world answers with the same
 * {@code WorldConfiguration} instance carrying WorldGuard's defaults — uWorldGuard's own settings do
 * not map onto WorldGuard's field-per-protection layout, so nothing is copied across.
 */
public final class CompatConfigurationManager extends BukkitConfigurationManager {

    public static final CompatConfigurationManager INSTANCE = new CompatConfigurationManager();

    private final BukkitWorldConfiguration shared = new BukkitWorldConfiguration(null, null, null);

    private CompatConfigurationManager() {
        super(null);
    }

    @Override
    public File getDataFolder() {
        return WgCompatBridge.plugin().getDataFolder();
    }

    @Override
    public BukkitWorldConfiguration get(final com.sk89q.worldedit.world.World world) {
        return shared;
    }

    @Override
    public BukkitWorldConfiguration get(final String worldName) {
        return shared;
    }

    @Override
    public Collection<BukkitWorldConfiguration> getWorldConfigs() {
        return List.of(shared);
    }

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    @Override
    public void disableUuidMigration() {
    }
}
