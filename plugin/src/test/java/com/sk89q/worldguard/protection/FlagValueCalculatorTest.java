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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
