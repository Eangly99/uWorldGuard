package com.tricrotism.uworldguard.universe;

import com.tricrotism.uworldguard.event.RegionExternalChangeEvent;
import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.GlobalProtectedRegion;
import com.tricrotism.uworldguard.region.ProtectedRegion;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.region.SpatialIndex;
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
    private final Map<String, RegionDefinition> lastSaved = new ConcurrentHashMap<>();
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
                lastSaved.put(key(worldName, region.id()), codec.definitionOf(region));
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
        for (final ProtectedRegion region : manager.getRegions()) {
            if (unsaved.contains(key(world, region.getId())) && storable(region)) {
                publish(world, region);
            }
        }

        final Set<String> deletes = pendingDeletes.remove(world);
        if (deletes == null) {
            return;
        }
        try {
            for (final String id : deletes) {
                if (!manager.hasRegion(id)) {
                    service.delete(world, id, RemovalStrategy.UNSET_PARENT);
                    lastSaved.remove(key(world, id));
                }
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
        indexes.remove(worldName);
        final String prefix = worldName + ":";
        inFlight.keySet().removeIf(key -> key.startsWith(prefix));
        lastSaved.keySet().removeIf(key -> key.startsWith(prefix));
        unsavableIds.removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void close() {
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
                    publish(world, ours);
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
        manager.addRegion(converted);
        lastSaved.put(key(world, region.id()), incoming);
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
     * <p>Structural and flag edits go straight into the registry instead of waiting for a save: the
     * index is the only thing queries read, so a region defined and not yet pushed would stand
     * unprotected. Regions uSpigot cannot hold (non-ASCII ids) are kept in a side list checked on
     * every lookup for the same reason.
     */
    private final class WorldIndex implements SpatialIndex {

        private static final ProtectedRegion[] NONE = new ProtectedRegion[0];

        private final String world;
        private final RegionManager manager;
        private volatile ProtectedRegion[] unindexable;
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
            return unindexable.length == 0 && !globalGrouped;
        }

        @Override
        public void put(final ProtectedRegion region) {
            unsaved.add(key(world, region.getId()));
            if (region instanceof GlobalProtectedRegion) {
                globalGrouped = !region.getFlagGroups().isEmpty();
            }
            if (storable(region)) {
                publish(world, region);
            } else {
                setUnindexable(region, true);
            }
        }

        @Override
        public void remove(final ProtectedRegion region) {
            if (region instanceof GlobalProtectedRegion) {
                globalGrouped = false;
            }
            if (!storable(region)) {
                setUnindexable(region, false);
                return;
            }
            service.remove(world, region.getId());
            changed(world);
            pendingDeletes.computeIfAbsent(world, _ -> ConcurrentHashMap.newKeySet())
                .add(region.getId().toLowerCase(Locale.ROOT));
            inFlight.remove(key(world, region.getId()));
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

        @Override
        public List<ProtectedRegion> candidatesAt(final int x, final int y, final int z) {
            final Collector collector = collectors.get();
            collector.index = this;
            collector.x = x;
            collector.y = y;
            collector.z = z;
            collector.agrees = delegates();
            collector.out = null;
            query().intersecting(x, y, z, x, y, z, collector);
            final Hits out = collector.out;
            if (out != null) {
                out.agrees = collector.agrees;
            }
            collector.index = null;
            collector.out = null;
            final ProtectedRegion[] extra = unindexable;
            if (extra.length == 0) {
                return out == null ? List.of() : out;
            }
            final List<ProtectedRegion> merged = out != null ? out : new ArrayList<>(extra.length);
            Collections.addAll(merged, extra);
            return merged;
        }

        @Override
        public List<ProtectedRegion> intersecting(
            final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ
        ) {
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
                final BlockVector3 min = region.getMinimumPoint();
                final BlockVector3 max = region.getMaximumPoint();
                if (max.x() >= minX && min.x() <= maxX && max.y() >= minY && min.y() <= maxY
                    && max.z() >= minZ && min.z() <= maxZ) {
                    out.add(region);
                }
            }
            return out;
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
