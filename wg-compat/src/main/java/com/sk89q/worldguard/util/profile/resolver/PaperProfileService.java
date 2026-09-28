// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.resolver;

import com.sk89q.worldguard.util.profile.Profile;

import java.util.UUID;

/**
 * Answers exactly as {@link PaperPlayerService}: from the server's user cache, never from Mojang.
 * WorldGuard's Bukkit module ships this name separately, so consumers that link to it find it here.
 */
public final class PaperProfileService extends SingleRequestService {

    private static final PaperProfileService INSTANCE = new PaperProfileService();

    private PaperProfileService() {
    }

    public static PaperProfileService getInstance() {
        return INSTANCE;
    }

    @Override
    public int getIdealRequestLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Profile findByName(final String name) {
        return PaperPlayerService.getInstance().findByName(name);
    }

    @Override
    public Profile findByUuid(final UUID uuid) {
        return PaperPlayerService.getInstance().findByUuid(uuid);
    }
}
