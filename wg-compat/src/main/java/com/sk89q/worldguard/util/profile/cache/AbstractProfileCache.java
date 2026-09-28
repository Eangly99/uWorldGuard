// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.cache;

import com.google.common.collect.ImmutableMap;
import com.sk89q.worldguard.util.profile.Profile;

import java.util.List;
import java.util.UUID;

abstract class AbstractProfileCache implements ProfileCache {

    @Override
    public void put(final Profile profile) {
        putAll(List.of(profile));
    }

    @Override
    public Profile getIfPresent(final UUID uuid) {
        final ImmutableMap<UUID, Profile> found = getAllPresent(List.of(uuid));
        return found.get(uuid);
    }
}
