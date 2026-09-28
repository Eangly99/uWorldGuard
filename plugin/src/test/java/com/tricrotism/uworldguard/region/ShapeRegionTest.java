package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.storage.RegionSerializer;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shapes UniverseSpigot has that uWorldGuard gained with it: the convex polyhedron, the composite
 * made of named parts, and the carved solid with named holes.
 */
class ShapeRegionTest {

    private static ProtectedCuboidRegion cuboid(final String id, final int x1, final int y1, final int z1,
                                                final int x2, final int y2, final int z2) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    private static List<BlockVector3> points(final int... xyz) {
        final List<BlockVector3> out = new ArrayList<>();
        for (int i = 0; i < xyz.length; i += 3) {
            out.add(BlockVector3.at(xyz[i], xyz[i + 1], xyz[i + 2]));
        }
        return out;
    }

    @Test
    void tetrahedronHoldsItsInsideAndFacesOnly() {
        final ProtectedPolyhedronRegion tetra = new ProtectedPolyhedronRegion("t",
            points(0, 0, 0, 10, 0, 0, 0, 10, 0, 0, 0, 10));

        assertTrue(tetra.contains(0, 0, 0));
        assertTrue(tetra.contains(3, 3, 3), "x+y+z = 9, inside the slanted face");
        assertTrue(tetra.contains(5, 5, 0), "on a face");
        assertFalse(tetra.contains(4, 4, 4), "x+y+z = 12, past the slanted face");
        assertFalse(tetra.contains(-1, 0, 0));
    }

    @Test
    void pointsInsideTheHullAreDropped() {
        final List<BlockVector3> pts = points(
            0, 0, 0, 10, 0, 0, 0, 10, 0, 10, 10, 0, 0, 0, 10, 10, 0, 10, 0, 10, 10, 10, 10, 10,
            5, 5, 5, 3, 7, 2, 5, 0, 5);
        final ProtectedPolyhedronRegion cube = new ProtectedPolyhedronRegion("c", pts);

        assertEquals(8, cube.getPoints().size());
        assertTrue(cube.contains(10, 10, 10));
        assertFalse(cube.contains(11, 5, 5));
    }

