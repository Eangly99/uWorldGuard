package com.sk89q.worldguard.protection;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.association.Associables;
import com.sk89q.worldguard.protection.association.RegionOverlapAssociation;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.tricrotism.uworldguard.wgcompat.RegionAdapters;
import com.tricrotism.uworldguard.wgcompat.WrappedRegionSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The WorldGuard-API resolution consumers reach through {@link FlagValueCalculator} and
 * {@link RegionResultSet}, the classes a consumer compiled against real WorldGuard links to.
 */
class FlagValueCalculatorTest {

    private static ProtectedRegion region(final String id, final int priority) {
        final ProtectedRegion region = new ProtectedCuboidRegion(id,
            BlockVector3.at(0, 0, 0), BlockVector3.at(16, 255, 16));
        region.setPriority(priority);
        return region;
    }

    @Test
    void higherPriorityWinsAndDenyBeatsAllowAtEqualPriority() {
        final ProtectedRegion low = region("low", 0);
        final ProtectedRegion highAllow = region("high-allow", 10);
        final ProtectedRegion highDeny = region("high-deny", 10);
        low.setFlag(Flags.PVP, StateFlag.State.DENY);
        highAllow.setFlag(Flags.PVP, StateFlag.State.ALLOW);

        final List<ProtectedRegion> onlyAllow = new ArrayList<>(List.of(low, highAllow));
        assertEquals(StateFlag.State.ALLOW,
            new RegionResultSet(onlyAllow, null).queryState(null, Flags.PVP));

        highDeny.setFlag(Flags.PVP, StateFlag.State.DENY);
        assertEquals(StateFlag.State.DENY,
            new RegionResultSet(List.of(low, highAllow, highDeny), null).queryState(null, Flags.PVP));
    }

    @Test
    void globalRegionAnswersWhenNoRegionSetsTheFlag() {
        final ProtectedRegion global = new GlobalProtectedRegion(ProtectedRegion.GLOBAL_REGION);
        global.setFlag(Flags.PVP, StateFlag.State.DENY);

        final FlagValueCalculator calculator = new FlagValueCalculator(List.of(region("plain", 0)), global);

        assertEquals(StateFlag.State.DENY, calculator.queryState(null, Flags.PVP));
        assertEquals(Integer.MIN_VALUE, calculator.getPriority(global));
    }

    @Test
    void membershipReportsNoRegionsFailAndSuccess() {
        assertEquals(FlagValueCalculator.Result.NO_REGIONS,
            new FlagValueCalculator(List.of(), null).getMembership(null));

        final FlagValueCalculator calculator = new FlagValueCalculator(List.of(region("claim", 0)), null);
        assertEquals(FlagValueCalculator.Result.FAIL,
            calculator.getMembership(Associables.constant(Association.NON_MEMBER)));
        assertEquals(FlagValueCalculator.Result.SUCCESS,
            calculator.getMembership(Associables.constant(Association.MEMBER)));
    }

    @Test
    void listConstructorSortsACopyAndLeavesTheCallersListAlone() {
        final ProtectedRegion low = region("low", 0);
        final ProtectedRegion high = region("high", 5);
        final List<ProtectedRegion> given = List.of(low, high);

        final RegionResultSet set = new RegionResultSet(given, null);

        assertSame(high, set.iterator().next());
        assertSame(low, given.get(0));
    }

    @Test
    void overlapAssociationOwnsOnlyRegionsItStandsIn() {
        final ProtectedRegion inside = region("inside", 0);
        final ProtectedRegion elsewhere = region("elsewhere", 0);
        final RegionOverlapAssociation piston = new RegionOverlapAssociation(Set.of(inside));

        assertEquals(Association.OWNER, piston.getAssociation(List.of(inside)));
        assertEquals(Association.NON_MEMBER, piston.getAssociation(List.of(elsewhere)));
    }

