package com.tricrotism.uworldguard.commands;

import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the replacement region for the part and hole commands. Every result is a fresh region that
 * only carries geometry, so the redefine path can copy the live region's settings onto it.
 */
@NullMarked final class RegionShapes {

    /**
     * The name the existing shape takes when a plain region is turned into a composite.
     */
    static final String MAIN_PART = "main";

    private RegionShapes() {}

    /**
     * A geometry-only copy of {@code region} under {@code id}. The live region cannot be reused as a
     * part or solid because it carries flags and members and stays registered until the swap.
     *
     * @throws IllegalArgumentException for the global region, which has no shape
     */
    static ProtectedRegion copy(final String id, final ProtectedRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return switch (region) {
            case ProtectedCuboidRegion _ -> new ProtectedCuboidRegion(id, min, max);
            case ProtectedPolygonRegion p -> new ProtectedPolygonRegion(id, p.getPoints(), min.y(), max.y());
            case ProtectedCylinderRegion _ -> new ProtectedCylinderRegion(id,
                (min.x() + max.x()) / 2, (min.z() + max.z()) / 2,
                (max.x() - min.x()) / 2, (max.z() - min.z()) / 2, min.y(), max.y());
            case ProtectedSphereRegion _ -> new ProtectedSphereRegion(id,
                (min.x() + max.x()) / 2, (min.y() + max.y()) / 2, (min.z() + max.z()) / 2,
                (max.x() - min.x()) / 2, (max.y() - min.y()) / 2, (max.z() - min.z()) / 2);
            case ProtectedPolyhedronRegion p -> new ProtectedPolyhedronRegion(id, p.getPoints());
            case ProtectedCompositeRegion c -> new ProtectedCompositeRegion(id, c.getParts());
            case ProtectedCarvedRegion c -> new ProtectedCarvedRegion(id, c.getSolid(), c.getHoles());
            default -> throw new IllegalArgumentException("The global region has no shape");
        };
    }

    /**
     * {@code region} with {@code shape} added as part {@code name}, replacing a part of that name. A
     * region that is not a composite yet keeps its current shape as the part {@link #MAIN_PART}.
     */
    static ProtectedCompositeRegion withPart(final ProtectedRegion region, final String name, final ProtectedRegion shape) {
        final Map<String, ProtectedRegion> parts = new LinkedHashMap<>();
        if (region instanceof ProtectedCompositeRegion composite) {
            parts.putAll(composite.getParts());
        } else {
            parts.put(MAIN_PART, copy(MAIN_PART, region));
        }
        parts.put(name, shape);
        return new ProtectedCompositeRegion(region.getId(), parts);
    }

    /**
     * {@code composite} without part {@code name}.
     *
     * @throws IllegalArgumentException if that is its only part
     */
    static ProtectedCompositeRegion withoutPart(final ProtectedCompositeRegion composite, final String name) {
        final Map<String, ProtectedRegion> parts = new LinkedHashMap<>(composite.getParts());
        parts.remove(name);
        return new ProtectedCompositeRegion(composite.getId(), parts);
    }

    /**
     * {@code region} with {@code shape} cut out as hole {@code name}, replacing a hole of that name.
     * A carved region is handed to the constructor as-is, which merges its holes rather than nesting it.
     */
    static ProtectedCarvedRegion withHole(final ProtectedRegion region, final String name, final ProtectedRegion shape) {
        final ProtectedRegion solid = region instanceof ProtectedCarvedRegion ? region : copy(region.getId(), region);
        return new ProtectedCarvedRegion(region.getId(), solid, Map.of(name, shape));
    }

    /**
     * {@code carved} without hole {@code name}. Removing the last hole leaves the plain solid.
     */
    static ProtectedRegion withoutHole(final ProtectedCarvedRegion carved, final String name) {
        final Map<String, ProtectedRegion> holes = new LinkedHashMap<>(carved.getHoles());
        holes.remove(name);
        return holes.isEmpty()
            ? copy(carved.getId(), carved.getSolid())
            : new ProtectedCarvedRegion(carved.getId(), carved.getSolid(), holes);
    }
}
