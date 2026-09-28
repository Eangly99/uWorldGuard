package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A manager with an index installed hands point lookups to it and tells it about every structural
 * edit, while still owning exact containment. This is the seam the UniverseSpigot backend plugs into.
 */
class SpatialIndexTest {

    /**
     * Answers with every region it knows about, whatever the point, so the tests can see the manager
     * filtering candidates itself.
     */
    private static final class EverythingIndex implements SpatialIndex {
        final Map<String, ProtectedRegion> known = new ConcurrentHashMap<>();
        int lookups;
        int puts;
        @Nullable RegionManager manager;
        boolean sawUnpublished;

        @Override
        public void put(final ProtectedRegion region) {
            puts++;
            if (manager != null && manager.getRegion(region.getId()) != region) {
                sawUnpublished = true;
            }
            known.put(region.getId(), region);
        }

        @Override
        public void remove(final ProtectedRegion region) {
            known.remove(region.getId());
        }

        @Override
        public List<ProtectedRegion> candidatesAt(final int x, final int y, final int z) {
            lookups++;
            final List<ProtectedRegion> out = new ArrayList<>();
            for (final ProtectedRegion region : known.values()) {
                if (!(region instanceof GlobalProtectedRegion)) {
                    out.add(region);
                }
            }
            return out;
        }

        @Override
        public List<ProtectedRegion> intersecting(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ
        ) {
            return candidatesAt(minX, minY, minZ);
        }

        @Override
        public @Nullable State stateAcross(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ,
            final StateFlag flag, final boolean groups
        ) {
            return null;
        }
    }

    private static ProtectedCuboidRegion cuboid(final String id, final int x1, final int z1, final int x2, final int z2) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(x1, 0, z1), BlockVector3.at(x2, 255, z2));
    }

    @Test
    void lookupsGoThroughTheIndex() {
        final RegionManager manager = new RegionManager();
        final EverythingIndex index = new EverythingIndex();
        manager.uwgUseIndex(index);
        manager.addRegion(cuboid("spawn", 0, 0, 32, 32));

        assertEquals(1, manager.getApplicableRegions(16, 64, 16).size());
        assertEquals(1, index.lookups);
    }

    @Test
    void overReportedCandidatesAreFilteredByContainment() {
        final RegionManager manager = new RegionManager();
        manager.uwgUseIndex(new EverythingIndex());
        manager.addRegion(cuboid("spawn", 0, 0, 32, 32));
        manager.addRegion(new ProtectedSphereRegion("dome", 100, 64, 100, 10, 4, 10));

        assertTrue(manager.getApplicableRegions(500, 64, 500).isEmpty());
        assertTrue(manager.getApplicableRegions(109, 67, 109).isEmpty(),
            "inside the dome's bounding box but outside the ellipsoid");
        assertEquals(1, manager.getApplicableRegions(100, 64, 100).size());
    }

    @Test
    void everyStructuralEditReachesTheIndex() {
        final RegionManager manager = new RegionManager();
        final EverythingIndex index = new EverythingIndex();
        manager.uwgUseIndex(index);

        manager.addRegion(cuboid("a", 0, 0, 8, 8));
        assertNull(manager.addRegionIfAbsent(cuboid("b", 0, 0, 8, 8)));
        final ProtectedCuboidRegion wider = cuboid("a", 0, 0, 64, 64);
        manager.redefineRegion(wider);
        assertSame(wider, index.known.get("a"));

        manager.removeRegion("b");
        assertFalse(index.known.containsKey("b"));
    }

    @Test
    void editsToALiveRegionReachTheIndex() {
        final RegionManager manager = new RegionManager();
        final EverythingIndex index = new EverythingIndex();
        manager.uwgUseIndex(index);
        final ProtectedCuboidRegion parent = cuboid("parent", 0, 0, 64, 64);
        final ProtectedCuboidRegion child = cuboid("child", 0, 0, 8, 8);
        manager.addRegion(parent);
        manager.addRegion(child);
        index.puts = 0;

        child.setFlag(Flags.PVP, State.DENY);
        child.setFlagGroup(Flags.PVP, RegionGroup.NON_MEMBERS);
        child.setPriority(5);
        child.setParent(parent);

        assertEquals(4, index.puts);
    }

    /**
     * Redefine copies state into the replacement inside the map's compute. An index told about the
     * replacement from there could call back into the manager for that same id, which the map rejects
     * as a recursive update, so the index must only hear of it once it is published.
     */
    @Test
    void redefineTellsTheIndexOnlyAfterTheSwap() {
        final RegionManager manager = new RegionManager();
        final EverythingIndex index = new EverythingIndex();
        manager.uwgUseIndex(index);
        index.manager = manager;
        final ProtectedCuboidRegion parent = cuboid("parent", 0, 0, 128, 128);
        final ProtectedCuboidRegion plot = cuboid("plot", 0, 0, 8, 8);
        manager.addRegion(parent);
        manager.addRegion(plot);
        plot.setParent(parent);

        manager.redefineRegion(cuboid("plot", 0, 0, 32, 32));

        assertFalse(index.sawUnpublished);
    }

    @Test
    void removingTheIndexFallsBackToTheChunkCache() {
        final RegionManager manager = new RegionManager();
        final EverythingIndex index = new EverythingIndex();
        manager.uwgUseIndex(index);
        manager.addRegion(cuboid("spawn", 0, 0, 32, 32));
        manager.uwgUseIndex(null);

        assertEquals(1, manager.getApplicableRegions(16, 64, 16).size());
        assertEquals(0, index.lookups);
    }
}
