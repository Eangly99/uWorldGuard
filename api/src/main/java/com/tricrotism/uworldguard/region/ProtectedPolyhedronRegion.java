package com.tricrotism.uworldguard.region;

import com.tricrotism.uworldguard.util.BlockVector3;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * The convex hull of a set of points: the smallest convex solid that holds them all, such as a
 * WorldEdit convex selection. A block is inside when it lies inside or on every face. Points that end
 * up inside the hull are dropped, so {@link #getPoints()} returns only its corners.
 *
 * <p>Every coordinate is taken relative to the minimum corner and the span on each axis is capped at
 * {@link #MAX_SPAN}, the same limit UniverseSpigot uses, which keeps all the plane arithmetic
 * exact in a {@code long}.
 */
@NullMarked
public final class ProtectedPolyhedronRegion extends ProtectedRegion {

    public static final int MAX_SPAN = 1_000_000;

    private final BlockVector3 min;
    private final BlockVector3 max;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int[] cornerX;
    private final int[] cornerY;
    private final int[] cornerZ;
    // a, b, c, d per face, side by side so one face is one contiguous read
    private final long[] planes;

    /**
     * @throws IllegalArgumentException if the points do not span a solid (fewer than four, or all in one plane) or span more than {@link #MAX_SPAN} on any axis
     */
    public ProtectedPolyhedronRegion(final String id, final List<BlockVector3> points) {
        super(id);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Polyhedron needs at least 4 points");
        }
        int loX = Integer.MAX_VALUE;
        int loY = Integer.MAX_VALUE;
        int loZ = Integer.MAX_VALUE;
        int hiX = Integer.MIN_VALUE;
        int hiY = Integer.MIN_VALUE;
        int hiZ = Integer.MIN_VALUE;
        for (final BlockVector3 p : points) {
            loX = Math.min(loX, p.x());
            loY = Math.min(loY, p.y());
            loZ = Math.min(loZ, p.z());
            hiX = Math.max(hiX, p.x());
            hiY = Math.max(hiY, p.y());
            hiZ = Math.max(hiZ, p.z());
        }
        if ((long) hiX - loX > MAX_SPAN || (long) hiY - loY > MAX_SPAN || (long) hiZ - loZ > MAX_SPAN) {
            throw new IllegalArgumentException("Polyhedron spans more than " + MAX_SPAN + " blocks on an axis");
        }
        this.min = BlockVector3.at(loX, loY, loZ);
        this.max = BlockVector3.at(hiX, hiY, hiZ);
        this.minX = loX;
        this.minY = loY;
        this.minZ = loZ;
        this.maxX = hiX;
        this.maxY = hiY;
        this.maxZ = hiZ;

        final Hull hull = new Hull(points, loX, loY, loZ);
        final int[] corners = hull.corners();
        this.cornerX = new int[corners.length];
        this.cornerY = new int[corners.length];
        this.cornerZ = new int[corners.length];
        for (int i = 0; i < corners.length; i++) {
            cornerX[i] = hull.x[corners[i]];
            cornerY[i] = hull.y[corners[i]];
            cornerZ[i] = hull.z[corners[i]];
        }
        this.planes = hull.planes();
    }

    @Override
    public RegionType getType() {
        return RegionType.POLYHEDRON;
    }

    @Override
    public boolean contains(final int x, final int y, final int z) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            return false;
        }
        final long lx = x - minX;
        final long ly = y - minY;
        final long lz = z - minZ;
        final long[] p = planes;
        for (int i = 0; i < p.length; i += 4) {
            if (p[i] * lx + p[i + 1] * ly + p[i + 2] * lz > p[i + 3]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return min;
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return max;
    }

    /**
     * The hull's corners, in no particular order. Building a polyhedron from these gives the same region.
     */
    public List<BlockVector3> getPoints() {
        final List<BlockVector3> out = new ArrayList<>(cornerX.length);
        for (int i = 0; i < cornerX.length; i++) {
            out.add(BlockVector3.at(minX + cornerX[i], minY + cornerY[i], minZ + cornerZ[i]));
        }
        return out;
    }

    /**
     * Incremental 3D convex hull over points relative to the minimum corner. With coordinates at most
     * {@link #MAX_SPAN}, a cross product stays under 2e12 per component and an orientation test under
     * 6e18, so every comparison is exact.
     */
    private static final class Hull {
        final int[] x;
        final int[] y;
        final int[] z;
        final List<int[]> faces = new ArrayList<>();

        Hull(final List<BlockVector3> points, final int loX, final int loY, final int loZ) {
            final Set<Long> seen = new HashSet<>();
            final List<int[]> unique = new ArrayList<>(points.size());
            for (final BlockVector3 p : points) {
                final int lx = p.x() - loX;
                final int ly = p.y() - loY;
                final int lz = p.z() - loZ;
                if (seen.add(((long) lx << 42) | ((long) ly << 21) | lz)) {
                    unique.add(new int[]{lx, ly, lz});
                }
            }
            final int n = unique.size();
            x = new int[n];
            y = new int[n];
            z = new int[n];
            for (int i = 0; i < n; i++) {
                x[i] = unique.get(i)[0];
                y[i] = unique.get(i)[1];
                z[i] = unique.get(i)[2];
            }
            build(n);
        }

        private void build(final int n) {
            int i1 = -1;
            int i2 = -1;
            int i3 = -1;
            for (int i = 1; i < n && i1 < 0; i++) {
                i1 = i;
            }
            for (int i = 1; i < n && i1 >= 0 && i2 < 0; i++) {
                if (!collinear(0, i1, i)) {
                    i2 = i;
                }
            }
            for (int i = 1; i < n && i2 >= 0 && i3 < 0; i++) {
                if (orient(0, i1, i2, i) != 0) {
                    i3 = i;
                }
            }
            if (i3 < 0) {
                throw new IllegalArgumentException("Polyhedron needs at least 4 points that are not all in one plane");
            }
            final int[] tetra = {0, i1, i2, i3};
            for (int f = 0; f < 4; f++) {
                final int a = tetra[f];
                final int b = tetra[(f + 1) % 4];
                final int c = tetra[(f + 2) % 4];
                final int opposite = tetra[(f + 3) % 4];
                faces.add(orient(a, b, c, opposite) > 0 ? new int[]{a, c, b} : new int[]{a, b, c});
            }
            for (int p = 1; p < n; p++) {
                if (p == i1 || p == i2 || p == i3) {
                    continue;
                }
                addPoint(p, n);
            }
        }

        /**
         * Removes the faces {@code p} can see and closes the hole with faces from its horizon to
         * {@code p}. Each horizon edge keeps the winding it had in its removed face, so new faces face
         * outward too.
         */
        private void addPoint(final int p, final int n) {
            final Set<Long> edges = new HashSet<>();
            final List<int[]> kept = new ArrayList<>(faces.size());
            boolean visible = false;
            for (final int[] f : faces) {
                if (orient(f[0], f[1], f[2], p) > 0) {
                    visible = true;
                    edges.add((long) f[0] * n + f[1]);
                    edges.add((long) f[1] * n + f[2]);
                    edges.add((long) f[2] * n + f[0]);
                } else {
                    kept.add(f);
                }
            }
            if (!visible) {
                return;
            }
            for (final long edge : edges) {
                final int a = (int) (edge / n);
                final int b = (int) (edge % n);
                if (!edges.contains((long) b * n + a)) {
                    kept.add(new int[]{a, b, p});
                }
            }
            faces.clear();
            faces.addAll(kept);
        }

        private boolean collinear(final int a, final int b, final int c) {
            final long ux = x[b] - x[a];
            final long uy = y[b] - y[a];
            final long uz = z[b] - z[a];
            final long vx = x[c] - x[a];
            final long vy = y[c] - y[a];
            final long vz = z[c] - z[a];
            return uy * vz - uz * vy == 0 && uz * vx - ux * vz == 0 && ux * vy - uy * vx == 0;
        }

        /**
         * Positive when {@code p} lies on the outer side of face {@code a,b,c}.
         */
        private long orient(final int a, final int b, final int c, final int p) {
            final long ux = x[b] - x[a];
            final long uy = y[b] - y[a];
            final long uz = z[b] - z[a];
            final long vx = x[c] - x[a];
            final long vy = y[c] - y[a];
            final long vz = z[c] - z[a];
            return (uy * vz - uz * vy) * (x[p] - x[a])
                + (uz * vx - ux * vz) * (y[p] - y[a])
                + (ux * vy - uy * vx) * (z[p] - z[a]);
        }

        int[] corners() {
            final Set<Integer> used = new TreeSet<>();
            for (final int[] f : faces) {
                used.add(f[0]);
                used.add(f[1]);
                used.add(f[2]);
            }
            return used.stream().mapToInt(Integer::intValue).toArray();
        }

        /**
         * One half-space per distinct face plane. Triangles that share a plane collapse into one, so
         * containment tests each flat side once.
         */
        long[] planes() {
            final Set<List<Long>> distinct = new LinkedHashSet<>();
            for (final int[] f : faces) {
                final int a = f[0];
                long nx = (long) (y[f[1]] - y[a]) * (z[f[2]] - z[a]) - (long) (z[f[1]] - z[a]) * (y[f[2]] - y[a]);
                long ny = (long) (z[f[1]] - z[a]) * (x[f[2]] - x[a]) - (long) (x[f[1]] - x[a]) * (z[f[2]] - z[a]);
                long nz = (long) (x[f[1]] - x[a]) * (y[f[2]] - y[a]) - (long) (y[f[1]] - y[a]) * (x[f[2]] - x[a]);
                final long g = gcd(gcd(Math.abs(nx), Math.abs(ny)), Math.abs(nz));
                nx /= g;
                ny /= g;
                nz /= g;
                distinct.add(List.of(nx, ny, nz, nx * x[a] + ny * y[a] + nz * z[a]));
            }
            final long[] out = new long[distinct.size() * 4];
            int i = 0;
            for (final List<Long> plane : distinct) {
                for (int k = 0; k < 4; k++) {
                    out[i++] = plane.get(k);
                }
            }
            return out;
        }

        private static long gcd(long a, long b) {
            while (b != 0) {
                final long t = a % b;
                a = b;
                b = t;
            }
            return a;
        }
    }
}
