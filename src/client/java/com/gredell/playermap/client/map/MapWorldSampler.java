package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public final class MapWorldSampler {
	private static final int UNEXPLORED_COLOR = 0xFF1A1D28;
	private static final int UNLOADED_COLOR = 0xFF252A38;

	private MapWorldSampler() {
	}

	public static int sampleColor(ClientLevel level, ResourceKey<Level> dimension, int blockX, int blockZ) {
		int cx = blockX >> 4;
		int cz = blockZ >> 4;

		if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
			return UNEXPLORED_COLOR;
		}

		if (level != null && level.dimension().equals(dimension) && level.hasChunk(cx, cz)) {
			LevelChunk chunk = level.getChunk(cx, cz);
			return ChunkSurfaceColors.getColor(chunk, blockX, blockZ);
		}

		int cached = ChunkSurfaceColors.getCachedColor(cx, cz, blockX, blockZ);
		return cached == 0 ? UNLOADED_COLOR : cached;
	}
}
