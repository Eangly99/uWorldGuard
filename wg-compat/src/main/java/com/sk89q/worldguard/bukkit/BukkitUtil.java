// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Bukkit item helpers. The {@code createTarget(...)} overloads are not shipped: they build
 * WorldGuard blacklist targets, a feature uWorldGuard does not run.
 */
public class BukkitUtil {

    private BukkitUtil() {
    }

    public static boolean isWaterPotion(final ItemStack item) {
        return item.getType() == Material.POTION
            && item.getItemMeta() instanceof PotionMeta meta
            && meta.getBasePotionType() == PotionType.WATER;
    }

    /**
     * Always {@code 0}: potions stopped encoding their effect in the item's damage value when item
     * data components replaced it, so there are no bits to read.
     */
    public static int getPotionEffectBits(final ItemStack item) {
        return 0;
    }
}
