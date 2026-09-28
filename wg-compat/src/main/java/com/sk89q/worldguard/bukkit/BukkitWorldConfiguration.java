// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.config.YamlWorldConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;

/**
 * The per-world configuration type WorldGuard hands out on Bukkit, which consumers cast
 * {@code ConfigurationManager.get(world)} to. Carries WorldGuard's defaults like its superclass.
 *
 * <p>Sign chest protection is a WorldGuard feature uWorldGuard does not run, so the
 * {@code isChestProtected*} checks answer {@code false}. The {@code allowAllInteract} and
 * {@code blockUseAtFeet} matcher sets and {@code getChestProtection()} are not shipped: they
 * reference WorldGuard-internal types this layer does not provide.
 */
public class BukkitWorldConfiguration extends YamlWorldConfiguration {

    public Set<PotionEffectType> blockPotions = new HashSet<>(0);
    public boolean usePaperEntityOrigin;

    /**
     * {@code plugin} and {@code worldName} are not used, since nothing is read from a file.
     */
    public BukkitWorldConfiguration(final WorldGuardPlugin plugin, final String worldName,
                                    final YAMLProcessor parentConfig) {
        this.parentConfig = parentConfig;
    }

    @Override
    public void loadConfiguration() {
    }

    public boolean isChestProtected(final Location location, final LocalPlayer player) {
        return false;
    }

    public boolean isChestProtected(final Location location) {
        return false;
    }

    public boolean isChestProtectedPlacement(final Location location, final LocalPlayer player) {
        return false;
    }

    public boolean isAdjacentChestProtected(final Location location, final LocalPlayer player) {
        return false;
    }
}