    /**
     * The hull of any point set holds every one of those points, and nothing outside their box.
     */
    @Test
    void randomHullsHoldEveryPointTheyWereBuiltFrom() {
        final Random random = new Random(42);
        for (int round = 0; round < 50; round++) {
            final List<BlockVector3> pts = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                pts.add(BlockVector3.at(random.nextInt(200) - 100, random.nextInt(200) - 100, random.nextInt(200) - 100));
            }
            final ProtectedPolyhedronRegion hull = new ProtectedPolyhedronRegion("r", pts);
            for (final BlockVector3 p : pts) {
                assertTrue(hull.contains(p.x(), p.y(), p.z()), "round " + round + " lost " + p);
            }
            assertFalse(hull.contains(101, 0, 0));
            final ProtectedPolyhedronRegion rebuilt = new ProtectedPolyhedronRegion("r", hull.getPoints());
            for (int i = 0; i < 500; i++) {
                final int x = random.nextInt(220) - 110;
                final int y = random.nextInt(220) - 110;
                final int z = random.nextInt(220) - 110;
                assertEquals(hull.contains(x, y, z), rebuilt.contains(x, y, z), "corners rebuild the same hull");
            }
        }
    }

    @Test
    void flatOrTooFewPointsAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProtectedPolyhedronRegion("f", points(0, 0, 0, 10, 0, 0, 0, 0, 10, 10, 0, 10)));
        assertThrows(IllegalArgumentException.class,
            () -> new ProtectedPolyhedronRegion("f", points(0, 0, 0, 1, 1, 1, 2, 2, 2)));
        assertThrows(IllegalArgumentException.class,
            () -> new ProtectedPolyhedronRegion("f", points(0, 0, 0, 2_000_000, 0, 0, 0, 5, 0, 0, 0, 5)));
    }

    @Test
    void compositeHoldsAnyPartAndNothingBetween() {
        final Map<String, ProtectedRegion> parts = new LinkedHashMap<>();
        parts.put("west", cuboid("west", 0, 0, 0, 10, 10, 10));
        parts.put("east", cuboid("east", 20, 0, 0, 30, 10, 10));
        final ProtectedCompositeRegion plot = new ProtectedCompositeRegion("plot", parts);

        assertTrue(plot.contains(5, 5, 5));
        assertTrue(plot.contains(25, 5, 5));
        assertFalse(plot.contains(15, 5, 5), "the gap between the parts");
        assertEquals(BlockVector3.at(0, 0, 0), plot.getMinimumPoint());
        assertEquals(BlockVector3.at(30, 10, 10), plot.getMaximumPoint());

        final RegionManager manager = new RegionManager();
        manager.addRegion(plot);
        assertEquals(1, manager.getApplicableRegions(25, 5, 5).size());
        assertTrue(manager.getApplicableRegions(15, 5, 5).isEmpty());
    }

    @Test
    void carvedExcludesItsHolesAndMergesWhenCarvedAgain() {
        final ProtectedCarvedRegion yard = new ProtectedCarvedRegion("yard",
            cuboid("s", 0, 0, 0, 20, 20, 20), Map.of("pond", cuboid("pond", 5, 5, 5, 10, 10, 10)));

        assertTrue(yard.contains(2, 2, 2));
        assertFalse(yard.contains(7, 7, 7), "inside the hole");
        assertFalse(yard.contains(21, 2, 2));

        final ProtectedCarvedRegion twice = new ProtectedCarvedRegion("yard", yard,
            Map.of("path", cuboid("path", 15, 0, 0, 16, 20, 20)));
        assertInstanceOf(ProtectedCuboidRegion.class, twice.getSolid());
        assertEquals(2, twice.getHoles().size());
        assertFalse(twice.contains(7, 7, 7));
        assertFalse(twice.contains(15, 3, 3));
    }

    @Test
    void globalRegionsCannotBePartsOrSolids() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProtectedCompositeRegion("x", Map.of("all", new GlobalProtectedRegion())));
        assertThrows(IllegalArgumentException.class,
            () -> new ProtectedCarvedRegion("x", new GlobalProtectedRegion(), Map.of("h", cuboid("h", 0, 0, 0, 1, 1, 1))));
    }

    @Test
    void everyNewShapeSurvivesAYamlRoundTrip() throws Exception {
        final RegionManager manager = new RegionManager();
        manager.addRegion(new ProtectedPolyhedronRegion("gem", points(0, 0, 0, 10, 0, 0, 0, 10, 0, 0, 0, 10, 10, 10, 10)));
        final Map<String, ProtectedRegion> parts = new LinkedHashMap<>();
        parts.put("pit", cuboid("pit", 40, 0, 40, 50, 20, 50));
        parts.put("ring", new ProtectedSphereRegion("ring", 45, 30, 45, 8, 3, 8));
        manager.addRegion(new ProtectedCompositeRegion("mine", parts));
        manager.addRegion(new ProtectedCarvedRegion("arena", cuboid("s", 100, 0, 100, 140, 30, 140),
            Map.of("stands", new ProtectedPolyhedronRegion("stands", points(
                110, 0, 110, 130, 0, 110, 110, 0, 130, 120, 20, 120)))));

        final RegionSerializer serializer = new RegionSerializer();
        final RegionManager loaded = new RegionManager();
        serializer.fromYaml(serializer.toYaml(manager), loaded);

        final Random random = new Random(7);
        for (final String id : List.of("gem", "mine", "arena")) {
            final ProtectedRegion before = manager.getRegion(id);
            final ProtectedRegion after = loaded.getRegion(id);
            assertNotNull(after, id);
            assertEquals(before.getType(), after.getType());
            final BlockVector3 lo = before.getMinimumPoint();
            final BlockVector3 hi = before.getMaximumPoint();
            for (int i = 0; i < 2000; i++) {
                final int x = lo.x() - 1 + random.nextInt(hi.x() - lo.x() + 3);
                final int y = lo.y() - 1 + random.nextInt(hi.y() - lo.y() + 3);
                final int z = lo.z() - 1 + random.nextInt(hi.z() - lo.z() + 3);
                assertEquals(before.contains(x, y, z), after.contains(x, y, z), id + " at " + x + "," + y + "," + z);
            }
        }
    }
}
