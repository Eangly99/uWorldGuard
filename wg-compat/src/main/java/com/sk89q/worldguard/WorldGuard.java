// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.util.profile.cache.HashMapCache;
import com.sk89q.worldguard.util.profile.cache.ProfileCache;
import com.sk89q.worldguard.util.profile.resolver.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * WorldGuard's singleton entry point: {@code WorldGuard.getInstance().getPlatform()}.
 *
 * <p>The platform is created on first use rather than installed by a bootstrap, so a consumer that
 * only ever calls {@code getInstance()} works whether or not uWorldGuard has finished enabling.
 *
 * <p>Nothing in this class resolves a WorldEdit type from a static or instance initialiser — it sits
 * on the same load path as {@link com.sk89q.worldguard.bukkit.WorldGuardPlugin}, which must stay
 * loadable on a server with no WorldEdit installed.
 *
 * <p>{@code getSupervisor()} and {@code getExceptionConverter()} are not shipped: each returns a type
 * this layer does not provide.
 */
public final class WorldGuard {

    public static final Logger logger = Logger.getLogger("uWorldGuard");

    private static final WorldGuard INSTANCE = new WorldGuard();

    private static final String API_VERSION = com.tricrotism.uworldguard.util.WorldGuardApiLevel.VERSION;

    private volatile WorldGuardPlatform platform;
    private volatile FlagRegistry flagRegistry;
    private volatile ProfileCache profileCache;
    private volatile ProfileService profileService;
    private volatile ListeningExecutorService executorService;

    private WorldGuard() {
    }

    public static WorldGuard getInstance() {
        return INSTANCE;
    }

    /**
     * The WorldGuard API level this shim emulates, not uWorldGuard's own version — consumers compare
     * it against WorldGuard releases. {@code getPlatform().getPlatformVersion()} gives uWorldGuard's.
     */
    public static String getVersion() {
        return API_VERSION;
    }

    public WorldGuardPlatform getPlatform() {
        WorldGuardPlatform current = platform;
        if (current == null) {
            synchronized (this) {
                current = platform;
                if (current == null) {
                    current = com.tricrotism.uworldguard.wgcompat.UwgPlatform.INSTANCE;
                    platform = current;
                }
            }
        }
        return current;
    }

    public void setPlatform(final WorldGuardPlatform platform) {
        this.platform = platform;
    }

    public FlagRegistry getFlagRegistry() {
        FlagRegistry current = flagRegistry;
        if (current == null) {
            synchronized (this) {
                current = flagRegistry;
                if (current == null) {
                    final SimpleFlagRegistry registry = new SimpleFlagRegistry();
                    registry.registerAll(Flags.uwgAll());
                    current = registry;
                    flagRegistry = current;
                }
            }
        }
        return current;
    }

    public ProfileCache getProfileCache() {
        ProfileCache current = profileCache;
        if (current == null) {
            synchronized (this) {
                current = profileCache;
                if (current == null) {
                    current = new HashMapCache();
                    profileCache = current;
                }
            }
        }
        return current;
    }

    /**
     * Resolves from players online and the server's user cache, remembering hits in
     * {@link #getProfileCache()}. Never goes to the network, so it is safe to call on a region thread,
     * and a name the server has never seen resolves to {@code null}.
     */
    public ProfileService getProfileService() {
        ProfileService current = profileService;
        if (current == null) {
            synchronized (this) {
                current = profileService;
                if (current == null) {
                    current = new CacheForwardingService(
                        new CombinedProfileService(BukkitPlayerService.getInstance(), PaperPlayerService.getInstance()),
                        getProfileCache());
                    profileService = current;
                }
            }
        }
        return current;
    }

    /**
     * A small daemon pool owned by the compat layer, created on first use and shut down by
     * {@link #disable()}. Tasks here must not touch worlds, entities or players.
     */
    public ListeningExecutorService getExecutorService() {
        ListeningExecutorService current = executorService;
        if (current == null) {
            synchronized (this) {
                current = executorService;
                if (current == null) {
                    final AtomicInteger counter = new AtomicInteger();
                    final ThreadFactory threads = runnable -> {
                        final Thread thread = new Thread(runnable, "uWorldGuard-compat-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    };
                    current = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(threads));
                    executorService = current;
                }
            }
        }
        return current;
    }

    /**
     * The {@code LocalPlayer} behind a WorldEdit actor.
     *
     * @throws com.sk89q.minecraft.util.commands.CommandException when the actor is not a player
     */
    public LocalPlayer checkPlayer(final com.sk89q.worldedit.extension.platform.Actor sender)
        throws com.sk89q.minecraft.util.commands.CommandException {
        return (LocalPlayer) ActorCheck.check(sender);
    }

    /**
     * Idempotent: creates the platform if nothing has asked for it yet. uWorldGuard drives its own
     * lifecycle, so there is nothing else to do here.
     */
    public void setup() {
        getPlatform();
    }

    public void disable() {
        final WorldGuardPlatform current = platform;
        if (current != null) {
            current.unload();
        }
        platform = null;
        final ListeningExecutorService executor = executorService;
        executorService = null;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (final InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Kept in its own class so the WorldEdit exception type is only resolved when a consumer calls
     * {@link #checkPlayer}. Verifying a throw of it inside {@code WorldGuard} would load it, and this
     * class must load on a server with no WorldEdit.
     */
    private static final class ActorCheck {

        private ActorCheck() {
        }

        static Object check(final Object actor) throws com.sk89q.minecraft.util.commands.CommandException {
            if (actor instanceof LocalPlayer) {
                return actor;
            }
            if (actor instanceof com.sk89q.worldedit.entity.Player player) {
                final org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(player.getUniqueId());
                if (online != null) {
                    return com.tricrotism.uworldguard.wgcompat.PlayerWrapping.wrap(online);
                }
            }
            throw new com.sk89q.minecraft.util.commands.CommandException("A player is expected.");
        }
    }
}
