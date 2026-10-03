package com.tricrotism.uworldguard.universe;

import com.tricrotism.uworldguard.event.RegionExternalChangeEvent;
import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.universeprojects.api.region.*;
import com.universeprojects.api.region.event.RegionLifecycleListener;
import com.universeprojects.api.region.flag.FlagKey;
import com.universeprojects.api.region.flag.FlagSubject;
import com.universeprojects.api.region.shape.Cuboid;
import com.universeprojects.api.region.storage.RegionDefinition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps regions in UniverseSpigot's region service: its files store them, its index answers point
 * lookups, and regions edited through uSpigot directly are applied back to our managers.
 *
 * <p>Our managers stay the newest copy of anything edited since the last save. Every definition we
 * publish is remembered until uSpigot echoes it back, so an echo is never mistaken for someone else's
 * edit, and a uSpigot reload (which re-reads its files) keeps our unsaved edits instead of reverting
 * them.
 *
 * <p>The store the plugin is configured with (YAML or SQL) keeps a full copy: every save writes it
 * too, so leaving UniverseSpigot loses nothing, and regions uSpigot cannot hold (non-ASCII ids) are
 * read back from it on each load. Each world migrates from it once, on its first load here. Regions
 * uSpigot already holds under the same id win, since they are the newer copy. The world is then
 * recorded in {@code universe-migrated.txt}, so a region deleted through uSpigot is not refilled from
 * our copy before that copy's next save catches up.
 */
@NullMarked
public final class UniverseRegionStore implements RegionStore, RegionLifecycleListener {

    private static final int MAX_IN_FLIGHT = 8;

    private final Plugin plugin;
    private final RegionService service;
    private final RegionStore legacy;
    private final UniverseRegions codec;
    private final Logger log;
    private final Path migratedFile;
    private final Set<String> migrated;
    private final Map<String, RegionManager> managers = new ConcurrentHashMap<>();
    private final ThreadLocal<Collector> collectors = ThreadLocal.withInitial(Collector::new);
    private final Map<String, Deque<RegionDefinition>> inFlight = new ConcurrentHashMap<>();
    private final Set<String> unsaved = ConcurrentHashMap.newKeySet();
    /**
     * What uSpigot holds for each region, in the form our copy would write. Recorded from our
     * conversion rather than from uSpigot's definition: anything the conversion could not carry
     * would otherwise read as an edit, and the next save would write the stripped region over
     * uSpigot's copy.
     */
    private final Map<String, RegionDefinition> lastSaved = new ConcurrentHashMap<>();
    /**
     * Set while {@link #apply} adds a region uSpigot already holds, so the index does not queue it
     * straight back to uSpigot as an edit of ours.
     */
    private final ThreadLocal<Boolean> applyingExternal = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final Map<String, Set<String>> pendingDeletes = new ConcurrentHashMap<>();
    private final Set<String> unsavableIds = ConcurrentHashMap.newKeySet();
    private final Set<String> brokenMirrors = ConcurrentHashMap.newKeySet();
    /**
     * uSpigot keys by {@link Flag#getIndex()}, or {@link #NO_KEY} for a flag it cannot resolve.
     */
    private volatile AtomicReferenceArray<@Nullable Object> flagKeys = new AtomicReferenceArray<>(128);
    private static final Object NO_KEY = new Object();
    private final Map<String, WorldIndex> indexes = new ConcurrentHashMap<>();

    private record QuerySnapshot(long generation, com.universeprojects.api.region.RegionQuery query) {}

    private UniverseRegionStore(
        final Plugin plugin, final RegionService service, final RegionStore legacy,
        final Path migratedFile, final Set<String> migrated
    ) {
        this.plugin = plugin;
        this.service = service;
        this.legacy = legacy;
        this.log = plugin.getLogger();
        this.codec = new UniverseRegions(service.flags(), log);
        this.migratedFile = migratedFile;
        this.migrated = migrated;
    }

    /**
     * Entry point for the plugin, which reaches this class by reflection so that no uSpigot type is
     * ever loaded on another server. {@code service} is the server's {@link RegionService}.
     */
    public static RegionStore create(final Plugin plugin, final Object service, final RegionStore legacy) throws IOException {
        final Path migratedFile = plugin.getDataFolder().toPath().resolve("universe-migrated.txt");
        final Set<String> migrated = ConcurrentHashMap.newKeySet();
        if (Files.exists(migratedFile)) {
            for (final String line : Files.readAllLines(migratedFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    migrated.add(line.strip());
                }
            }
        }
        final UniverseRegionStore store = new UniverseRegionStore(plugin, (RegionService) service, legacy, migratedFile, migrated);
        store.service.events().addListener(store);
        return store;
    }

