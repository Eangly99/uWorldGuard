// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.entity.metadata.EntityProperties;

public class Entities {

    private Entities() {
    }

    /**
     * Whether the entity is a kind that exists in large numbers and is cheap to lose: dropped items,
     * experience orbs, falling blocks, primed TNT, paintings and item frames.
     */
    public static boolean isIntensiveEntity(final Entity entity) {
        final EntityProperties properties = entity.getFacet(EntityProperties.class);
        return properties != null
            && (properties.isItem()
            || properties.isExperienceOrb()
            || properties.isFallingBlock()
            || properties.isTNT()
            || properties.isPainting()
            || properties.isItemFrame());
    }
}
