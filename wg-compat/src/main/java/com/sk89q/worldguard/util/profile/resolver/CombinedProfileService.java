// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.util.profile.Profile;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Asks each service in order; a later one only sees the names or UUIDs the earlier ones missed.
 */
public class CombinedProfileService implements ProfileService {

    private final List<ProfileService> services;

    public CombinedProfileService(final List<ProfileService> services) {
        this.services = List.copyOf(services);
    }

    public CombinedProfileService(final ProfileService... services) {
        this(List.of(services));
    }

    @Override
    public int getIdealRequestLimit() {
        int limit = Integer.MAX_VALUE;
        for (final ProfileService service : services) {
            limit = Math.min(limit, service.getIdealRequestLimit());
        }
        return limit;
    }

    @Override
    public Profile findByName(final String name) throws IOException, InterruptedException {
        for (final ProfileService service : services) {
            final Profile profile = service.findByName(name);
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }

    @Override
    public ImmutableList<Profile> findAllByName(final Iterable<String> names) throws IOException, InterruptedException {
        final ImmutableList.Builder<Profile> found = ImmutableList.builder();
        findAllByName(names, profile -> {
            found.add(profile);
            return true;
        });
        return found.build();
    }

    @Override
    public void findAllByName(final Iterable<String> names, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        List<String> remaining = copy(names);
        for (final ProfileService service : services) {
            if (remaining.isEmpty()) {
                return;
            }
            final Set<String> seen = new HashSet<>();
            final boolean[] stopped = new boolean[1];
            service.findAllByName(remaining, profile -> {
                seen.add(profile.getName().toLowerCase(Locale.ROOT));
                if (!consumer.test(profile)) {
                    stopped[0] = true;
                    return false;
                }
                return true;
            });
            if (stopped[0]) {
                return;
            }
            final List<String> next = new ArrayList<>(remaining.size());
            for (final String name : remaining) {
                if (!seen.contains(name.toLowerCase(Locale.ROOT))) {
                    next.add(name);
                }
            }
            remaining = next;
        }
    }

    @Override
    public Profile findByUuid(final UUID uuid) throws IOException, InterruptedException {
        for (final ProfileService service : services) {
            final Profile profile = service.findByUuid(uuid);
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }

    @Override
    public ImmutableList<Profile> findAllByUuid(final Iterable<UUID> uuids) throws IOException, InterruptedException {
        final ImmutableList.Builder<Profile> found = ImmutableList.builder();
        findAllByUuid(uuids, profile -> {
            found.add(profile);
            return true;
        });
        return found.build();
    }

    @Override
    public void findAllByUuid(final Iterable<UUID> uuids, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        List<UUID> remaining = copy(uuids);
        for (final ProfileService service : services) {
            if (remaining.isEmpty()) {
                return;
            }
            final Set<UUID> seen = new HashSet<>();
            final boolean[] stopped = new boolean[1];
            service.findAllByUuid(remaining, profile -> {
                seen.add(profile.getUniqueId());
                if (!consumer.test(profile)) {
                    stopped[0] = true;
                    return false;
                }
                return true;
            });
            if (stopped[0]) {
                return;
            }
            final List<UUID> next = new ArrayList<>(remaining.size());
            for (final UUID uuid : remaining) {
                if (!seen.contains(uuid)) {
                    next.add(uuid);
                }
            }
            remaining = next;
        }
    }

    private static <T> List<T> copy(final Iterable<T> source) {
        final List<T> list = new ArrayList<>();
        for (final T item : source) {
            list.add(item);
        }
        return list;
    }
}
