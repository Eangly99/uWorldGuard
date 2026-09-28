package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Several named shapes that make up one area under one id and one set of flags, owners and members:
 * a plot in two pieces, or a mine made of a pit and a surface ring. A block is inside when any part
 * holds it.
 *
 * <p>Parts only supply geometry. Their own ids, flags, owners and members are never read. Parts are
 * named so a part can be replaced or dropped without rebuilding the rest.
 */
@NullMarked
public final class ProtectedCompositeRegion extends ProtectedRegion {

    private final Map<String, ProtectedRegion> parts;
    private final ProtectedRegion[] partArray;
    private final int[] partBounds;
    private final BlockVector3 min;
    private final BlockVector3 max;

    /**
     * @throws IllegalArgumentException if there are no parts, or a part is a global region
     */
    public ProtectedCompositeRegion(final String id, final Map<String, ? extends ProtectedRegion> parts) {
        super(id);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Composite needs at least one part");
        }
        this.parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        this.partArray = this.parts.values().toArray(new ProtectedRegion[0]);
        int loX = Integer.MAX_VALUE;
        int loY = Integer.MAX_VALUE;
        int loZ = Integer.MAX_VALUE;
        int hiX = Integer.MIN_VALUE;
        int hiY = Integer.MIN_VALUE;
        int hiZ = Integer.MIN_VALUE;
        for (final ProtectedRegion part : partArray) {
            if (part instanceof GlobalProtectedRegion) {
                throw new IllegalArgumentException("A composite part cannot be a global region");
            }
            final BlockVector3 lo = part.getMinimumPoint();
            final BlockVector3 hi = part.getMaximumPoint();
            loX = Math.min(loX, lo.x());
            loY = Math.min(loY, lo.y());
            loZ = Math.min(loZ, lo.z());
            hiX = Math.max(hiX, hi.x());
            hiY = Math.max(hiY, hi.y());
            hiZ = Math.max(hiZ, hi.z());
        }
        this.min = BlockVector3.at(loX, loY, loZ);
        this.max = BlockVector3.at(hiX, hiY, hiZ);
        this.partBounds = bounds(partArray);
    }

    @Override
    public RegionType getType() {
        return RegionType.COMPOSITE;
    }

    @Override
    public boolean contains(final int x, final int y, final int z) {
        final int[] b = partBounds;
        for (int i = 0; i < partArray.length; i++) {
            if (inBounds(b, i, x, y, z) && partArray[i].contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Each region's box as six ints, min then max, so a lookup tests boxes in one flat array rather
     * than through two vector objects per region.
     */
    static int[] bounds(final ProtectedRegion[] regions) {
        final int[] out = new int[regions.length * 6];
        for (int i = 0; i < regions.length; i++) {
            final BlockVector3 lo = regions[i].getMinimumPoint();
            final BlockVector3 hi = regions[i].getMaximumPoint();
            final int o = i * 6;
            out[o] = lo.x();
            out[o + 1] = lo.y();
            out[o + 2] = lo.z();
            out[o + 3] = hi.x();
            out[o + 4] = hi.y();
            out[o + 5] = hi.z();
        }
        return out;
    }

    static boolean inBounds(final int[] bounds, final int i, final int x, final int y, final int z) {
        final int o = i * 6;
        return x >= bounds[o] && x <= bounds[o + 3] && y >= bounds[o + 1] && y <= bounds[o + 4]
            && z >= bounds[o + 2] && z <= bounds[o + 5];
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return min;
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return max;
    }

    /**
     * The parts by name, in the order given.
     */
    public Map<String, ProtectedRegion> getParts() {
        return parts;
    }
}
