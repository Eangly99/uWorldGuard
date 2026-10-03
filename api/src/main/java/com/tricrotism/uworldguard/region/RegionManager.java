package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Holds all regions for a single world. Thread-safe; queried from region threads and
 * edited from command threads.
 *
 * <p>Applicable-region lookup is backed by a per-chunk candidate cache. The first query in a
 * chunk scans every region for those whose bounding box overlaps it and caches that (usually
 * tiny, often empty) list; later queries in the chunk test only those candidates. Wilderness
 * chunks cache a shared empty list, so the common no-region case is a single array-slot read. The
 * cache is dropped wholesale when a region is added or removed — bounds are immutable and
 * flag/priority/parent edits read through to the live region, so nothing else changes what
 * overlaps a chunk — and is a fixed-capacity direct-mapped table (primitive {@code long} keys, no
 * boxing) to bound memory. A spatial index (R-tree) remains the endgame for worlds with very many
 * large, overlapping regions.
 */
@NullMarked
public final class RegionManager {

    private final Map<String, ProtectedRegion> regions = new ConcurrentHashMap<>();
    /**
     * Regions whose id is not all lower case, by that exact id. Storage backends hand ids back as
     * written, and without this a mixed-case id was lowercased into a new string on every lookup. An
     * entry counts only while its region is still owned by this manager, so a stale one costs the
     * lowercase fallback, never a wrong answer.
     */
    private final Map<String, ProtectedRegion> mixedCaseIds = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile @Nullable GlobalProtectedRegion global;
    private volatile @Nullable ApplicableRegionSet emptySet;
    private volatile @Nullable Object compatShim;
    /**
     * Its own monitor rather than the manager's: {@link #rebuildFlagIndex} holds that one across a
     * walk of every region, and a world's first compat query has no reason to wait behind it.
     */
    private final Object compatShimLock = new Object();

    /**
     * Bumped after every edit that can change which flags are set. The index is current while
     * {@link #flagIndexBuiltAt} matches it. A rebuild records the count it started from, so an edit
     * landing mid-walk leaves the two apart and the next read rebuilds again.
     */
    private final AtomicLong flagEdits = new AtomicLong();
    private volatile long flagIndexBuiltAt = -1L;
    private volatile long[] usedFlagBits = EMPTY_BITS;
    private volatile boolean groupsInUse;

    private static final long[] EMPTY_BITS = new long[0];
    private static final Comparator<ProtectedRegion> PRIORITY_DESC =
        Comparator.comparingInt(ProtectedRegion::getPriority).reversed();

    private static final int CHUNK_CACHE_BITS = 14;
    private static final int CHUNK_CACHE_SLOTS = 1 << CHUNK_CACHE_BITS; // 16384
    private static final long CHUNK_HASH_MULTIPLIER = 0x9E3779B97F4A7C15L; // fibonacci hashing
    /**
     * {@code null} until the first query after a load or an edit installs a table. See
     * {@link #invalidateChunkIndex}.
     */
    private volatile @Nullable AtomicReferenceArray<@Nullable ChunkCandidates> chunkIndex;
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<RegionManager, AtomicReferenceArray> CHUNK_INDEX =
        AtomicReferenceFieldUpdater.newUpdater(RegionManager.class, AtomicReferenceArray.class, "chunkIndex");
    private volatile @Nullable SpatialIndex spatialIndex;

    /**
     * Internal (storage backends): route point lookups through {@code index} instead of the chunk
     * cache, or back to the cache with {@code null}. Install it once the manager holds what the index
     * already knows, since only later edits are forwarded. Plugins should not call this.
     */
    public void uwgUseIndex(final @Nullable SpatialIndex index) {
        spatialIndex = index;
        invalidateChunkIndex();
    }

    private void indexPut(final ProtectedRegion region) {
        final SpatialIndex index = spatialIndex;
        if (index != null) {
            index.put(region);
        }
    }

