package com.tricrotism.uworldguard.universe;

import com.tricrotism.uworldguard.domain.DefaultDomain;
import com.tricrotism.uworldguard.flags.*;
import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.util.BlockVector3;
import com.universeprojects.api.region.Region;
import com.universeprojects.api.region.RegionFlags;
import com.universeprojects.api.region.flag.FlagKey;
import com.universeprojects.api.region.shape.*;
import com.universeprojects.api.region.storage.RegionDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Converts between our regions and uSpigot's. Everything uSpigot has no field for rides along as
 * extra flags, so a region read back is the region that was written: owners and members as string
 * sets, group qualifiers as {@code <flag>-group}, and the exact geometry of an ellipsoid, which
 * uSpigot's single-radius shapes cannot hold.
 *
 * <p>Stored values are canonical (booleans for state flags, sorted string lists for sets), so two
 * definitions of the same region compare equal whichever side built them.
 */
@NullMarked final class UniverseRegions {

    static final String OWNERS = "uwg-owners";
    static final String MEMBERS = "uwg-members";
    static final String SHAPE = "uwg-shape";
    private static final String GROUP_SUFFIX = "-group";
    private static final String GROUP_ENTRY = "g:";

    private final RegionFlags flags;
    private final Logger log;
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    /**
     * List-valued keys are registered up front rather than on first write. uSpigot loads its stored
     * regions when a world loads, before anything here writes, and a list under an unregistered key
     * is kept untyped with one warning per region on every boot.
     */
    UniverseRegions(final RegionFlags flags, final Logger log) {
        this.flags = flags;
        this.log = log;
        registerStringSet(OWNERS);
        registerStringSet(MEMBERS);
        for (final Flag<?> flag : Flags.all()) {
            if (flag instanceof MaterialSetFlag || flag instanceof EntityTypeSetFlag
                || flag instanceof PotionEffectSetFlag || flag instanceof StringSetFlag) {
                registerStringSet(flag.getName());
            }
        }
    }

    private void registerStringSet(final String key) {
        if (registered.add(key)) {
            flags.registerStringSetFlag(key, null);
        }
    }

