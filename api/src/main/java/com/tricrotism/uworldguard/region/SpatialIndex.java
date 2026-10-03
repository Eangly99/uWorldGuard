package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Internal (storage backends): answers a {@link RegionManager}'s spatial lookups in place of its chunk
 * cache, and is told about every region the manager gains or loses. Plugins should not implement this.
 */
@NullMarked
public interface SpatialIndex {

    /**
     * {@code region} was added, replaced the region holding its id, or had its flags, group
     * qualifiers, priority or parent edited.
     */
    void put(ProtectedRegion region);

    void remove(ProtectedRegion region);

    /**
     * Regions whose bounding box holds the point, global region excluded, or an empty list. May
     * over-report: the manager tests bounds and exact containment on every candidate itself.
     *
     * <p>A non-empty list must be fresh and mutable: the manager takes ownership, filters it in place
     * and keeps it as the result set's region list rather than copying it.
     *
     * <p>A returned list that is also a {@link FlagResolver} lets flags at this block be resolved by
     * the backend instead of by walking the regions.
     */
    List<ProtectedRegion> candidatesAt(int x, int y, int z);

    /**
     * Regions whose bounding box meets the box (inclusive), global region excluded, in any order.
     */
    List<ProtectedRegion> intersecting(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /**
     * What {@link ApplicableRegionSet#queryState(StateFlag)} returns at every block of the box, or
     * {@code null} when the blocks may differ or the backend cannot prove they match.
     *
     * @param groups whether any region in the world carries a group qualifier
     */
    @Nullable State stateAcross(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, StateFlag flag, boolean groups);

    /**
     * Flag resolution at one block, answered from the backend's own copy of the regions there.
     */
    interface FlagResolver {

        /**
         * Whether the backend holds exactly the regions the manager keeps at this block and can answer
         * for {@code flag}. Nothing else here may be called for a flag this refuses.
         */
        boolean resolves(Flag<?> flag);

        /**
         * The explicit state, as {@link ApplicableRegionSet#queryExplicitState} defines it.
         *
         * @param groups whether any region in the world carries a group qualifier
         */
        @Nullable State resolveState(StateFlag flag, @Nullable UUID subject, boolean groups);

        /**
         * The highest priority region here that sets {@code flag} itself or through a parent, global
         * region excluded.
         */
        @Nullable ProtectedRegion highestSetting(Flag<?> flag);
    }
}
