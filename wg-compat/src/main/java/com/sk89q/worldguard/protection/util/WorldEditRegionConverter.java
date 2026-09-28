// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.util;

import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;

/**
 * Converts protected regions into WorldEdit regions and selections, with no world attached.
 *
 * <p>uWorldGuard's cylinders and spheres report {@link RegionType#POLYGON} through this API, so they
 * convert as the polygon their points describe.
 */
public final class WorldEditRegionConverter {

    private WorldEditRegionConverter() {
    }

    /**
     * @return the WorldEdit region, or {@code null} for the global region
     */
    public static Region convertToRegion(final ProtectedRegion region) {
        final RegionType type = region.getType();
        if (type == RegionType.CUBOID) {
            return new CuboidRegion(region.getMinimumPoint(), region.getMaximumPoint());
        }
        if (type == RegionType.POLYGON) {
            return new Polygonal2DRegion(null, region.getPoints(),
                region.getMinimumPoint().y(), region.getMaximumPoint().y());
        }
        return null;
    }

    /**
     * @return a selector holding the region's shape, or {@code null} for the global region
     */
    public static RegionSelector convertToSelector(final ProtectedRegion region) {
        final RegionType type = region.getType();
        if (type == RegionType.CUBOID) {
            return new CuboidRegionSelector(null, region.getMinimumPoint(), region.getMaximumPoint());
        }
        if (type == RegionType.POLYGON) {
            return new Polygonal2DRegionSelector(null, region.getPoints(),
                region.getMinimumPoint().y(), region.getMaximumPoint().y());
        }
        return null;
    }
}
