// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.sk89q.worldguard.util.profile.Profile;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A service answering from profiles put into it by hand. Names match case-insensitively.
 */
public class HashMapService extends SingleRequestService {

    private final ConcurrentHashMap<String, Profile> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Profile> byUuid = new ConcurrentHashMap<>();

    public HashMapService() {
    }

    public HashMapService(final Map<String, UUID> map) {
        for (final Map.Entry<String, UUID> entry : map.entrySet()) {
            put(new Profile(entry.getValue(), entry.getKey()));
        }
    }

    public void put(final Profile profile) {
        byName.put(profile.getName().toLowerCase(Locale.ROOT), profile);
        byUuid.put(profile.getUniqueId(), profile);
    }

    public void putAll(final Collection<Profile> profiles) {
        for (final Profile profile : profiles) {
            put(profile);
        }
    }

    @Override
    public int getIdealRequestLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Profile findByName(final String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Profile findByUuid(final UUID uuid) {
        return byUuid.get(uuid);
    }
}
