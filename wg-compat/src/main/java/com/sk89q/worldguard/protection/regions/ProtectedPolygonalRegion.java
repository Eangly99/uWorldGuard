// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.regions;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2D footprint extruded between two Y values, backed by a uWorldGuard
 * {@code com.tricrotism.uworldguard.region.ProtectedPolygonRegion}.
 *
 * <p>This class also fronts uWorldGuard's cylinder, sphere, polyhedron, composite and carved regions,
 * which WorldGuard's API has no constant for. Those report {@link RegionType#POLYGON} and answer
 * {@link #getPoints()} with their four bounding-box corners, since their true outline is not a 2D
 * vertex list. Containment still uses the exact shape.
 */
public class ProtectedPolygonalRegion extends ProtectedRegion {

    public ProtectedPolygonalRegion(final String id, final List<BlockVector2> points,
                                    final int minY, final int maxY) {
        this(id, false, points, minY, maxY);
    }

    public ProtectedPolygonalRegion(final String id, final boolean transientRegion,
                                    final List<BlockVector2> points, final int minY, final int maxY) {
        super(new com.tricrotism.uworldguard.region.ProtectedPolygonRegion(id,
            toEnginePoints(points, minY), minY, maxY), transientRegion);
    }

    /**
     * Internal: adopts an existing engine polygon, cylinder or sphere.
     */
    public ProtectedPolygonalRegion(final com.tricrotism.uworldguard.region.ProtectedRegion backing) {
        super(backing, false);
    }

    @Override
    public RegionType getType() {
        return RegionType.POLYGON;
    }

    @Override
    public List<BlockVector2> getPoints() {
        if (uwgBacking() instanceof com.tricrotism.uworldguard.region.ProtectedPolygonRegion polygon) {
            final List<com.tricrotism.uworldguard.util.BlockVector3> enginePoints = polygon.getPoints();
            final List<BlockVector2> points = new ArrayList<>(enginePoints.size());
            for (int i = 0, n = enginePoints.size(); i < n; i++) {
                final com.tricrotism.uworldguard.util.BlockVector3 point = enginePoints.get(i);
                points.add(BlockVector2.at(point.x(), point.z()));
            }
            return points;
        }
        final BlockVector3 lo = getMinimumPoint();
        final BlockVector3 hi = getMaximumPoint();
        return List.of(
            BlockVector2.at(lo.x(), lo.z()),
            BlockVector2.at(hi.x(), lo.z()),
            BlockVector2.at(hi.x(), hi.z()),
            BlockVector2.at(lo.x(), hi.z()));
    }

    @Override
    public boolean contains(final BlockVector3 position) {
        return contains(position.x(), position.y(), position.z());
    }

    @Override
    public boolean isPhysicalArea() {
        return true;
    }

    /**
     * Bounding-box volume. The engine exposes no area for a polygon, cylinder or sphere, so this is
     * an upper bound rather than the exact block count.
     */
    @Override
    public int volume() {
        final BlockVector3 lo = getMinimumPoint();
        final BlockVector3 hi = getMaximumPoint();
        final long volume = ((long) hi.x() - lo.x() + 1L)
            * ((long) hi.y() - lo.y() + 1L)
            * ((long) hi.z() - lo.z() + 1L);
        return volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
    }

    private static List<com.tricrotism.uworldguard.util.BlockVector3> toEnginePoints(
        final List<BlockVector2> points, final int minY) {
        final List<com.tricrotism.uworldguard.util.BlockVector3> out = new ArrayList<>(points.size());
        for (int i = 0, n = points.size(); i < n; i++) {
            final BlockVector2 point = points.get(i);
            out.add(com.tricrotism.uworldguard.util.BlockVector3.at(point.x(), minY, point.z()));
        }
        return out;
    }
}
