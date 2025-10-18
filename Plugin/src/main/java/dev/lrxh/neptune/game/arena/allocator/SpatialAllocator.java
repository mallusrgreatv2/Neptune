package dev.lrxh.neptune.game.arena.allocator;

import dev.lrxh.neptune.configs.impl.SettingsLocale;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SpatialAllocator {

    private static final int DEFAULT_BASE_X = SettingsLocale.ARENA_COPY_OFFSET_X.getInt();
    private static final int DEFAULT_BASE_Z = SettingsLocale.ARENA_COPY_OFFSET_Z.getInt();

    private static final SpatialAllocator INSTANCE = new SpatialAllocator(DEFAULT_BASE_X, DEFAULT_BASE_Z);

    private final ConcurrentHashMap<Long, Long> occupancy = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Allocation> allocations = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    private final int baseChunkX;
    private final int baseChunkZ;

    private int nextX = 0;
    private int nextZ = 0;

    private SpatialAllocator(int baseChunkX, int baseChunkZ) {
        this.baseChunkX = baseChunkX;
        this.baseChunkZ = baseChunkZ;
    }

    private static long keyFor(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    public static SpatialAllocator get() {
        return INSTANCE;
    }

    /**
     * Allocate a rectangle on the chunk-grid sized widthChunks x depthChunks.
     * Always allocates relative to the arena offset in row-major order.
     *
     * @param widthChunks  width in chunks (>=1)
     * @param depthChunks  depth in chunks (>=1)
     * @param gutterChunks minimum gutter (empty chunks) between allocations (>=0)
     * @param maxRadius    ignored in this deterministic allocator
     * @return an Allocation (non-null)
     */
    public synchronized Allocation allocate(int widthChunks, int depthChunks, int gutterChunks, int maxRadius) {
        if (widthChunks <= 0 || depthChunks <= 0) {
            throw new IllegalArgumentException("widthChunks/depthChunks must be > 0");
        }

        int strideX = widthChunks + Math.max(0, gutterChunks);
        int strideZ = depthChunks + Math.max(0, gutterChunks);

        int allocX = baseChunkX + nextX;
        int allocZ = baseChunkZ + nextZ;

        while (!regionFree(allocX, allocZ, widthChunks, depthChunks)) {
            nextX += strideX;
            if (nextX > 10000) {
                nextX = 0;
                nextZ += strideZ;
            }
            allocX = baseChunkX + nextX;
            allocZ = baseChunkZ + nextZ;
        }

        Allocation alloc = reserveAt(allocX, allocZ, widthChunks, depthChunks);
        nextX += strideX;

        return alloc;
    }

    public Allocation allocate(int widthChunks, int depthChunks) {
        return allocate(widthChunks, depthChunks, 1, 200);
    }

    private boolean regionFree(int startChunkX, int startChunkZ, int widthChunks, int depthChunks) {
        for (int x = startChunkX; x < startChunkX + widthChunks; x++) {
            for (int z = startChunkZ; z < startChunkZ + depthChunks; z++) {
                if (occupancy.containsKey(keyFor(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private Allocation reserveAt(int startChunkX, int startChunkZ, int widthChunks, int depthChunks) {
        long id = idCounter.getAndIncrement();
        Allocation alloc = new Allocation(id, startChunkX, startChunkZ, widthChunks, depthChunks);

        try {
            allocations.put(id, alloc);

            // ✅ Reserve the region first
            for (int x = startChunkX; x < startChunkX + widthChunks; x++) {
                for (int z = startChunkZ; z < startChunkZ + depthChunks; z++) {
                    occupancy.put(keyFor(x, z), id);
                }
            }

            // ✅ Now verify that all chunks were successfully marked
            int mismatches = 0;
            for (int x = startChunkX; x < startChunkX + widthChunks; x++) {
                for (int z = startChunkZ; z < startChunkZ + depthChunks; z++) {
                    Long val = occupancy.get(keyFor(x, z));
                    if (val == null || val != id) {
                        mismatches++;
                    }
                }
            }

            if (mismatches > 0) {
                // Rollback if verification fails
                for (int x = startChunkX; x < startChunkX + widthChunks; x++) {
                    for (int z = startChunkZ; z < startChunkZ + depthChunks; z++) {
                        occupancy.remove(keyFor(x, z), id);
                    }
                }
                allocations.remove(id);
                throw new IllegalStateException("SpatialAllocator: failed to reserve full region for id=" + id);
            }

            return alloc;
        } catch (RuntimeException ex) {
            // Rollback if any unexpected exception occurs
            for (int x = startChunkX; x < startChunkX + widthChunks; x++) {
                for (int z = startChunkZ; z < startChunkZ + depthChunks; z++) {
                    occupancy.remove(keyFor(x, z), id);
                }
            }
            allocations.remove(id);
            throw ex;
        }
    }


    public synchronized void free(long allocationId) {
        Allocation alloc = allocations.remove(allocationId);
        if (alloc == null)
            return;
        for (int x = alloc.chunkX; x < alloc.chunkX + alloc.widthChunks; x++) {
            for (int z = alloc.chunkZ; z < alloc.chunkZ + alloc.depthChunks; z++) {
                occupancy.remove(keyFor(x, z), allocationId);
            }
        }
    }
}
