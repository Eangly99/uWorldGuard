// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection;

import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.StateFlag;

/**
 * Base for {@link ApplicableRegionSet} implementations: the state queries resolve through
 * {@link #queryValue}, with {@code DENY} from any flag winning.
 */
public abstract class AbstractRegionSet implements ApplicableRegionSet {

    public AbstractRegionSet() {
    }

    @Override
    public boolean testState(final RegionAssociable subject, final StateFlag... flags) {
        return queryState(subject, flags) == StateFlag.State.ALLOW;
    }

    @Override
    public StateFlag.State queryState(final RegionAssociable subject, final StateFlag... flags) {
        StateFlag.State result = null;
        for (int i = 0; i < flags.length; i++) {
            final StateFlag.State state = queryValue(subject, flags[i]);
            if (state == StateFlag.State.DENY) {
                return StateFlag.State.DENY;
            }
            if (state == StateFlag.State.ALLOW) {
                result = StateFlag.State.ALLOW;
            }
        }
        return result;
    }
}