    /**
     * Add {@code region}, replacing whatever held its id.
     *
     * <p>A replacement takes over the old region's place in the tree the same way
     * {@link #redefineRegion} does. Without that, every child kept pointing at the instance that just
     * left the map: it is unreachable, never saved, and still supplying the flags its children
     * inherit, so an import with {@code --overwrite} left the old values in force until a restart.
     * The replaced instance is then detached from this world (see {@code retire}).
     */
    public void addRegion(final ProtectedRegion region) {
        region.uwgOwnedBy(this);
        final String id = region.getId();
        final String key = id.toLowerCase(Locale.ROOT);
        final ProtectedRegion replaced = regions.put(key, region);
        trackCase(id, key, region);
        if (region instanceof GlobalProtectedRegion g) {
            global = g;
        } else if (replaced != null && replaced == global) {
            global = null;
        }
        if (replaced != null && replaced != region) {
            if (replaced.uwgHasChildren()) {
                for (final ProtectedRegion r : regions.values()) {
                    if (r != region && r.getParent() == replaced) {
                        r.setParent(region);
                    }
                }
            }
            if (region.getParent() == replaced) {
                region.setParent(null);
            }
            retire(replaced);
        }
        dirty.set(true);
        flagEdits.incrementAndGet();
        invalidateChunkIndex();
        indexPut(region);
    }

    /**
     * Detach a region that just left the map. Callers of the WorldGuard API keep region objects, and
     * an edit through a stale one used to reach this world: it re-sent the old geometry and flags to
     * the storage index. Its parent link goes too, or the parent kept counting it as a child forever.
     */
    private void retire(final ProtectedRegion gone) {
        gone.uwgOwnedBy(null);
        gone.setParent(null);
        mixedCaseIds.remove(gone.getId(), gone);
    }

    private void trackCase(final String id, final String key, final ProtectedRegion region) {
        if (!key.equals(id)) {
            mixedCaseIds.put(id, region);
        }
    }

    /**
     * Add {@code region} only if its id is free, returning the region already holding that id — or
     * {@code null} on success. For the {@code define} commands, where checking with {@link #hasRegion}
     * and then calling {@link #addRegion} leaves a window: two admins defining the same name from
     * different region threads both pass the check, and the second put silently replaces the first
     * region's bounds, owners and flags while telling both of them it was created.
     */
    public @Nullable ProtectedRegion addRegionIfAbsent(final ProtectedRegion region) {
        final String id = region.getId();
        final String key = id.toLowerCase(Locale.ROOT);
        final ProtectedRegion existing = regions.putIfAbsent(key, region);
        if (existing != null) {
            return existing;
        }
        region.uwgOwnedBy(this);
        trackCase(id, key, region);
        if (region instanceof GlobalProtectedRegion g) {
            global = g;
        }
        dirty.set(true);
        flagEdits.incrementAndGet();
        invalidateChunkIndex();
        indexPut(region);
        return null;
    }

    /**
     * Replace the region holding {@code replacement}'s id with {@code replacement}, which inherits
     * the old region's flags, owners, members, priority and parent, and takes over as the parent of
     * any region that pointed at the old one. Returns the region that was replaced, or {@code null}
     * if the id was not in use.
     *
     * <p>Backs {@code /uwg redefine}. One swap rather than remove-then-add: bounds are immutable, so
     * reshaping means a new instance, and a query landing in the gap between a remove and an add
     * would see the area as unprotected. The state copy runs inside the map's compute, so a second
     * redefine of the same id cannot interleave with it.
     */
    public @Nullable ProtectedRegion redefineRegion(final ProtectedRegion replacement) {
        replacement.uwgOwnedBy(this);
        final String id = replacement.getId();
        final String lower = id.toLowerCase(Locale.ROOT);
        final ProtectedRegion[] previous = new ProtectedRegion[1];
        regions.computeIfPresent(
            lower,
            (key, existing) -> {
                previous[0] = existing;
                replacement.copyStateFrom(existing);
                return replacement;
            });

        final ProtectedRegion replaced = previous[0];
        if (replaced == null) {
            return null;
        }
        trackCase(id, lower, replacement);

        if (replacement instanceof GlobalProtectedRegion g) {
            global = g;
        } else if (replaced == global) {
            global = null;
        }
        if (replaced.uwgHasChildren()) {
            for (final ProtectedRegion r : regions.values()) {
                if (r.getParent() == replaced) {
                    r.setParent(replacement);
                }
            }
        }
        retire(replaced);
        dirty.set(true);
        flagEdits.incrementAndGet();
        invalidateChunkIndex();
        indexPut(replacement);
        return replaced;
    }