    @Test
    void sharedProtectionDomainCountsAsOwnership() {
        final ProtectedRegion source = region("source", 0);
        final ProtectedRegion target = region("target", 0);
        source.setFlag(Flags.NONPLAYER_PROTECTION_DOMAINS, Set.of("farm"));
        target.setFlag(Flags.NONPLAYER_PROTECTION_DOMAINS, Set.of("farm"));

        assertEquals(Association.OWNER,
            new RegionOverlapAssociation(Set.of(source)).getAssociation(List.of(target)));
    }

    private static com.tricrotism.uworldguard.region.ProtectedCuboidRegion engineRegion(final String id, final int priority) {
        final com.tricrotism.uworldguard.region.ProtectedCuboidRegion region =
            new com.tricrotism.uworldguard.region.ProtectedCuboidRegion(id,
                com.tricrotism.uworldguard.util.BlockVector3.at(0, 0, 0),
                com.tricrotism.uworldguard.util.BlockVector3.at(16, 255, 16));
        region.setPriority(priority);
        return region;
    }

    @Test
    void engineFastPathSkipsValuesQualifiedAwayFromTheSubject() {
        final com.tricrotism.uworldguard.region.RegionManager manager = new com.tricrotism.uworldguard.region.RegionManager();
        final com.tricrotism.uworldguard.region.ProtectedCuboidRegion low = engineRegion("low", 0);
        low.setFlag(com.tricrotism.uworldguard.flags.Flags.GREETING, "everyone");
        final com.tricrotism.uworldguard.region.ProtectedCuboidRegion high = engineRegion("high", 10);
        high.setFlag(com.tricrotism.uworldguard.flags.Flags.GREETING, "owners only");
        high.setFlagGroup(com.tricrotism.uworldguard.flags.Flags.GREETING,
            com.tricrotism.uworldguard.flags.RegionGroup.OWNERS);
        manager.addRegion(low);
        manager.addRegion(high);

        final WrappedRegionSet set = new WrappedRegionSet(manager.getApplicableRegions(8, 64, 8), manager);

        assertEquals("everyone", set.queryValue(null, Flags.GREET_MESSAGE));
        assertEquals(List.of("everyone"), List.copyOf(set.queryAllValues(null, Flags.GREET_MESSAGE)));
    }

    @Test
    void aParentFromAnotherWorldIsRefused() {
        final com.tricrotism.uworldguard.region.RegionManager here = new com.tricrotism.uworldguard.region.RegionManager();
        final com.tricrotism.uworldguard.region.RegionManager elsewhere = new com.tricrotism.uworldguard.region.RegionManager();
        final com.tricrotism.uworldguard.region.ProtectedCuboidRegion child = engineRegion("shop", 0);
        final com.tricrotism.uworldguard.region.ProtectedCuboidRegion parent = engineRegion("mall", 0);
        here.addRegion(child);
        elsewhere.addRegion(parent);

        final ProtectedRegion shimChild = RegionAdapters.region(child, here);
        final ProtectedRegion shimParent = RegionAdapters.region(parent, elsewhere);

        assertThrows(IllegalArgumentException.class, () -> shimChild.setParent(shimParent));
        assertNull(child.getParent());
    }

    @Test
    void engineFastPathFallsBackToTheGlobalRegionForAllValues() {
        final com.tricrotism.uworldguard.region.RegionManager manager = new com.tricrotism.uworldguard.region.RegionManager();
        final com.tricrotism.uworldguard.region.GlobalProtectedRegion global =
            new com.tricrotism.uworldguard.region.GlobalProtectedRegion();
        global.setFlag(com.tricrotism.uworldguard.flags.Flags.GREETING, "hello");
        manager.addRegion(global);
        manager.addRegion(engineRegion("plain", 0));

        final WrappedRegionSet set = new WrappedRegionSet(manager.getApplicableRegions(8, 64, 8), manager);

        assertEquals(List.of("hello"), List.copyOf(set.queryAllValues(null, Flags.GREET_MESSAGE)));
    }
}
