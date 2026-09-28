// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's UUID and last known name.
 */
public final class Profile {

    private final UUID uniqueId;
    private final String name;

    public Profile(final UUID uniqueId, final String name) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * @return a new profile with the given UUID and this profile's name
     */
    public Profile setUniqueId(final UUID uniqueId) {
        return new Profile(uniqueId, name);
    }

    public String getName() {
        return name;
    }

    /**
     * @return a new profile with this profile's UUID and the given name
     */
    public Profile setName(final String name) {
        return new Profile(uniqueId, name);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Profile profile && uniqueId.equals(profile.uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }

    @Override
    public String toString() {
        return "Profile{uniqueId=" + uniqueId + ", name=" + name + '}';
    }
}
