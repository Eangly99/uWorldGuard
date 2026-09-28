// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.internal.platform;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;

import java.nio.file.Path;

/**
 * The server-specific half of WorldGuard, reached through
 * {@link com.sk89q.worldguard.WorldGuard#getPlatform()}.
 *
 * <p>uWorldGuard's implementation is {@code com.tricrotism.uworldguard.wgcompat.UwgPlatform}.
 *
 * <p>Two members of WorldGuard's interface are not shipped, because they reference types this layer
 * does not provide: {@code getDebugHandler()} and {@code addPlatformReports(...)}. The
 * {@code broadcastNotification} overload taking WorldEdit's shaded {@code TextComponent} is omitted
 * for the same reason: that class ships in {@code worldedit-libs}, not in the WorldEdit API this
 * module compiles against.
 */
public interface WorldGuardPlatform {

    StringMatcher getMatcher();

    /**
     * The shared service {@link com.sk89q.worldguard.WorldGuard#getProfileService()} returns.
     * {@code profileCache} is not used: that service already fronts its own cache.
     */
    default com.sk89q.worldguard.util.profile.resolver.ProfileService createProfileService(
        final com.sk89q.worldguard.util.profile.cache.ProfileCache profileCache) {
        return com.sk89q.worldguard.WorldGuard.getInstance().getProfileService();
    }

    String getPlatformName();

    String getPlatformVersion();

    RegionContainer getRegionContainer();

    SessionManager getSessionManager();

    ConfigurationManager getGlobalStateManager();

    Path getConfigDir();

    com.sk89q.worldedit.world.gamemode.GameMode getDefaultGameMode();

    void broadcastNotification(String message);

    void notifyFlagContextCreate(FlagContext.FlagContextBuilder flagContextBuilder);

    void load();

    void unload();

    /**
     * The server's spawn-protection region, or {@code null} when there is none.
     */
    default ProtectedRegion getSpawnProtection(final com.sk89q.worldedit.world.World world) {
        return null;
    }

    /**
     * @deprecated a WorldGuard command feature, not part of region protection.
     */
    @Deprecated(forRemoval = true)
    void stackPlayerInventory(LocalPlayer localPlayer);
}
