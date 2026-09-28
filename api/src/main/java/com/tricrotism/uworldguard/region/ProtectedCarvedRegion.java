package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A shape with named holes cut out: a plot around a courtyard, an arena without its stands, or a
 * claim with a public path through it. A block is inside when the solid holds it and no hole does.
 *
 * <p>This differs from an overlapping region with opposite flags. A hole is not part of this region
 * at all, so it inherits nothing from it and a query there does not see it.
 *
 * <p>Carving a carved region merges its holes into the new one, so the solid is never itself carved.
 * Holes only supply geometry, as a {@link ProtectedCompositeRegion}'s parts do.
 */
@NullMarked
public final class ProtectedCarvedRegion extends ProtectedRegion {

    private final ProtectedRegion solid;
    private final Map<String, ProtectedRegion> holes;
    private final ProtectedRegion[] holeArray;
    private final int[] holeBounds;

    /**
     * @throws IllegalArgumentException if there are no holes, or the solid or a hole is a global region
     */
    public ProtectedCarvedRegion(final String id, final ProtectedRegion solid, final Map<String, ? extends ProtectedRegion> holes) {
        super(id);
        if (holes.isEmpty()) {
            throw new IllegalArgumentException("A carved region needs at least one hole");
        }
        final Map<String, ProtectedRegion> merged = new LinkedHashMap<>();
        ProtectedRegion base = solid;
        if (solid instanceof ProtectedCarvedRegion carved) {
            base = carved.solid;
            merged.putAll(carved.holes);
        }
        merged.putAll(holes);
        if (base instanceof GlobalProtectedRegion) {
            throw new IllegalArgumentException("A carved region's solid cannot be a global region");
        }
        for (final ProtectedRegion hole : merged.values()) {
            if (hole instanceof GlobalProtectedRegion) {
                throw new IllegalArgumentException("A hole cannot be a global region");
            }
        }
        this.solid = base;
        this.holes = Collections.unmodifiableMap(merged);
        this.holeArray = merged.values().toArray(new ProtectedRegion[0]);
        this.holeBounds = ProtectedCompositeRegion.bounds(holeArray);
    }

    @Override
    public RegionType getType() {
        return RegionType.CARVED;
    }

    @Override
    public boolean contains(final int x, final int y, final int z) {
        if (!solid.contains(x, y, z)) {
            return false;
        }
        final int[] b = holeBounds;
        for (int i = 0; i < holeArray.length; i++) {
            if (ProtectedCompositeRegion.inBounds(b, i, x, y, z) && holeArray[i].contains(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The solid's bounds. A hole only takes blocks away, so they still hold everything left.
     */
    @Override
    public BlockVector3 getMinimumPoint() {
        return solid.getMinimumPoint();
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return solid.getMaximumPoint();
    }

    /**
     * The shape the holes are cut from. Never itself a carved region.
     */
    public ProtectedRegion getSolid() {
        return solid;
    }

    /**
     * The holes by name, in the order given.
     */
    public Map<String, ProtectedRegion> getHoles() {
        return holes;
    }
}
