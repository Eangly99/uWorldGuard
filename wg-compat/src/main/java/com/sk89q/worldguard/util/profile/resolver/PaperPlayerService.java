// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.sk89q.worldguard.util.profile.Profile;
import com.tricrotism.uworldguard.wgcompat.NameResolver;

import java.util.UUID;

/**
 * Resolves any player the server has seen, from its in-memory user cache. Never reads playerdata
 * from disk and never asks Mojang, so a miss is {@code null} and costs nothing.
 */
public class PaperPlayerService extends SingleRequestService {

    private static final PaperPlayerService INSTANCE = new PaperPlayerService();

    private PaperPlayerService() {
    }

    public static PaperPlayerService getInstance() {
        return INSTANCE;
    }

    @Override
    public int getIdealRequestLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Profile findByName(final String name) {
        final UUID uuid = NameResolver.uuid(name);
        if (uuid == null) {
            return null;
        }
        final String cached = NameResolver.name(uuid);
        return new Profile(uuid, cached == null ? name : cached);
    }

    @Override
    public Profile findByUuid(final UUID uuid) {
        final String name = NameResolver.name(uuid);
        return name == null ? null : new Profile(uuid, name);
    }
}