    RegionDefinition definition(final String world, final ProtectedRegion region) {
        final Map<String, Object> stored = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> e : region.getUnresolvedFlags().entrySet()) {
            put(stored, e.getKey(), canonical(e.getValue()));
        }
        for (final Map.Entry<Flag<?>, Object> e : region.getFlags().entrySet()) {
            final Object value = e.getValue();
            put(stored, e.getKey().getName(),
                value instanceof State state ? (Object) (state == State.ALLOW) : canonical(marshal(e.getKey(), value)));
        }
        for (final Map.Entry<Flag<?>, RegionGroup> e : region.getFlagGroups().entrySet()) {
            put(stored, e.getKey().getName() + GROUP_SUFFIX, e.getValue().serialized());
        }
        putDomain(stored, OWNERS, region.getOwners());
        putDomain(stored, MEMBERS, region.getMembers());
        final RegionShape shape = shape(region, stored);
        final ProtectedRegion parent = region.getParent();
        return new RegionDefinition(region.getId(), world, region.getPriority(), shape, stored,
            parent == null ? null : parent.getId());
    }

    /**
     * {@code region} in the stored form {@link #definition} writes, for comparing the two.
     */
    RegionDefinition definitionOf(final Region region) {
        final Map<String, Object> stored = new LinkedHashMap<>();
        for (final Map.Entry<FlagKey<?>, Object> e : region.localFlags().entrySet()) {
            stored.put(e.getKey().name(), canonical(e.getValue()));
        }
        final Region parent = region.parent();
        return new RegionDefinition(region.id(), region.world(), region.priority(), region.shape(), stored,
            parent == null ? null : parent.id());
    }

    /**
     * Whether {@code a} and {@code b} describe the same region, reading any two whole numbers (or any
     * two decimals) of equal value as equal. uSpigot may hand back an int flag as a {@code Long} that
     * was written as an {@code Integer}, and a strict comparison would then never settle.
     */
    static boolean same(final RegionDefinition a, final RegionDefinition b) {
        if (a.priority() != b.priority() || !a.id().equalsIgnoreCase(b.id())
            || !Objects.equals(lower(a.parent()), lower(b.parent()))
            || !a.shape().equals(b.shape()) || a.flags().size() != b.flags().size()) {
            return false;
        }
        for (final Map.Entry<String, Object> e : a.flags().entrySet()) {
            if (!sameValue(e.getValue(), b.flags().get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * What differs between two definitions of one region, in the terms
     * {@code RegionExternalChangeEvent.getChanges} documents.
     */
    static List<String> differences(final RegionDefinition before, final RegionDefinition after) {
        final Set<String> out = new LinkedHashSet<>();
        if (!before.shape().equals(after.shape())
            || !Objects.equals(before.flags().get(SHAPE), after.flags().get(SHAPE))) {
            out.add("shape");
        }
        if (before.priority() != after.priority()) {
            out.add("priority");
        }
        if (!Objects.equals(lower(before.parent()), lower(after.parent()))) {
            out.add("parent");
        }
        final Set<String> keys = new TreeSet<>(before.flags().keySet());
        keys.addAll(after.flags().keySet());
        for (final String key : keys) {
            final Object a = before.flags().get(key);
            final Object b = after.flags().get(key);
            if (key.equals(SHAPE) || (a == null ? b == null : sameValue(a, b))) {
                continue;
            }
            out.add(switch (key) {
                case OWNERS -> "owners";
                case MEMBERS -> "members";
                default ->
                    "flag:" + (key.endsWith(GROUP_SUFFIX) ? key.substring(0, key.length() - GROUP_SUFFIX.length()) : key);
            });
        }
        return List.copyOf(out);
    }

    private static @Nullable String lower(final @Nullable String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static boolean sameValue(final Object a, final @Nullable Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return isWhole(x) && isWhole(y) ? x.longValue() == y.longValue() : x.doubleValue() == y.doubleValue();
        }
        return a.equals(b);
    }

    private static boolean isWhole(final Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    /**
     * Our region for {@code region}, parented to whatever {@code manager} holds under the parent's
     * id, or {@code null} for a shape we have no region type for.
     */
    @Nullable ProtectedRegion toProtected(final Region region, final RegionManager manager) {
        final RegionDefinition def = definitionOf(region);
        ProtectedRegion out = exactShape(def.id(), def.flags().get(SHAPE));
        if (out == null) {
            out = shapeOf(def.id(), def.shape());
        }
        if (out == null) {
            log.warning("UniverseSpigot region '" + def.id() + "' in world '" + def.world() + "' has a "
                + def.shape().getClass().getSimpleName() + " shape, which uWorldGuard cannot represent."
                + " It is left to UniverseSpigot and does not protect through uWorldGuard.");
            return null;
        }
        out.setPriority(def.priority());
        for (final Map.Entry<String, Object> e : def.flags().entrySet()) {
            apply(out, e.getKey(), e.getValue());
        }
        final String parentId = def.parent();
        if (parentId != null) {
            final ProtectedRegion parent = manager.getRegion(parentId);
            if (parent != null) {
                try {
                    out.setParent(parent);
                } catch (final IllegalArgumentException e) {
                    log.warning("Region '" + def.id() + "' cannot have '" + parentId + "' as its parent: "
                        + e.getMessage());
                }
            }
        }
        return out;
    }

    private void apply(final ProtectedRegion region, final String key, final Object stored) {
        switch (key) {
            case SHAPE -> {
            }
            case OWNERS -> readDomain(stored, region.getOwners());
            case MEMBERS -> readDomain(stored, region.getMembers());
            default -> {
                if (key.endsWith(GROUP_SUFFIX)) {
                    final Flag<?> flag = Flags.get(key.substring(0, key.length() - GROUP_SUFFIX.length()));
                    final RegionGroup group = RegionGroup.parse(String.valueOf(stored));
                    if (flag != null && group != null) {
                        region.setFlagGroup(flag, group);
                        return;
                    }
                }
                final Flag<?> flag = Flags.get(key);
                if (flag == null) {
                    region.putUnresolvedFlag(key, stored);
                } else if (!setFlag(region, flag, stored)) {
                    // kept as stored so a save writes it back unchanged rather than deleting it
                    region.putUnresolvedFlag(key, stored);
                    log.warning("Region '" + region.getId() + "' has a value for flag '" + key
                        + "' that uWorldGuard cannot read: " + stored + ". It has no effect here and is"
                        + " kept as stored.");
                }
            }
        }
    }

    private static <T> boolean setFlag(final ProtectedRegion region, final Flag<T> flag, final Object stored) {
        final T value = flag.unmarshal(stored);
        if (value == null) {
            return false;
        }
        region.setFlag(flag, value);
        return true;
    }

    /**
     * Ellipsoids widen to the single radius uSpigot can index, so its bounds always hold ours, and
     * the exact radii go in {@link #SHAPE}. The manager tests our own containment on every candidate,
     * so the wider index shape never grants an extra block.
     */
    private RegionShape shape(final ProtectedRegion region, final Map<String, Object> stored) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return switch (region) {
            case GlobalProtectedRegion _ -> Global.INSTANCE;
            case ProtectedCylinderRegion _ -> {
                final int cx = (min.x() + max.x()) / 2;
                final int cz = (min.z() + max.z()) / 2;
                final int rx = (max.x() - min.x()) / 2;
                final int rz = (max.z() - min.z()) / 2;
                if (rx != rz) {
                    put(stored, SHAPE, "cylinder:" + cx + "," + cz + "," + rx + "," + rz + "," + min.y() + "," + max.y());
                }
                yield Cylinder.of(cx, cz, Math.max(rx, rz), min.y(), max.y());
            }
            case ProtectedSphereRegion _ -> {
                final int cx = (min.x() + max.x()) / 2;
                final int cy = (min.y() + max.y()) / 2;
                final int cz = (min.z() + max.z()) / 2;
                final int rx = (max.x() - min.x()) / 2;
                final int ry = (max.y() - min.y()) / 2;
                final int rz = (max.z() - min.z()) / 2;
                if (rx != ry || ry != rz) {
                    put(stored, SHAPE, "sphere:" + cx + "," + cy + "," + cz + "," + rx + "," + ry + "," + rz);
                }
                yield Sphere.of(cx, cy, cz, Math.max(rx, Math.max(ry, rz)));
            }
            default -> nestedShape(region);
        };
    }

    /**
     * Whether uSpigot can hold this region's geometry. Only a top-level ellipsoid has somewhere to
     * keep its exact radii ({@link #SHAPE}), so one nested in a composite or carved region cannot go
     * there without changing the area it covers.
     */
    static boolean representable(final ProtectedRegion region) {
        return switch (region) {
            case ProtectedCompositeRegion c -> allExact(c.getParts().values());
            case ProtectedCarvedRegion c -> exact(c.getSolid()) && allExact(c.getHoles().values());
            default -> true;
        };
    }

    private static boolean allExact(final Collection<ProtectedRegion> regions) {
        for (final ProtectedRegion region : regions) {
            if (!exact(region)) {
                return false;
            }
        }
        return true;
    }

    private static boolean exact(final ProtectedRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        final int rx = max.x() - min.x();
        final int ry = max.y() - min.y();
        final int rz = max.z() - min.z();
        return switch (region) {
            case ProtectedCylinderRegion _ -> rx == rz;
            case ProtectedSphereRegion _ -> rx == ry && ry == rz;
            default -> representable(region);
        };
    }

    /**
     * The shape for a region {@link #representable} accepts, with no widening, for use as a composite
     * part or a carved region's solid or hole.
     */
    private static RegionShape nestedShape(final ProtectedRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return switch (region) {
            case ProtectedCuboidRegion _ -> Cuboid.of(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
            case ProtectedPolygonRegion p -> {
                final List<BlockVector3> points = p.getPoints();
                final int[] xs = new int[points.size()];
                final int[] zs = new int[points.size()];
                for (int i = 0; i < xs.length; i++) {
                    xs[i] = points.get(i).x();
                    zs[i] = points.get(i).z();
                }
                yield new Polygon(xs, zs, min.y(), max.y());
            }
            case ProtectedCylinderRegion _ -> Cylinder.of((min.x() + max.x()) / 2, (min.z() + max.z()) / 2,
                (max.x() - min.x()) / 2, min.y(), max.y());
            case ProtectedSphereRegion _ -> Sphere.of((min.x() + max.x()) / 2, (min.y() + max.y()) / 2,
                (min.z() + max.z()) / 2, (max.x() - min.x()) / 2);
            case ProtectedPolyhedronRegion p -> {
                final List<BlockVector3> points = p.getPoints();
                final int[] xs = new int[points.size()];
                final int[] ys = new int[points.size()];
                final int[] zs = new int[points.size()];
                for (int i = 0; i < xs.length; i++) {
                    xs[i] = points.get(i).x();
                    ys[i] = points.get(i).y();
                    zs[i] = points.get(i).z();
                }
                yield Polyhedron.of(xs, ys, zs);
            }
            case ProtectedCompositeRegion c -> {
                final Map<String, RegionShape> parts = new LinkedHashMap<>();
                c.getParts().forEach((name, part) -> parts.put(name, nestedShape(part)));
                yield Composite.of(parts);
            }
            case ProtectedCarvedRegion c -> {
                final Map<String, RegionShape> holes = new LinkedHashMap<>();
                c.getHoles().forEach((name, hole) -> holes.put(name, nestedShape(hole)));
                yield Carved.of(nestedShape(c.getSolid()), holes);
            }
            default -> throw new IllegalArgumentException("Region '" + region.getId() + "' has a "
                + region.getType() + " shape, which has no UniverseSpigot equivalent");
        };
    }

    /**
     * The ellipsoid stored in {@link #SHAPE}, or {@code null} when there is none. The value comes from
     * uSpigot's files, so one that will not parse is reported and the region falls back to the wider
     * shape uSpigot indexes, which over-protects rather than dropping the region.
     */
    private @Nullable ProtectedRegion exactShape(final String id, final @Nullable Object exact) {
        if (exact == null) {
            return null;
        }
        final String s = String.valueOf(exact);
        final int colon = s.indexOf(':');
        try {
            final int[] v = Arrays.stream(s.substring(colon + 1).split(",")).mapToInt(p -> Integer.parseInt(p.trim())).toArray();
            if (colon > 0 && v.length == 6) {
                switch (s.substring(0, colon)) {
                    case "sphere" -> {
                        return new ProtectedSphereRegion(id, v[0], v[1], v[2], v[3], v[4], v[5]);
                    }
                    case "cylinder" -> {
                        return new ProtectedCylinderRegion(id, v[0], v[1], v[2], v[3], v[4], v[5]);
                    }
                    default -> {
                    }
                }
            }
        } catch (final NumberFormatException _) {
            // reported below
        }
        log.warning("Region '" + id + "' has an unreadable " + SHAPE + " value: '" + s + "'. Using the"
            + " shape UniverseSpigot stores for it, which may cover more blocks than before.");
        return null;
    }

    private static @Nullable ProtectedRegion shapeOf(final String id, final RegionShape shape) {
        return switch (shape) {
            case Global _ -> new GlobalProtectedRegion();
            case Cuboid c -> new ProtectedCuboidRegion(id,
                BlockVector3.at(c.minX(), c.minY(), c.minZ()), BlockVector3.at(c.maxX(), c.maxY(), c.maxZ()));
            case Sphere s -> new ProtectedSphereRegion(id,
                s.centerX(), s.centerY(), s.centerZ(), s.radius(), s.radius(), s.radius());
            case Cylinder c -> new ProtectedCylinderRegion(id,
                c.centerX(), c.centerZ(), c.radius(), c.radius(), c.minY(), c.maxY());
            case Polygon p -> {
                final int[] xs = p.xs();
                final int[] zs = p.zs();
                final List<BlockVector3> points = new ArrayList<>(xs.length);
                for (int i = 0; i < xs.length; i++) {
                    points.add(BlockVector3.at(xs[i], p.minY(), zs[i]));
                }
                yield new ProtectedPolygonRegion(id, points, p.minY(), p.maxY());
            }
            case Polyhedron p -> {
                final int[] xs = p.vertX();
                final int[] ys = p.vertY();
                final int[] zs = p.vertZ();
                final List<BlockVector3> points = new ArrayList<>(xs.length);
                for (int i = 0; i < xs.length; i++) {
                    points.add(BlockVector3.at(xs[i], ys[i], zs[i]));
                }
                yield new ProtectedPolyhedronRegion(id, points);
            }
            case Composite c -> {
                final Map<String, ProtectedRegion> parts = shapesOf(c.parts());
                yield parts == null ? null : new ProtectedCompositeRegion(id, parts);
            }
            case Carved c -> {
                final ProtectedRegion solid = shapeOf("solid", c.solid());
                final Map<String, ProtectedRegion> holes = shapesOf(c.holes());
                yield solid == null || holes == null ? null : new ProtectedCarvedRegion(id, solid, holes);
            }
            default -> null;
        };
    }

    /**
     * Each shape converted under its name, or {@code null} if any one has no region type here, since
     * a composite missing a part or a carved region missing a hole would cover the wrong area.
     */
    private static @Nullable Map<String, ProtectedRegion> shapesOf(final Map<String, RegionShape> shapes) {
        final Map<String, ProtectedRegion> out = new LinkedHashMap<>();
        for (final Map.Entry<String, RegionShape> e : shapes.entrySet()) {
            final ProtectedRegion region = shapeOf(e.getKey(), e.getValue());
            if (region == null) {
                return null;
            }
            out.put(e.getKey(), region);
        }
        return out;
    }

    private void put(final Map<String, Object> stored, final String key, final Object value) {
        stored.put(key, value);
        if (registered.add(key)) {
            switch (value) {
                case Boolean _ -> flags.registerFlag(key, RegionFlags.FlagType.BOOLEAN);
                case Integer _ -> flags.registerFlag(key, RegionFlags.FlagType.INTEGER);
                case Long _ -> flags.registerFlag(key, RegionFlags.FlagType.LONG);
                case Number _ -> flags.registerFlag(key, RegionFlags.FlagType.DOUBLE);
                case List<?> _ -> flags.registerStringSetFlag(key, null);
                default -> flags.registerFlag(key, RegionFlags.FlagType.STRING);
            }
        }
    }

    private void putDomain(final Map<String, Object> stored, final String key, final DefaultDomain domain) {
        if (domain.isEmpty()) {
            return;
        }
        final List<String> entries = new ArrayList<>(domain.size());
        for (final UUID uuid : domain.getPlayers()) {
            entries.add(uuid.toString());
        }
        for (final String group : domain.getGroups()) {
            entries.add(GROUP_ENTRY + group);
        }
        Collections.sort(entries);
        put(stored, key, entries);
    }

    private void readDomain(final Object stored, final DefaultDomain domain) {
        if (!(stored instanceof Collection<?> entries)) {
            return;
        }
        for (final Object entry : entries) {
            final String s = String.valueOf(entry);
            if (s.startsWith(GROUP_ENTRY)) {
                domain.addGroup(s.substring(GROUP_ENTRY.length()));
                continue;
            }
            try {
                domain.addPlayer(UUID.fromString(s));
            } catch (final IllegalArgumentException _) {
                log.warning("Dropped a region owner or member entry that is not a UUID: '" + s + "'");
            }
        }
    }

    /**
     * Sets and lists become sorted string lists, so the order a set happened to iterate in never
     * makes two equal regions compare unequal.
     */
    private static Object canonical(final Object value) {
        if (value instanceof Collection<?> c) {
            final List<String> out = new ArrayList<>(c.size());
            for (final Object o : c) {
                out.add(String.valueOf(o));
            }
            Collections.sort(out);
            return out;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object marshal(final Flag<?> flag, final Object value) {
        return ((Flag<Object>) flag).marshal(value);
    }
}
