// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.util.profile.Profile;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A service that resolves one profile per request; the batch methods loop the single ones.
 */
abstract class SingleRequestService implements ProfileService {

    @Override
    public final ImmutableList<Profile> findAllByName(final Iterable<String> names)
        throws IOException, InterruptedException {
        final ImmutableList.Builder<Profile> found = ImmutableList.builder();
        findAllByName(names, profile -> {
            found.add(profile);
            return true;
        });
        return found.build();
    }

    @Override
    public final void findAllByName(final Iterable<String> names, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        for (final String name : names) {
            final Profile profile = findByName(name);
            if (profile != null && !consumer.test(profile)) {
                return;
            }
        }
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
    public final void findAllByUuid(final Iterable<UUID> uuids, final Predicate<Profile> consumer)
        throws IOException, InterruptedException {
        for (final UUID uuid : uuids) {
            final Profile profile = findByUuid(uuid);
            if (profile != null && !consumer.test(profile)) {
                return;
            }
        }
    }
}