    public @Nullable ProtectedRegion removeRegion(final String id) {
        final ProtectedRegion removed = regions.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            removed.uwgOwnedBy(null);
            mixedCaseIds.remove(removed.getId(), removed);
            if (removed == global) {
                global = null;
            }

            if (removed.uwgHasChildren()) {
                for (final ProtectedRegion r : regions.values()) {
                    if (r.getParent() == removed) {
                        r.setParent(null);
                    }
                }
            }
            dirty.set(true);
            flagEdits.incrementAndGet();
            invalidateChunkIndex();
            final SpatialIndex index = spatialIndex;
            if (index != null) {
                index.remove(removed);
            }
        }
        return removed;
    }

    /**
     * Ids are keyed in lower case. An id already in lower case hits on the first lookup, and a
     * mixed-case id as the region spells it hits {@code mixedCaseIds}, so neither allocates. Only an
     * id in some other case pays for lowercasing.
     */
    public @Nullable ProtectedRegion getRegion(final String id) {
        final ProtectedRegion exact = regions.get(id);
        if (exact != null) {
            return exact;
        }
        final ProtectedRegion spelled = mixedCaseIds.get(id);
        if (spelled != null && spelled.uwgOwner() == this) {
            return spelled;
        }
        return regions.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean hasRegion(final String id) {
        return getRegion(id) != null;
    }

    public Collection<ProtectedRegion> getRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public int size() {
        return regions.size();
    }

    /**
     * Regions whose bounding box overlaps the box from {@code a} to {@code b} (inclusive, either
     * corner first), highest priority first. The global region is never included.
     *
     * <p>This is the check a claim needs before it is created: does the new area touch anything that
     * exists? It compares bounding boxes, so for a cylinder, sphere or polygon a hit means the two
     * may overlap. Test {@link ProtectedRegion#contains} on the blocks that matter when that
     * difference counts.
     *
     * <p>Without a backend index this walks every region in the world, so run it when a claim is
     * made, not per move.
     */
    public List<ProtectedRegion> getRegionsIntersecting(final BlockVector3 a, final BlockVector3 b) {
        final int minX = Math.min(a.x(), b.x());
        final int minY = Math.min(a.y(), b.y());
        final int minZ = Math.min(a.z(), b.z());
        final int maxX = Math.max(a.x(), b.x());
        final int maxY = Math.max(a.y(), b.y());
        final int maxZ = Math.max(a.z(), b.z());
        final SpatialIndex index = spatialIndex;
        if (index != null) {
            final List<ProtectedRegion> hits = new ArrayList<>(index.intersecting(minX, minY, minZ, maxX, maxY, maxZ));
            hits.sort(PRIORITY_DESC);
            return hits;
        }
        final List<ProtectedRegion> hits = new ArrayList<>();
        for (final ProtectedRegion region : regions.values()) {
            if (region instanceof GlobalProtectedRegion) {
                continue;
            }
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            if (max.x() >= minX && min.x() <= maxX
                && max.y() >= minY && min.y() <= maxY
                && max.z() >= minZ && min.z() <= maxZ) {
                hits.add(region);
            }
        }
        hits.sort(PRIORITY_DESC);
        return hits;
    }

    /**
     * Internal (compat layer): the WorldGuard-API shim built over this manager, or {@code null} while
     * nothing has asked for one. Plugins should not call this.
     *
     * @see ProtectedRegion#uwgCompatShim()
     */
    public @Nullable Object uwgCompatShim() {
        return compatShim;
    }

    /**
     * Internal (compat layer): publish {@code shim} as this manager's wrapper, returning whichever
     * instance won when two threads wrapped the same manager at once. Plugins should not call this.
     */
    public Object uwgLinkCompatShim(final Object shim) {
        synchronized (compatShimLock) {
            final Object existing = compatShim;
            if (existing != null) {
                return existing;
            }
            compatShim = shim;
            return shim;
        }
    }

    /**
     * Internal (persistence): consume the dirty bit. Plugins should not call this.
     */
    public boolean clearDirty() {
        return dirty.getAndSet(false);
    }

    /**
     * Mark this world's regions as needing a save — call after mutating a region's
     * flags, domains, priority, or parent directly.
     */
    public void markDirty() {
        dirty.set(true);
        flagEdits.incrementAndGet();
    }

    /**
     * {@code region}'s flags, group qualifiers, priority or parent changed. Owners and members have no
     * way back to their region, so those edits reach an index at the next add or save instead.
     *
     * <p>Only a flag or group edit retires the flag index. Priority and parent do not change which
     * flags any region sets, and a stale index costs the next query on a hot thread a full rebuild.
     */
    void regionEdited(final ProtectedRegion region, final boolean flagsChanged) {
        if (flagsChanged) {
            markDirty();
        } else {
            dirty.set(true);
        }
        indexPut(region);
    }

    /**
     * Whether any region in this world sets {@code flag} directly. Backed by a bitset over
     * {@link Flag#getIndex()} rebuilt lazily after a mutation, so a check is a volatile read plus one
     * word test — cheap enough to gate per-move flag reads, not just the once-a-second services.
     * Inherited flags count because the parent that defines them is itself a region.
     */
    public boolean anyRegionUses(final Flag<?> flag) {
        if (flagIndexBuiltAt != flagEdits.get()) {
            rebuildFlagIndex();
        }
        final int index = flag.getIndex();
        if (index < 0) {
            return false;
        }
        final long[] bits = usedFlagBits;
        final int word = index >> 6;
        return word < bits.length && (bits[word] & (1L << index)) != 0L;
    }

    /**
     * Readers that find the index stale wait here rather than read the old bits, which would answer
     * "unset" for a flag set a moment ago.
     */
    private synchronized void rebuildFlagIndex() {
        final long target = flagEdits.get();
        if (flagIndexBuiltAt == target) {
            return;
        }
        final long[] bits = new long[(Flags.count() >> 6) + 1];
        boolean any = false;
        boolean groups = false;
        for (final ProtectedRegion region : regions.values()) {
            if (!region.getFlagGroups().isEmpty()) {
                groups = true;
            }
            for (final Flag<?> flag : region.getFlags().keySet()) {
                final int index = flag.getIndex();
                if (index >= 0 && (index >> 6) < bits.length) {
                    bits[index >> 6] |= 1L << index;
                    any = true;
                }
            }
        }
        usedFlagBits = any ? bits : EMPTY_BITS;
        groupsInUse = groups;
        flagIndexBuiltAt = target;
    }

    /**
     * Whether any region in this world carries a group qualifier. Almost no server uses them, so this
     * lets flag resolution skip the per-region association check entirely rather than paying two map
     * lookups per region per flag on the hottest path in the plugin.
     */
    boolean anyFlagGroups() {
        if (flagIndexBuiltAt != flagEdits.get()) {
            rebuildFlagIndex();
        }
        return groupsInUse;
    }

    public ApplicableRegionSet getApplicableRegions(final BlockVector3 point) {
        return getApplicableRegions(point.x(), point.y(), point.z());
    }

    /**
     * Kept to a dispatch: the whole lookup was 380 bytes of bytecode, over C2's 325-byte limit for hot
     * callees, so it was never inlined into the query path that calls it on every event.
     */
    public ApplicableRegionSet getApplicableRegions(final int x, final int y, final int z) {
        final SpatialIndex index = spatialIndex;
        return index != null ? indexedAt(index, x, y, z) : cachedAt(x, y, z);
    }

    private ApplicableRegionSet indexedAt(final SpatialIndex index, final int x, final int y, final int z) {
        final List<ProtectedRegion> owned = index.candidatesAt(x, y, z);
        if (owned.isEmpty() || !retainContaining(owned, x, y, z)) {
            return emptySet();
        }
        return new ApplicableRegionSet(owned, global, this,
            owned instanceof SpatialIndex.FlagResolver resolver ? resolver : null);
    }

    private ApplicableRegionSet cachedAt(final int x, final int y, final int z) {
        final List<ProtectedRegion> candidates = chunkCandidates(chunkKey(x, z));
        if (candidates.isEmpty()) {
            return emptySet();
        }
        return containing(candidates, x, y, z);
    }

    private List<ProtectedRegion> chunkCandidates(final long key) {
        final AtomicReferenceArray<@Nullable ChunkCandidates> current = chunkIndex;
        final AtomicReferenceArray<@Nullable ChunkCandidates> cache = current != null ? current : installChunkIndex();
        final int slot = (int) ((key * CHUNK_HASH_MULTIPLIER) >>> (64 - CHUNK_CACHE_BITS));
        final ChunkCandidates cached = cache.get(slot);
        if (cached != null && cached.key() == key) {
            return cached.regions();
        }
        final List<ProtectedRegion> built = buildChunkCandidates(key);
        cache.set(slot, new ChunkCandidates(key, built));
        return built;
    }

    private ApplicableRegionSet containing(final List<ProtectedRegion> candidates, final int x, final int y, final int z) {
        List<ProtectedRegion> matches = null;
        for (int i = 0, n = candidates.size(); i < n; i++) {
            final ProtectedRegion region = candidates.get(i);
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            if (x < min.x() || x > max.x() || y < min.y() || y > max.y() || z < min.z() || z > max.z()) {
                continue;
            }
            if (region.contains(x, y, z)) {
                if (matches == null) {
                    matches = new ArrayList<>(4);
                }
                matches.add(region);
            }
        }
        if (matches == null) {
            return emptySet();
        }
        return new ApplicableRegionSet(matches, global, this, null);
    }

    /**
     * Drops, in place, the index's candidates that do not hold the point. Returns whether any are left.
     * The index over-reports by bounding box, so this is usually a pass that removes nothing.
     */
    private static boolean retainContaining(final List<ProtectedRegion> candidates, final int x, final int y, final int z) {
        int kept = 0;
        final int n = candidates.size();
        for (int i = 0; i < n; i++) {
            final ProtectedRegion region = candidates.get(i);
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            if (x < min.x() || x > max.x() || y < min.y() || y > max.y() || z < min.z() || z > max.z()
                || !region.contains(x, y, z)) {
                continue;
            }
            if (kept != i) {
                candidates.set(kept, region);
            }
            kept++;
        }
        if (kept < n) {
            candidates.subList(kept, n).clear();
        }
        return kept > 0;
    }

    /**
     * Whether {@link #stateAcross} can ever answer, so a caller can skip computing a bounding box.
     */
    boolean resolvesBoxes() {
        return spatialIndex != null;
    }

    /**
     * What {@link ApplicableRegionSet#queryState(StateFlag)} returns at every block of the box, or
     * {@code null} when that is not proven and each block has to be tested.
     */
    @Nullable State stateAcross(
        final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ,
        final StateFlag flag
    ) {
        final SpatialIndex index = spatialIndex;
        return index == null ? null : index.stateAcross(minX, minY, minZ, maxX, maxY, maxZ, flag, anyFlagGroups());
    }

    /**
     * Regions whose XZ bounding box overlaps the given chunk — the candidate set every query in
     * that chunk is narrowed to. Built once per chunk (the only full scan), then cached. The Y
     * bound and exact {@link ProtectedRegion#contains} are still tested per query, so non-cuboid
     * shapes resolve correctly. Global regions are excluded; they are not spatial.
     */
    private List<ProtectedRegion> buildChunkCandidates(final long key) {
        final int minBx = ((int) (key >> 32)) << 4;
        final int maxBx = minBx + 15;
        final int minBz = ((int) key) << 4;
        final int maxBz = minBz + 15;
        List<ProtectedRegion> list = null;
        for (final ProtectedRegion region : regions.values()) {
            if (region instanceof GlobalProtectedRegion) {
                continue;
            }
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            if (max.x() < minBx || min.x() > maxBx || max.z() < minBz || min.z() > maxBz) {
                continue;
            }
            if (list == null) {
                list = new ArrayList<>(4);
            }
            list.add(region);
        }
        return list == null ? List.of() : list;
    }

    private static long chunkKey(final int x, final int z) {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }

    /**
     * A published cache slot: the chunk key it answers for, and the regions whose XZ bounds overlap
     * that chunk. The region list is a fresh snapshot never mutated after construction, so the record
     * is safe to read without locking once stored via {@link AtomicReferenceArray#set} (a volatile
     * write that publishes it).
     */
    private record ChunkCandidates(long key, List<ProtectedRegion> regions) {}

    /**
     * Drop the per-chunk cache on a region add/remove (which can change what overlaps a chunk).
     * Unpublishes the table rather than clearing it in place, so a query mid-build can never publish a
     * stale candidate list: its store lands in the now-orphaned old table. The table is a
     * fixed-capacity direct-mapped cache: a fresh chunk hashing to an occupied slot simply overwrites
     * it, bounding memory without per-lookup boxing or eviction bookkeeping.
     *
     * <p>The next query installs a new table, not this call. A load or import adds regions one at a
     * time, and allocating the 64 KB table per add made N regions cost N tables of garbage.
     */
    private void invalidateChunkIndex() {
        chunkIndex = null;
    }

    /**
     * Installs a fresh table unless another query already did. A compare-and-set rather than a plain
     * write: a plain write could land after an invalidation and republish a table filled from the
     * regions as they were before it.
     */
    @SuppressWarnings("unchecked")
    private AtomicReferenceArray<@Nullable ChunkCandidates> installChunkIndex() {
        final AtomicReferenceArray<@Nullable ChunkCandidates> fresh = new AtomicReferenceArray<>(CHUNK_CACHE_SLOTS);
        if (CHUNK_INDEX.compareAndSet(this, null, fresh)) {
            return fresh;
        }
        final AtomicReferenceArray<@Nullable ChunkCandidates> raced = chunkIndex;
        return raced != null ? raced : fresh;
    }

    /**
     * Cached no-match result. Most queries hit unprotected wilderness, so the empty set is
     * reused instead of allocated per event.
     *
     * <p>Rebuilt when the global region changes <em>or</em> when the world's group-qualifier state
     * flips. A set snapshots {@code groupsInUse} at construction, and the empty set is the one place
     * that snapshot can outlive the fact: with no applicable regions the only value left to resolve
     * is the global region's, so a qualifier added to it afterwards would be ignored in wilderness
     * for as long as the cached set survived.
     */
    private ApplicableRegionSet emptySet() {
        final GlobalProtectedRegion g = global;
        final boolean groups = anyFlagGroups();
        ApplicableRegionSet cached = emptySet;
        if (cached == null || cached.globalRegion() != g || cached.usesGroups() != groups) {
            cached = new ApplicableRegionSet(List.of(), g, this, null);
            emptySet = cached;
        }
        return cached;
    }
}
