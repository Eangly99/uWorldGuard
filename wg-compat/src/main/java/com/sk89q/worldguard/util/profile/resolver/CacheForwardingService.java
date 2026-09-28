// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.util.profile.Profile;
import com.sk89q.worldguard.util.profile.cache.ProfileCache;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Wraps a service so every profile it resolves is also written to a cache.
 */
public class CacheForwardingService implements ProfileService {

    private final ProfileService resolver;
    private final ProfileCache cache;

    public CacheForwardingService(final ProfileService resolver, final ProfileCache cache) {
        this.resolver = resolver;
        this.cache = cache;
    }

    @Override
    public int getIdealRequestLimit() {
        return resolver.getIdealRequestLimit();
    }

    @Override
    public Profile findByName(final String name) throws IOException, InterruptedException {
        return remember(resolver.findByName(name));
    }

    @Override
    public ImmutableList<Profile> findAllByName(final Iterable<String> names) throws IOException, InterruptedException {
        final ImmutableList<Profile> found = resolver.findAllByName(names);
        cache.putAll(found);
        return found;
    }

    @Override
    public void findAllByName(final Iterable<String> names, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        resolver.findAllByName(names, profile -> {
            cache.put(profile);
            return consumer.test(profile);
        });
    }

    @Override
    public Profile findByUuid(final UUID uuid) throws IOException, InterruptedException {
        return remember(resolver.findByUuid(uuid));
    }

    @Override
    public ImmutableList<Profile> findAllByUuid(final Iterable<UUID> uuids) throws IOException, InterruptedException {
        final ImmutableList<Profile> found = resolver.findAllByUuid(uuids);
        cache.putAll(found);
        return found;
    }

    @Override
    public void findAllByUuid(final Iterable<UUID> uuids, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        resolver.findAllByUuid(uuids, profile -> {
            cache.put(profile);
            return consumer.test(profile);
        });
    }

    private Profile remember(final Profile profile) {
        if (profile != null) {
            cache.put(profile);
        }
        return profile;
    }
}
