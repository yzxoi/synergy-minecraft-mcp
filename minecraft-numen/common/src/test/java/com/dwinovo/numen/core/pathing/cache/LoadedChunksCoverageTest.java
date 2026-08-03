package com.dwinovo.numen.core.pathing.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression pins for respawn snapshot coverage checks. */
class LoadedChunksCoverageTest {

    @BeforeAll
    static void boot() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void coverageUsesChunkCoordinatesIncludingNegativePositions() {
        Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
        chunks.put(ChunkPos.asLong(1, -3), null);
        LoadedChunks snapshot = new LoadedChunks(chunks, new LongOpenHashSet());

        assertTrue(snapshot.covers(new BlockPos(16, 70, -33)));
        assertTrue(snapshot.covers(new BlockPos(31, 0, -48)));
        assertFalse(snapshot.covers(new BlockPos(32, 70, -33)));
        assertFalse(snapshot.covers(null));
    }
}
