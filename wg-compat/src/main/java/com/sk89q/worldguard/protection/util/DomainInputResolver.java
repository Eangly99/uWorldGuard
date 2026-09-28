// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.util;

import com.google.common.base.Function;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.util.profile.Profile;
import com.sk89q.worldguard.util.profile.resolver.ProfileService;
import com.sk89q.worldguard.util.profile.util.UUIDs;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Turns command-style member input into a {@link DefaultDomain}: {@code g:name} is a group, a UUID
 * (dashed or not) is a player, and anything else is a player name handled per
 * {@link UserLocatorPolicy}.
 *
 * <p>Name lookups go through the given {@link ProfileService}. The services uWorldGuard ships read
 * the server's player cache only, so {@link #call()} does not block on the network, but a name the
 * server has never seen does not resolve.
 */
public class DomainInputResolver implements Callable<DefaultDomain> {

    private static final Pattern GROUP_PREFIX = Pattern.compile("^[gG]:");
    private static final Pattern UNDASHED_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern DASHED_UUID =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * How a bare player name is stored.
     */
    public enum UserLocatorPolicy {

        /**
         * Resolve to a UUID; an unresolvable name fails the whole call.
         */
        UUID_ONLY,

        /**
         * Store the name as given, without a lookup.
         */
        NAME_ONLY,

        /**
         * Resolve to a UUID, falling back to the name when the lookup misses.
         */
        UUID_AND_NAME
    }

    private final ProfileService profileService;
    private final String[] input;
    private UserLocatorPolicy locatorPolicy = UserLocatorPolicy.UUID_ONLY;

    public DomainInputResolver(final ProfileService profileService, final String[] input) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.input = input.clone();
    }

    public UserLocatorPolicy getLocatorPolicy() {
        return locatorPolicy;
    }

    public void setLocatorPolicy(final UserLocatorPolicy locatorPolicy) {
        this.locatorPolicy = Objects.requireNonNull(locatorPolicy, "locatorPolicy");
    }

    /**
     * @throws UnresolvedNamesException when the policy is {@link UserLocatorPolicy#UUID_ONLY} and a name
     *                                  did not resolve, or the profile service failed
     */
    @Override
    public DefaultDomain call() throws UnresolvedNamesException {
        final DefaultDomain domain = new DefaultDomain();
        final List<String> names = new ArrayList<>();
        for (final String token : input) {
            if (GROUP_PREFIX.matcher(token).find()) {
                domain.addGroup(token.substring(2));
            } else if (isUuid(token)) {
                domain.addPlayer(parseUUID(token));
            } else if (locatorPolicy == UserLocatorPolicy.NAME_ONLY) {
                domain.addPlayer(token);
            } else {
                names.add(token);
            }
        }
        if (names.isEmpty()) {
            return domain;
        }

        final List<Profile> found;
        try {
            found = profileService.findAllByName(names);
        } catch (final IOException e) {
            throw new UnresolvedNamesException("Could not look up player names", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnresolvedNamesException("Interrupted while looking up player names", e);
        }

        final List<String> unresolved = new ArrayList<>();
        for (final String name : names) {
            final Profile profile = match(found, name);
            if (profile != null) {
                domain.addPlayer(profile.getUniqueId());
            } else if (locatorPolicy == UserLocatorPolicy.UUID_AND_NAME) {
                domain.addPlayer(name);
            } else {
                unresolved.add(name);
            }
        }
        if (!unresolved.isEmpty()) {
            throw new UnresolvedNamesException("Unable to resolve the names " + String.join(", ", unresolved));
        }
        return domain;
    }

    /**
     * @return a function that adds the resolved domain to {@code target} and returns {@code target}
     */
    public Function<DefaultDomain, DefaultDomain> createAddAllFunction(final DefaultDomain target) {
        return resolved -> {
            target.addAll(resolved);
            return target;
        };
    }

    /**
     * @return a function that removes the resolved domain from {@code target} and returns {@code target}
     */
    public Function<DefaultDomain, DefaultDomain> createRemoveAllFunction(final DefaultDomain target) {
        return resolved -> {
            target.removeAll(resolved);
            return target;
        };
    }

    /**
     * @throws IllegalArgumentException when {@code input} is not a UUID in either form
     */
    public static UUID parseUUID(final String input) {
        final String dashed = UNDASHED_UUID.matcher(input).matches() ? UUIDs.addDashes(input) : input;
        return UUID.fromString(dashed);
    }

    private static boolean isUuid(final String token) {
        return UNDASHED_UUID.matcher(token).matches() || DASHED_UUID.matcher(token).matches();
    }

    private static Profile match(final List<Profile> profiles, final String name) {
        final String wanted = name.toLowerCase(Locale.ROOT);
        for (int i = 0, n = profiles.size(); i < n; i++) {
            final Profile profile = profiles.get(i);
            if (profile.getName().toLowerCase(Locale.ROOT).equals(wanted)) {
                return profile;
            }
        }
        return null;
    }
}
