// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.cache;

import com.google.common.collect.ImmutableMap;
import com.sk89q.worldguard.util.profile.Profile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory {@link ProfileCache}. Unbounded, so it grows by one entry per distinct player looked
 * up, which the server's player count bounds.
 */
public class HashMapCache extends AbstractProfileCache {

    private final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();

    public HashMapCache() {
    }

    @Override
    public void put(final Profile profile) {
        names.put(profile.getUniqueId(), profile.getName());
    }

    @Override
    public void putAll(final Iterable<Profile> profiles) {
        for (final Profile profile : profiles) {
            put(profile);
        }
    }

    @Override
    public Profile getIfPresent(final UUID uuid) {
        final String name = names.get(uuid);
        return name == null ? null : new Profile(uuid, name);
    }

    @Override
    public ImmutableMap<UUID, Profile> getAllPresent(final Iterable<UUID> uuids) {
        final ImmutableMap.Builder<UUID, Profile> found = ImmutableMap.builder();
        for (final UUID uuid : uuids) {
            final String name = names.get(uuid);
            if (name != null) {
                found.put(uuid, new Profile(uuid, name));
            }
        }
        return found.buildKeepingLast();
    }
}
