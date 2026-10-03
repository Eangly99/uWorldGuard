package com.tricrotism.uworldguard.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * The player state uWorldGuard has overridden and still owes back, mirrored to disk so it survives a
 * shutdown that could not undo it — including one that never runs.
 *
 * <p>{@code MovementListener} restores game-mode, speed and flight itself whenever the flag stops
 * applying, and on disable for every player the disabling thread owns. Under regionized threading
 * that is only the region doing the disabling: no API reaches into another region synchronously, and
 * a task scheduled there is never run, because the plugin's tasks are cancelled with it. Without
 * this, a player left standing in a {@code game-mode} region on any other region's thread would have
 * creative written into their playerdata and keep it for good — the plugin is the only thing that
 * knows it did that, so it has to be the thing that remembers.
 *
 * <p>A crash reaches no disable at all, which is why this mirrors the overrides as they are taken
 * rather than only collecting them at the end. The mirror is in memory and the disk write is
 * write-behind: {@link #record} and {@link #forget} publish to memory and ask for a flush, and the
 * flush is one async write {@value #FLUSH_DELAY_SECONDS} second later. Nothing touches the disk on
 * the movement path, which is where those calls come from.
 *
 * <p>Debounced rather than polled, so the delay is the whole crash window instead of the average of
 * a fixed interval, and so a server where no region sets these flags never wakes for this at all —
 * a poll would wake forever to find nothing changed. Coalescing is the other half: a player walking
 * back and forth across a region border cannot turn crossings into writes, because the flush already
 * pending covers them all.
 *
 * <p>The file is read and deleted at enable, replayed as those players log in, and written out again
 * at disable with whatever is still outstanding. Absent file, empty file and unreadable file all mean
 * the same thing: nothing to undo.
 */
@NullMarked
public final class PendingRestores {

    /**
     * What one player is owed. Any field may be {@code null} — only the flags that actually overrode
     * something are recorded.
     */
    public record State(
        @Nullable GameMode gameMode, @Nullable Float walkSpeed, @Nullable Float flySpeed,
        @Nullable Boolean allowFlight
    ) {
        boolean isEmpty() {
            return gameMode == null && walkSpeed == null && flySpeed == null && allowFlight == null;
        }
    }

    /**
     * How long a change waits for others to join it before the write goes out, and so how much a
     * crash can lose. Long enough that a player crossing a region border repeatedly is one write,
     * short enough that the window is a second rather than a session.
     */
    private static final long FLUSH_DELAY_SECONDS = 1L;

    private static final String GAME_MODE = "game-mode";
    private static final String WALK_SPEED = "walk-speed";
    private static final String FLY_SPEED = "fly-speed";
    private static final String ALLOW_FLIGHT = "allow-flight";

    private final Plugin plugin;
    private final Path target;
    private final Path temp;
    private final Map<UUID, State> outstanding = new ConcurrentHashMap<>();
    private final Map<UUID, State> view = Collections.unmodifiableMap(outstanding);
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private volatile boolean stopped;
    private final Object saveLock = new Object();
    private volatile @Nullable ScheduledTask task;

    /**
     * Reads the file and deletes it, holding what it said in memory.
     *
     * <p>What was read is immediately marked dirty, so the first flush writes it straight back. That
     * looks redundant next to simply leaving the file alone, and is not: every later removal has to
     * reach the disk too, and a mirror that is only ever written when something changes has to start
     * from a file that matches it. Skipping this would mean a server that crashed twice before the
     * owed players rejoined lost the overrides on the second crash — which is the case this whole
     * class exists for.
     */
    public PendingRestores(final Plugin plugin) {
        this.plugin = plugin;
        final File file = new File(plugin.getDataFolder(), "pending-restores.yml");
        this.target = file.toPath();
        this.temp = new File(plugin.getDataFolder(), "pending-restores.yml.tmp").toPath();
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (final String key : cfg.getKeys(false)) {
            final ConfigurationSection section = cfg.getConfigurationSection(key);
            final UUID uuid = parseUuid(key);
            if (section == null || uuid == null) {
                continue;
            }
            final State state = new State(
                parseGameMode(section.getString(GAME_MODE)),
                section.isSet(WALK_SPEED) ? (float) section.getDouble(WALK_SPEED) : null,
                section.isSet(FLY_SPEED) ? (float) section.getDouble(FLY_SPEED) : null,
                section.isSet(ALLOW_FLIGHT) ? section.getBoolean(ALLOW_FLIGHT) : null);
            if (!state.isEmpty()) {
                outstanding.put(uuid, state);
            }
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName()
                + "; player state overrides may be restored twice.");
        }
        if (!outstanding.isEmpty()) {
            dirty.set(true);
            plugin.getLogger().info("Holding " + outstanding.size() + " player state override(s) that"
                + " the last shutdown could not undo; they will be restored as those players log in.");
        }
    }

    /**
     * Writes back whatever the last run left owed. From here on every change queues its own flush,
     * so there is nothing running in the background on a server that never sets these flags.
     */
    public void start() {
        scheduleFlush();
    }

    /**
     * Stops accepting new flushes and cancels any queued one, so the disable's final write cannot
     * race it — and so nothing tries to schedule onto a plugin that is already disabled, which
     * throws.
     */
    public void stop() {
        stopped = true;
        final ScheduledTask queued = task;
        if (queued != null) {
            queued.cancel();
            task = null;
        }
    }

    /**
     * Queues the write, unless one is already queued or the plugin is going away.
     *
     * <p>{@code stopped} is read before scheduling and set during disable, so a region thread still
     * handling a crossing can pass that check and reach a plugin that is by then disabled — which is
     * what the scheduler throws on. Nothing is lost when it does: the change is already in the
     * mirror, and the disable's own {@link #flushNow} writes it.
     */
    private void scheduleFlush() {
        if (stopped || !dirty.get() || !flushQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            task = plugin.getServer().getAsyncScheduler().runDelayed(plugin, _ -> {
                flushQueued.set(false);
                flush();
            }, FLUSH_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (final RuntimeException disabling) {
            flushQueued.set(false);
        }
    }

    /**
     * Records everything {@code uuid} is currently owed, replacing whatever was recorded before.
     * All-null forgets them, which is what a player who has left the region owes.
     *
     * <p>Called from the movement path, so it does no I/O: it publishes to the in-memory mirror and
     * sets the dirty bit the flush reads.
     */
    public void record(final UUID uuid, final @Nullable GameMode gameMode,
                       final @Nullable Float walkSpeed, final @Nullable Float flySpeed,
                       final @Nullable Boolean allowFlight) {
        final State state = new State(gameMode, walkSpeed, flySpeed, allowFlight);
        if (state.isEmpty()) {
            forget(uuid);
            return;
        }
        if (state.equals(outstanding.put(uuid, state))) {
            return;
        }
        dirty.set(true);
        scheduleFlush();
    }

    public void forget(final UUID uuid) {
        if (!outstanding.isEmpty() && outstanding.remove(uuid) != null) {
            dirty.set(true);
            scheduleFlush();
        }
    }

    /**
     * What {@code uuid} is owed, removing it — a restore happens once.
     */
    public @Nullable State take(final UUID uuid) {
        if (outstanding.isEmpty()) {
            return null;
        }
        final State owed = outstanding.remove(uuid);
        if (owed != null) {
            dirty.set(true);
            scheduleFlush();
        }
        return owed;
    }

    /**
     * Everything currently owed. Live and unmodifiable: iterating it while {@link #forget} removes
     * from it is safe, which is how the disable path walks it.
     */
    public Map<UUID, State> outstanding() {
        return view;
    }

    /**
     * Writes only if something changed since the last write. A failed write puts the dirty bit back
     * and queues another attempt, rather than dropping the overrides on the floor.
     */
    private void flush() {
        if (dirty.compareAndSet(true, false) && !writeFile()) {
            dirty.set(true);
            scheduleFlush();
        }
    }

    /**
     * The final write, on the calling thread — this runs from disable, where the async scheduler is
     * stopping and a scheduled flush would never execute.
     */
    public void flushNow() {
        dirty.set(false);
        writeFile();
    }

    /**
     * Stages through a temp file and moves it into place, so a crash during the write cannot leave a
     * torn document — which would parse as empty at next boot and lose every override in it, the
     * exact thing this class exists to prevent.
     *
     * @return whether the file now reflects {@link #outstanding}
     */
    private boolean writeFile() {
        synchronized (saveLock) {
            try {
                if (outstanding.isEmpty()) {
                    Files.deleteIfExists(target);
                    return true;
                }
                // Written by hand: the values are UUIDs, enum names, numbers and booleans, so the
                // document is fixed in shape, and a YAML dumper per write cost ~270 KB of garbage.
                final StringBuilder out = new StringBuilder(96 * outstanding.size());
                for (final Map.Entry<UUID, State> entry : outstanding.entrySet()) {
                    final State state = entry.getValue();
                    out.append('\'').append(entry.getKey()).append("':\n");
                    if (state.gameMode() != null) {
                        out.append("  ").append(GAME_MODE).append(": ").append(state.gameMode().name()).append('\n');
                    }
                    if (state.walkSpeed() != null) {
                        out.append("  ").append(WALK_SPEED).append(": ").append(state.walkSpeed().floatValue()).append('\n');
                    }
                    if (state.flySpeed() != null) {
                        out.append("  ").append(FLY_SPEED).append(": ").append(state.flySpeed().floatValue()).append('\n');
                    }
                    if (state.allowFlight() != null) {
                        out.append("  ").append(ALLOW_FLIGHT).append(": ").append(state.allowFlight().booleanValue()).append('\n');
                    }
                }
                Files.createDirectories(target.getParent());
                Files.writeString(temp, out);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (final IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save pending-restores.yml; player"
                    + " state overrides this shutdown could not undo will stay applied.", e);
                return false;
            }
        }
    }

    private static @Nullable UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable GameMode parseGameMode(final @Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
