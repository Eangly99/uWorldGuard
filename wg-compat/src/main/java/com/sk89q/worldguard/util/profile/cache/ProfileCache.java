// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.cache;

import com.google.common.collect.ImmutableMap;
import com.sk89q.worldguard.util.profile.Profile;

import java.util.UUID;

/**
 * Remembers the profiles a lookup has seen, so later UUID to name reads skip the lookup.
 */
public interface ProfileCache {

    void put(Profile profile);

    void putAll(Iterable<Profile> profiles);

    Profile getIfPresent(UUID uuid);

    ImmutableMap<UUID, Profile> getAllPresent(Iterable<UUID> ids);
}
