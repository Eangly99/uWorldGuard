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
 * Resolves player names and UUIDs to profiles.
 *
 * <p>Every service uWorldGuard ships answers from the server's local player cache and never goes
 * to the network, so a lookup that misses returns {@code null} rather than waiting on Mojang.
 */
public interface ProfileService {

    int getIdealRequestLimit();

    Profile findByName(String name) throws IOException, InterruptedException;

    ImmutableList<Profile> findAllByName(Iterable<String> names) throws IOException, InterruptedException;

    /**
     * Feeds each resolved profile to {@code consumer}, stopping early when it returns {@code false}.
     */
    void findAllByName(Iterable<String> names, Predicate<Profile> consumer) throws IOException, InterruptedException;

    Profile findByUuid(UUID uuid) throws IOException, InterruptedException;

    ImmutableList<Profile> findAllByUuid(Iterable<UUID> uuids) throws IOException, InterruptedException;

    /**
     * @see #findAllByName(Iterable, Predicate)
     */
    void findAllByUuid(Iterable<UUID> uuids, Predicate<Profile> consumer) throws IOException, InterruptedException;
}