    @Override
    public void load(final String worldName, final RegionManager manager) throws Exception {
        brokenMirrors.remove(worldName);
        for (final Region region : stored(worldName)) {
            final ProtectedRegion converted = codec.toProtected(region, manager);
            if (converted != null) {
                manager.addRegion(converted);
                lastSaved.put(key(worldName, region.id()), codec.definition(worldName, converted));
            }
        }
        if (!migrated.contains(worldName)) {
            migrate(worldName, manager);
            return;
        }
        final RegionManager mirror = new RegionManager();
        try {
            legacy.load(worldName, mirror);
        } catch (final Exception e) {
            brokenMirrors.add(worldName);
            log.log(Level.WARNING, "Could not read uWorldGuard's own copy of world '" + worldName + "'."
                + " Regions still load from UniverseSpigot, but that copy is not written until the next"
                + " restart reads it cleanly.", e);
            return;
        }
        final List<ProtectedRegion> carried = new ArrayList<>();
        for (final ProtectedRegion region : mirror.getRegions()) {
            if (!storable(region) && !manager.hasRegion(region.getId())) {
                carried.add(region);
            }
        }
        carry(manager, carried);
    }

    /**
     * Everything uSpigot holds for {@code world}, global region included, parents before children.
     */
    private List<Region> stored(final String world) {
        final List<Region> stored = new ArrayList<>(service.regions(world));
        final Region global = service.in(world).global();
        if (global != null) {
            stored.add(global);
        }
        stored.sort(Comparator.comparingInt(Region::depth));
        return stored;
    }

    /**
     * Throws, leaving the world unrecorded, when our own store cannot be read. The container then
     * disables saving for the world, so neither copy is overwritten, and the next boot tries again.
     */
    private void migrate(final String world, final RegionManager manager) throws Exception {
        final RegionManager old = new RegionManager();
        legacy.load(world, old);
        final List<ProtectedRegion> carried = new ArrayList<>();
        for (final ProtectedRegion region : old.getRegions()) {
            final ProtectedRegion existing = manager.getRegion(region.getId());
            if (existing == null || existing instanceof GlobalProtectedRegion && isBlank(existing)) {
                carried.add(region);
            }
        }
        carry(manager, carried);
        if (!carried.isEmpty()) {
            write(world, manager);
            log.info("Migrated " + carried.size() + " region(s) in world '" + world + "' to UniverseSpigot. uWorldGuard's own storage keeps a copy of every save from now on.");
        }
        markMigrated(world);
    }

    /**
     * Adds regions read from our own store, then points their parents at the manager's instances,
     * since the ones they were read with belong to a throwaway manager.
     */
    private static void carry(final RegionManager manager, final List<ProtectedRegion> carried) {
        for (final ProtectedRegion region : carried) {
            manager.addRegion(region);
        }
        for (final ProtectedRegion region : carried) {
            final ProtectedRegion parent = region.getParent();
            if (parent != null) {
                region.setParent(manager.getRegion(parent.getId()));
            }
        }
    }

    private static boolean isBlank(final ProtectedRegion region) {
        return region.getFlags().isEmpty() && region.getUnresolvedFlags().isEmpty()
            && region.getOwners().isEmpty() && region.getMembers().isEmpty();
    }

    private void markMigrated(final String world) throws IOException {
        synchronized (migrated) {
            if (!migrated.add(world)) {
                return;
            }
            Files.createDirectories(migratedFile.getParent());
            Files.writeString(migratedFile, world + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    @Override
    public void save(final String worldName, final RegionManager manager) throws Exception {
        write(worldName, manager);
    }

    /**
     * Writes UniverseSpigot and then our own store, each attempted even if the other fails, so one
     * broken backend never leaves the other stale. The first failure is rethrown so the container
     * keeps the world dirty and tries again.
     */
    private void write(final String world, final RegionManager manager) throws Exception {
        Exception failure = null;
        try {
            writeUniverse(world, manager);
        } catch (final RuntimeException e) {
            failure = e;
        }
        if (!brokenMirrors.contains(world)) {
            try {
                legacy.save(world, manager);
            } catch (final Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Sends uSpigot only the regions that differ from what it last stored. Each one it receives comes
     * back as a callback on its main thread, so a full resend would cost that thread work for every
     * region in the world on every autosave, where this costs it only the edits.
     *
     * <p>Ids edited from here on are marked unsaved again, so a region changed while {@code saveAll}
     * runs is republished afterwards. Without that, the registry would keep the older copy this save
     * just published.
     */
    private void writeUniverse(final String world, final RegionManager manager) {
        final WorldIndex index = indexes.get(world);
        if (index != null) {
            index.flush();
        }
        final String prefix = world + ":";
        final List<String> saving = new ArrayList<>();
        unsaved.removeIf(key -> key.startsWith(prefix) && saving.add(key));
        final List<RegionDefinition> definitions = new ArrayList<>();
        for (final ProtectedRegion region : manager.getRegions()) {
            if (!storable(region)) {
                if (unsavableIds.add(key(world, region.getId()))) {
                    log.warning("Region '" + region.getId() + "' in world '" + world + "' cannot be stored by"
                        + " UniverseSpigot, so it is kept in uWorldGuard's own storage only. Either its id has a"
                        + " character outside A-Z, a-z, 0-9, '_' and '-', or a part or hole is an oval sphere or"
                        + " cylinder, which uSpigot can only hold with equal radii.");
                }
                continue;
            }
            final RegionDefinition definition = codec.definition(world, region);
            final RegionDefinition previous = lastSaved.get(key(world, region.getId()));
            if (previous != null && UniverseRegions.same(previous, definition)) {
                continue;
            }
            expectEcho(world, definition);
            definitions.add(definition);
        }
        if (!definitions.isEmpty()) {
            try {
                service.saveAll(definitions);
            } catch (final RuntimeException e) {
                unsaved.addAll(saving);
                throw e;
            } finally {
                changed(world);
            }
            for (final RegionDefinition definition : definitions) {
                lastSaved.put(key(world, definition.id()), definition);
            }
        }
        for (final String key : unsaved) {
            if (key.startsWith(prefix)) {
                final ProtectedRegion region = manager.getRegion(key.substring(prefix.length()));
                if (region != null && storable(region)) {
                    republish(world, region);
                }
            }
        }

        final Set<String> deletes = pendingDeletes.remove(world);
        if (deletes == null) {
            return;
        }
        final List<String> gone = new ArrayList<>(deletes.size());
        for (final String id : deletes) {
            final ProtectedRegion held = manager.getRegion(id);
            if (held == null || !storable(held)) {
                gone.add(id);
            }
        }
        if (gone.isEmpty()) {
            return;
        }
        try {
            service.deleteAll(world, gone, RemovalStrategy.UNSET_PARENT);
            for (final String id : gone) {
                lastSaved.remove(key(world, id));
            }
        } catch (final RuntimeException e) {
            pendingDeletes.computeIfAbsent(world, _ -> ConcurrentHashMap.newKeySet()).addAll(deletes);
            throw e;
        } finally {
            changed(world);
        }
    }

    private void publish(final String world, final ProtectedRegion region) {
        final RegionDefinition definition = codec.definition(world, region);
        expectEcho(world, definition);
        service.put(definition.toRegion());
        changed(world);
    }

    /**
     * Queues {@code region} on its world's index, so it goes out with the tick's other edits, or
     * publishes it directly when the world has no index.
     */
    private void republish(final String world, final ProtectedRegion region) {
        final WorldIndex index = indexes.get(world);
        if (index != null) {
            index.queue(region.getId(), region);
        } else {
            publish(world, region);
        }
    }

    private void expectEcho(final String world, final RegionDefinition definition) {
        inFlight.compute(key(world, definition.id()), (_, queue) -> {
            final Deque<RegionDefinition> q = queue != null ? queue : new ArrayDeque<>(2);
            if (q.size() == MAX_IN_FLIGHT) {
                q.pollFirst();
            }
            q.addLast(definition);
            return q;
        });
    }

    /**
     * Whether {@code incoming} is one of our own publishes coming back. Consumes it and anything we
     * published before it, which uSpigot has necessarily superseded.
     */
    private boolean consumeEcho(final String world, final RegionDefinition incoming) {
        final boolean[] echo = new boolean[1];
        inFlight.computeIfPresent(key(world, incoming.id()), (_, queue) -> {
            int matched = -1;
            int i = 0;
            for (final RegionDefinition sent : queue) {
                if (UniverseRegions.same(sent, incoming)) {
                    matched = i;
                }
                i++;
            }
            for (int n = 0; n <= matched; n++) {
                queue.pollFirst();
            }
            echo[0] = matched >= 0;
            return queue.isEmpty() ? null : queue;
        });
        return echo[0];
    }

    private static String key(final String world, final String id) {
        return world + ":" + id.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether uSpigot can hold {@code region}: its geometry ({@link UniverseRegions#representable})
     * and its id. uSpigot takes ids made of {@code A-Za-z0-9_,'+-} and ours already allow only letters,
     * digits, '_' and '-', so the only ids it refuses are ones with a non-ASCII letter. Checked here
     * rather than through {@code Region.validId} so the answer does not depend on the server build.
     */
    private static boolean storable(final ProtectedRegion region) {
        final String id = region.getId();
        for (int i = 0, n = id.length(); i < n; i++) {
            if (id.charAt(i) > 0x7F) {
                return false;
            }
        }
        return UniverseRegions.representable(region);
    }

    @Override
    public SpatialIndex index(final String worldName, final RegionManager manager) {
        managers.put(worldName, manager);
        final WorldIndex index = new WorldIndex(worldName, manager);
        indexes.put(worldName, index);
        return index;
    }

    /**
     * Drops {@code world}'s cached uSpigot query after anything that may have changed its regions.
     */
    private void changed(final String world) {
        final WorldIndex index = indexes.get(world);
        if (index != null) {
            index.generation.incrementAndGet();
        }
    }

    @Override
    public void unload(final String worldName) {
        managers.remove(worldName);
        final WorldIndex index = indexes.remove(worldName);
        if (index != null) {
            index.flush();
        }
        final String prefix = worldName + ":";
        inFlight.keySet().removeIf(key -> key.startsWith(prefix));
        lastSaved.keySet().removeIf(key -> key.startsWith(prefix));
        unsavableIds.removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void close() {
        for (final WorldIndex index : indexes.values()) {
            index.flush();
        }
        service.events().removeListener(this);
        managers.clear();
        indexes.clear();
        inFlight.clear();
        lastSaved.clear();
        legacy.close();
    }

    @Override
    public void onRegionAdded(final Region region) {
        changed(region.world());
        apply(region);
    }

    @Override
    public void onRegionUpdated(final Region region) {
        changed(region.world());
        apply(region);
    }

    /**
     * Our own removals arrive here after the region has already left the manager, so only a removal
     * made through uSpigot reaches {@code removeRegion}.
     */
    @Override
    public void onRegionRemoved(final String id, final String world) {
        changed(world);
        final RegionManager manager = managers.get(world);
        if (manager == null) {
            return;
        }
        final ProtectedRegion held = manager.getRegion(id);
        if (held == null || !storable(held)) {
            // uSpigot cannot hold ours, so this is our retraction of an older copy
            return;
        }
        final ProtectedRegion removed = manager.removeRegion(id);
        if (removed != null) {
            report(world, id, RegionExternalChangeEvent.Kind.REMOVED, removed, null, List.of());
        }
    }

    /**
     * uSpigot re-read its files, so its copy of anything we changed since the last save is stale:
     * those regions are pushed back and pending removals repeated. Everything else takes what the
     * files now say, which is how an edit made to them by hand comes in.
     */
    @Override
    public void onRegionsReloaded() {
        for (final Map.Entry<String, RegionManager> entry : managers.entrySet()) {
            final String world = entry.getKey();
            changed(world);
            final RegionManager manager = entry.getValue();
            final Set<String> deletes = pendingDeletes.getOrDefault(world, Set.of());
            final Set<String> held = new HashSet<>();
            for (final Region region : stored(world)) {
                final String id = region.id().toLowerCase(Locale.ROOT);
                held.add(id);
                if (deletes.contains(id)) {
                    service.remove(world, region.id());
                } else if (!unsaved.contains(key(world, id))) {
                    apply(region);
                }
            }
            for (final ProtectedRegion ours : manager.getRegions()) {
                final String id = ours.getId().toLowerCase(Locale.ROOT);
                if ((unsaved.contains(key(world, id)) || !held.contains(id)) && storable(ours)) {
                    republish(world, ours);
                }
            }
        }
    }

    private void apply(final Region region) {
        final String world = region.world();
        final RegionManager manager = managers.get(world);
        if (manager == null) {
            return;
        }
        final RegionDefinition incoming = codec.definitionOf(region);
        if (consumeEcho(world, incoming)) {
            return;
        }
        final ProtectedRegion existing = manager.getRegion(region.id());
        final RegionDefinition ours = existing == null ? null : codec.definition(world, existing);
        if (ours != null && UniverseRegions.same(ours, incoming)) {
            return;
        }
        final ProtectedRegion converted = codec.toProtected(region, manager);
        if (converted == null) {
            return;
        }
        applyingExternal.set(Boolean.TRUE);
        try {
            manager.addRegion(converted);
        } finally {
            applyingExternal.set(Boolean.FALSE);
        }
        lastSaved.put(key(world, region.id()), codec.definition(world, converted));
        if (ours == null) {
            report(world, region.id(), RegionExternalChangeEvent.Kind.CREATED, null, converted, List.of());
        } else {
            report(world, region.id(), RegionExternalChangeEvent.Kind.CHANGED, existing, converted,
                UniverseRegions.differences(ours, incoming));
        }
    }

    /**
     * Only reached for changes that are not our own echoes, so each one is logged and handed to
     * listeners. The event needs the Bukkit world, and a world that is not loaded has nobody to tell.
     */
    private void report(
        final String world, final String id, final RegionExternalChangeEvent.Kind kind,
        final @Nullable ProtectedRegion before, final @Nullable ProtectedRegion after, final List<String> changes
    ) {
        log.info("Region '" + id + "' in world '" + world + "' was " + kind.name().toLowerCase(Locale.ROOT)
            + " outside uWorldGuard" + (changes.isEmpty() ? "." : ": " + String.join(", ", changes) + "."));
        final World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld != null) {
            Bukkit.getPluginManager().callEvent(new RegionExternalChangeEvent(bukkitWorld, id, kind, before, after, changes));
        }
    }

    /**
     * uSpigot's key for {@code flag}, or {@code null} when it cannot answer for that flag the way we
     * do. A state flag needs a boolean key with no default, since a default would hide whether any
     * region set the flag, which build checks depend on.
     */
    private @Nullable FlagKey<?> flagKey(final Flag<?> flag) {
        final int index = flag.getIndex();
        if (index < 0) {
            return null;
        }
        final AtomicReferenceArray<@Nullable Object> table = flagKeys;
        final Object cached = index < table.length() ? table.get(index) : null;
        if (cached != null) {
            return cached == NO_KEY ? null : (FlagKey<?>) cached;
        }
        final FlagKey<?> named = FlagKey.named(flag.getName());
        final Object found;
        if (flag instanceof StateFlag) {
            final FlagKey<?> key = named != null ? named : FlagKey.booleanFlag(flag.getName());
            found = key.kind == FlagKey.Kind.BOOLEAN && !key.defaulted() ? key : NO_KEY;
        } else if (named == null) {
            // uSpigot has not seen a value for it yet, so ask again next time
            return null;
        } else {
            found = named;
        }
        cacheFlagKey(index, found);
        return found == NO_KEY ? null : (FlagKey<?>) found;
    }

    private synchronized void cacheFlagKey(final int index, final Object key) {
        AtomicReferenceArray<@Nullable Object> table = flagKeys;
        if (index >= table.length()) {
            final AtomicReferenceArray<@Nullable Object> grown = new AtomicReferenceArray<>(Math.max(index + 1, table.length() * 2));
            for (int i = 0; i < table.length(); i++) {
                grown.set(i, table.get(i));
            }
            flagKeys = grown;
            table = grown;
        }
        table.set(index, key);
    }

    /**
     * Applies our group qualifiers during uSpigot's resolution, which only sees them as opaque
     * {@code <flag>-group} entries.
     */
    private static FlagSubject subject(final RegionManager manager, final Flag<?> flag, final @Nullable UUID player) {
        return (region, key) -> {
            final ProtectedRegion ours = manager.getRegion(region.id());
            if (ours == null) {
                return true;
            }
            final RegionGroup group = ours.getFlagGroup(flag);
            return group == RegionGroup.ALL || group.contains(player == null ? null : ours.getAssociation(player));
        };
    }

    /**
     * Lookups for one world, answered from uSpigot's index. Point lookups ask for bounding-box hits
     * and the manager tests our own containment on each, so a shape uSpigot draws wider than ours
     * (an ellipsoid) never changes which regions apply.
     *
     * <p>Flags resolve through uSpigot wherever its copy of the block provably matches ours: every
     * region it reports is one we hold, and every non-cuboid one agrees with our shape on the block.
     * Anywhere else, and in a world holding regions uSpigot cannot store or with group qualifiers on
     * the global region, the manager resolves from our own copy.
     *
     * <p>Structural and flag edits reach the registry on the next tick instead of waiting for a save,
     * gathered into one {@code putAll} and one {@code removeAll}. uSpigot rebuilds its inheritance
     * index on every single {@code put}, so a bulk edit sent one region at a time cost O(n) per region.
     * Until a queued edit is sent, every lookup merges the queued regions in and resolves flags from
     * our own copy, so a region defined and not yet pushed is never unprotected. Regions uSpigot
     * cannot hold (non-ASCII ids) are kept in a side list checked on every lookup for the same reason.
     */
    private final class WorldIndex implements SpatialIndex {

        private static final ProtectedRegion[] NONE = new ProtectedRegion[0];

        /**
         * Bounds for how many edits one flush sends. uSpigot's {@code putAll} and {@code removeAll}
         * cost more per region as the batch grows, and much more when a removal unparents children:
         * on DevSpace at 5k regions, 2 batches of 2,500 took 205 ms with one 126 ms tick, while a
         * fixed 256 still let a parent-heavy {@code removeAll} take 66 ms.
         */
        private static final int MIN_BATCH = 16;
        private static final int MAX_BATCH = 256;

        /**
         * What one flush aims to spend in uSpigot. The batch size follows the last flush's cost,
         * halving toward this when a flush runs over and doubling back while flushes stay under.
         */
        private static final long TARGET_FLUSH_NANOS = 4_000_000L;

        /**
         * Edits the next flush sends. Only read and written inside {@link #sendBatch}, which is
         * synchronized.
         */
        private int batchSize = MAX_BATCH;

        /**
         * A queued registry edit: {@code region} to publish, or {@code null} to remove.
         *
         * <p>Equal only to itself. A flag edit re-queues the same region instance, and a record would
         * compare that new entry equal to the one a flush is sending, so the flush's conditional
         * remove dropped the edit made while it ran.
         */
        private static final class Pending {

            private final @Nullable ProtectedRegion region;

            Pending(final @Nullable ProtectedRegion region) {
                this.region = region;
            }

            @Nullable ProtectedRegion region() {
                return region;
            }
        }

        /**
         * What lookups read of the queue, rebuilt only when {@link #pendingVersion} has moved.
         */
        private record PendingView(long version, ProtectedRegion[] puts, boolean empty) {}

        private final String world;
        private final RegionManager manager;
        private volatile ProtectedRegion[] unindexable;
        /**
         * Lower-case ids of stored regions that are not cuboids. While empty, point lookups use uSpigot's
         * point query, whose containment provably matches ours.
         */
        private final Set<String> shaped = ConcurrentHashMap.newKeySet();
        /**
         * Keyed by lower-case id, so a region edited twice in a tick is sent once. An entry leaves only
         * after uSpigot has accepted it, and only if it was not queued again meanwhile.
         */
        private final Map<String, Pending> pending = new ConcurrentHashMap<>();
        private final AtomicLong pendingVersion = new AtomicLong();
        private volatile PendingView pendingView = new PendingView(0L, NONE, true);
        private final java.util.concurrent.atomic.AtomicBoolean flushQueued = new java.util.concurrent.atomic.AtomicBoolean();
        /**
         * uSpigot may not apply our group check to its world defaults, so a grouped global region
         * keeps resolution on our side.
         */
        private volatile boolean globalGrouped;
        /**
         * Bumped by every change to this world's uSpigot regions. A snapshot fetched before a bump
         * fails the generation check on its next read, even if a slow fetch stored it afterwards.
         */
        private final AtomicLong generation = new AtomicLong();
        private volatile @Nullable QuerySnapshot snapshot;

        /**
         * uSpigot's query for this world, fetched again only after a change.
         */
        private com.universeprojects.api.region.RegionQuery query() {
            final long current = generation.get();
            final QuerySnapshot cached = snapshot;
            if (cached != null && cached.generation() == current) {
                return cached.query();
            }
            final com.universeprojects.api.region.RegionQuery fresh = service.in(world);
            snapshot = new QuerySnapshot(current, fresh);
            return fresh;
        }

        WorldIndex(final String world, final RegionManager manager) {
            this.world = world;
            this.manager = manager;
            final List<ProtectedRegion> invalid = new ArrayList<>();
            for (final ProtectedRegion region : manager.getRegions()) {
                if (!storable(region)) {
                    invalid.add(region);
                } else {
                    trackShape(region);
                }
            }
            this.unindexable = invalid.toArray(NONE);
            final ProtectedRegion global = manager.getRegion(GlobalProtectedRegion.ID);
            this.globalGrouped = global != null && !global.getFlagGroups().isEmpty();
        }

        /**
         * Whether uSpigot's resolution can stand in for ours anywhere in this world. A region it
         * cannot store might be a parent some stored region inherits through.
         */
        private boolean delegates() {
            return unindexable.length == 0 && !globalGrouped && pendingView().empty();
        }

        /**
         * A redefine can move an id across the storable line either way, so each side retracts the
         * other. Going unstorable, uSpigot's copy is deleted, or the next load would bring the old
         * definition back over ours. Going storable, the old instance leaves the side list, where it
         * would keep matching lookups and keep delegation off for the world.
         */
        @Override
        public void put(final ProtectedRegion region) {
            final boolean external = applyingExternal.get();
            if (!external) {
                unsaved.add(key(world, region.getId()));
            }
            if (region instanceof GlobalProtectedRegion) {
                globalGrouped = !region.getFlagGroups().isEmpty();
            }
            final boolean wasUnindexable = unindexableHas(region.getId());
            if (storable(region)) {
                trackShape(region);
                if (wasUnindexable) {
                    setUnindexable(region, false);
                }
                if (!external) {
                    queue(region.getId(), region);
                }
            } else {
                shaped.remove(region.getId().toLowerCase(Locale.ROOT));
                setUnindexable(region, true);
                if (!wasUnindexable) {
                    retract(region.getId());
                }
            }
        }

        /**
         * Keeps {@link #shaped} in step with what uSpigot holds: the ids of stored regions that are not
         * cuboids, whose containment uSpigot may answer differently at a boundary block.
         */
        private void trackShape(final ProtectedRegion region) {
            final String id = region.getId().toLowerCase(Locale.ROOT);
            if (region instanceof ProtectedCuboidRegion || region instanceof GlobalProtectedRegion) {
                shaped.remove(id);
            } else {
                shaped.add(id);
            }
        }

        @Override
        public void remove(final ProtectedRegion region) {
            if (region instanceof GlobalProtectedRegion) {
                globalGrouped = false;
            }
            shaped.remove(region.getId().toLowerCase(Locale.ROOT));
            if (!storable(region)) {
                setUnindexable(region, false);
                return;
            }
            retract(region.getId());
        }

        /**
         * Takes {@code id} out of uSpigot: queued for the next flush, and kept as a pending delete
         * for the next save in case that flush fails.
         */
        private void retract(final String id) {
            queue(id, null);
            pendingDeletes.computeIfAbsent(world, _ -> ConcurrentHashMap.newKeySet())
                .add(id.toLowerCase(Locale.ROOT));
            inFlight.remove(key(world, id));
        }

        private boolean unindexableHas(final String id) {
            for (final ProtectedRegion r : unindexable) {
                if (r.getId().equalsIgnoreCase(id)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Queues a registry edit and makes sure a flush is scheduled for the next tick. A plugin that
         * is disabling can no longer schedule, so the edit is sent straight away instead.
         */
        void queue(final String id, final @Nullable ProtectedRegion region) {
            pending.put(id.toLowerCase(Locale.ROOT), new Pending(region));
            pendingVersion.incrementAndGet();
            scheduleFlush();
        }

        private void scheduleFlush() {
            if (!flushQueued.compareAndSet(false, true)) {
                return;
            }
            try {
                Bukkit.getGlobalRegionScheduler().run(plugin, _ -> flushTick());
            } catch (final org.bukkit.plugin.IllegalPluginAccessException disabling) {
                flush();
            }
        }

        /**
         * One batch per tick. A bulk edit larger than {@link #batchSize} spreads over the ticks
         * after it, and lookups keep merging what is still queued meanwhile.
         */
        private void flushTick() {
            flushQueued.set(false);
            sendBatch();
            if (!pending.isEmpty()) {
                scheduleFlush();
            }
        }

        /**
         * Sends everything queued, a batch at a time. For saves, unloads and shutdown, which need the
         * registry current before they go on.
         */
        void flush() {
            flushQueued.set(false);
            int sent;
            do {
                sent = sendBatch();
            } while (sent > 0);
        }

        private PendingView pendingView() {
            final long version = pendingVersion.get();
            final PendingView view = pendingView;
            if (view.version() == version) {
                return view;
            }
            final List<ProtectedRegion> puts = new ArrayList<>();
            for (final Pending queued : pending.values()) {
                if (queued.region() != null) {
                    puts.add(queued.region());
                }
            }
            final PendingView fresh = new PendingView(version, puts.toArray(NONE), pending.isEmpty());
            pendingView = fresh;
            return fresh;
        }

        /**
         * Sends up to {@link #batchSize} queued edits in one {@code removeAll} and one
         * {@code putAll}. Entries stay queued, and so stay merged into lookups, until uSpigot has
         * them. A failure is logged and dropped: the regions are still marked unsaved, so the next
         * save publishes them.
         *
         * @return how many entries were taken off the queue
         */
        private synchronized int sendBatch() {
            if (pending.isEmpty()) {
                return 0;
            }
            final int limit = batchSize;
            final List<Map.Entry<String, Pending>> sent = new ArrayList<>(Math.min(pending.size(), limit));
            for (final Map.Entry<String, Pending> entry : pending.entrySet()) {
                sent.add(Map.entry(entry.getKey(), entry.getValue()));
                if (sent.size() == limit) {
                    break;
                }
            }
            final List<Region> puts = new ArrayList<>(sent.size());
            final List<Region> removals = new ArrayList<>();
            for (final Map.Entry<String, Pending> entry : sent) {
                final ProtectedRegion region = entry.getValue().region();
                if (region == null) {
                    final Region live = service.region(world, entry.getKey());
                    if (live != null) {
                        removals.add(live);
                    }
                } else if (manager.getRegion(region.getId()) == region) {
                    final RegionDefinition definition = codec.definition(world, region);
                    expectEcho(world, definition);
                    puts.add(definition.toRegion());
                }
            }
            final long callStart = System.nanoTime();
            try {
                if (!removals.isEmpty()) {
                    service.removeAll(removals);
                }
                if (!puts.isEmpty()) {
                    service.putAll(puts);
                }
                resize(sent.size(), limit, System.nanoTime() - callStart);
            } catch (final RuntimeException e) {
                log.log(Level.WARNING, "Could not send " + sent.size() + " region edit(s) in world '" + world
                    + "' to UniverseSpigot. They are published again on the next save.", e);
            } finally {
                changed(world);
                for (final Map.Entry<String, Pending> entry : sent) {
                    pending.remove(entry.getKey(), entry.getValue());
                }
                pendingVersion.incrementAndGet();
            }
            return sent.size();
        }

        /**
         * Scales the next batch to the cost of the last: in proportion when it ran over
         * {@link #TARGET_FLUSH_NANOS}, doubled when a full batch came in under half of it.
         */
        private void resize(final int sent, final int limit, final long elapsed) {
            if (elapsed > TARGET_FLUSH_NANOS) {
                batchSize = (int) Math.max(MIN_BATCH, sent * TARGET_FLUSH_NANOS / elapsed);
            } else if (sent == limit && elapsed < TARGET_FLUSH_NANOS / 2) {
                batchSize = Math.min(MAX_BATCH, limit * 2);
            }
        }

        private synchronized void setUnindexable(final ProtectedRegion region, final boolean present) {
            final List<ProtectedRegion> next = new ArrayList<>(unindexable.length + 1);
            for (final ProtectedRegion r : unindexable) {
                if (!r.getId().equalsIgnoreCase(region.getId())) {
                    next.add(r);
                }
            }
            if (present) {
                next.add(region);
            }
            unindexable = next.toArray(NONE);
        }

        /**
         * The queue is read before uSpigot is asked. A flush invalidates the cached query before it
         * empties the queue, so a region is always found in one or the other.
         */
        @Override
        public List<ProtectedRegion> candidatesAt(final int x, final int y, final int z) {
            final PendingView queued = pendingView();
            final Collector collector = collectors.get();
            collector.index = this;
            collector.x = x;
            collector.y = y;
            collector.z = z;
            collector.agrees = unindexable.length == 0 && !globalGrouped && queued.empty();
            collector.out = null;
            // regionsAt is uSpigot's point query and measured 40-70% cheaper per event than an intersecting box
            // of one block. It filters by uSpigot's own containment, which only provably matches ours for cuboids,
            // so a world holding any other shape keeps the box query and the agreement check in visit.
            if (shaped.isEmpty()) {
                query().regionsAt(x, y, z, collector);
            } else {
                query().intersecting(x, y, z, x, y, z, collector);
            }
            final Hits out = collector.out;
            if (out != null) {
                out.agrees = collector.agrees;
            }
            collector.index = null;
            collector.out = null;
            final ProtectedRegion[] extra = unindexable;
            if (extra.length == 0 && queued.puts().length == 0) {
                return out == null ? List.of() : out;
            }
            List<ProtectedRegion> merged = out;
            merged = mergeAt(merged, extra, x, y, z);
            merged = mergeAt(merged, queued.puts(), x, y, z);
            return merged == null || merged.isEmpty() ? List.of() : merged;
        }

        /**
         * Adds each of {@code regions} covering the point, allocating the list only on the first hit so
         * a wilderness lookup stays allocation-free.
         */
        private static @Nullable List<ProtectedRegion> mergeAt(
            @Nullable List<ProtectedRegion> merged, final ProtectedRegion[] regions,
            final int x, final int y, final int z
        ) {
            for (final ProtectedRegion region : regions) {
                if (overlaps(region, x, y, z, x, y, z) && (merged == null || !containsSame(merged, region))) {
                    if (merged == null) {
                        merged = new ArrayList<>(4);
                    }
                    merged.add(region);
                }
            }
            return merged;
        }

        /**
         * A queued edit of a region uSpigot already holds is reported by both, so a merge checks for
         * the same instance before adding.
         */
        private static boolean containsSame(final List<ProtectedRegion> regions, final ProtectedRegion region) {
            for (int i = 0, n = regions.size(); i < n; i++) {
                if (regions.get(i) == region) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<ProtectedRegion> intersecting(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ
        ) {
            final PendingView queued = pendingView();
            final List<ProtectedRegion> out = new ArrayList<>();
            query().intersecting(minX, minY, minZ, maxX, maxY, maxZ, region -> {
                if (!Region.GLOBAL_ID.equals(region.id())) {
                    final ProtectedRegion ours = manager.getRegion(region.id());
                    if (ours != null) {
                        out.add(ours);
                    }
                }
            });
            for (final ProtectedRegion region : unindexable) {
                if (overlaps(region, minX, minY, minZ, maxX, maxY, maxZ) && !containsSame(out, region)) {
                    out.add(region);
                }
            }
            for (final ProtectedRegion region : queued.puts()) {
                if (overlaps(region, minX, minY, minZ, maxX, maxY, maxZ) && !out.contains(region)) {
                    out.add(region);
                }
            }
            return out;
        }

        private static boolean overlaps(
            final ProtectedRegion region,
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ
        ) {
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            return max.x() >= minX && min.x() <= maxX && max.y() >= minY && min.y() <= maxY
                && max.z() >= minZ && min.z() <= maxZ;
        }

        /**
         * Only proven for boxes where every region uSpigot reports is a cuboid we hold, since a box
         * test cannot check other shapes block by block the way {@link Collector} does.
         */
        @Override
        @SuppressWarnings("unchecked")
        public @Nullable State stateAcross(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ,
            final StateFlag flag, final boolean groups
        ) {
            if (!delegates()) {
                return null;
            }
            final FlagKey<?> key = flagKey(flag);
            if (key == null) {
                return null;
            }
            final com.universeprojects.api.region.RegionQuery query = query();
            final boolean[] cuboids = {true};
            query.intersecting(minX, minY, minZ, maxX, maxY, maxZ, region -> {
                if (!Region.GLOBAL_ID.equals(region.id())
                    && (!(region.shape() instanceof Cuboid) || !manager.hasRegion(region.id()))) {
                    cuboids[0] = false;
                }
            });
            if (!cuboids[0]) {
                return null;
            }
            final FlagSpan span = query.resolveAcross(minX, minY, minZ, maxX, maxY, maxZ, (FlagKey<Boolean>) key,
                groups ? subject(manager, flag, null) : null);
            return switch (span) {
                case ALLOW -> State.ALLOW;
                case DENY -> State.DENY;
                case UNSET -> flag.getDefault();
                case MIXED -> null;
            };
        }

        /**
         * Regions at one block that can resolve flags there through uSpigot, when {@link #agrees}.
         * It is the candidate list itself, so a lookup allocates nothing extra for it.
         */
        private final class Hits extends ArrayList<ProtectedRegion> implements FlagResolver {

            private final int x;
            private final int y;
            private final int z;
            private boolean agrees;

            Hits(final int x, final int y, final int z) {
                super(4);
                this.x = x;
                this.y = y;
                this.z = z;
            }

            @Override
            public boolean resolves(final Flag<?> flag) {
                return agrees && flagKey(flag) != null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public @Nullable State resolveState(final StateFlag flag, final @Nullable UUID subject, final boolean groups) {
                final FlagState state = query().resolveState(x, y, z, (FlagKey<Boolean>) flagKey(flag),
                    groups ? subject(manager, flag, subject) : null);
                return state == null ? null : state == FlagState.ALLOW ? State.ALLOW : State.DENY;
            }

            @Override
            public @Nullable ProtectedRegion highestSetting(final Flag<?> flag) {
                final Region top = query().highestAt(x, y, z, flagKey(flag), null);
                return top == null ? null : manager.getRegion(top.id());
            }
        }
    }

    /**
     * Per-thread visitor so a lookup that hits nothing allocates nothing. The result list is created
     * on the first hit and handed to the manager, which takes ownership of it.
     *
     * <p>It also checks that uSpigot's view of the block is ours: a region only uSpigot holds, or a
     * non-cuboid whose uSpigot shape disagrees with ours on this block, keeps flag resolution on our
     * side. Cuboids are skipped because both shapes are the same bounds.
     */
    private static final class Collector implements RegionVisitor {

        private @Nullable WorldIndex index;
        private int x;
        private int y;
        private int z;
        private boolean agrees;
        private WorldIndex.@Nullable Hits out;

        @Override
        public void visit(final Region region) {
            final WorldIndex idx = index;
            if (idx == null || Region.GLOBAL_ID.equals(region.id())) {
                return;
            }
            final ProtectedRegion ours = idx.manager.getRegion(region.id());
            if (ours == null) {
                agrees = false;
                return;
            }
            if (agrees && !(region.shape() instanceof Cuboid) && region.contains(x, y, z) != ours.contains(x, y, z)) {
                agrees = false;
            }
            WorldIndex.Hits list = out;
            if (list == null) {
                list = idx.new Hits(x, y, z);
                out = list;
            }
            list.add(ours);
        }
    }
}
