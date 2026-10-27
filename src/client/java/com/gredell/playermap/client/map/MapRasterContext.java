package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable world snapshot for one raster job. Built on the render/main thread only.
 */
public final class MapRasterContext {
	private static final int UNEXPLORED_COLOR = 0xFF12151E;
	private static final int UNLOADED_COLOR = 0xFF3A4558;

	private final long jobGeneration;
	private final ResourceKey<Level> dimension;
	private final boolean overworld;
	private final double blocksPerPixel;
	private final double minX;
	private final double minZ;
	private final int width;
	private final int height;
	private final Set<Long> exploredChunks;
	private final Map<Long, ChunkSurfaceSnapshot> snapshotsByChunk;

	private MapRasterContext(
		long jobGeneration,
		ResourceKey<Level> dimension,
		boolean overworld,
		double blocksPerPixel,
		double minX,
		double minZ,
		int width,
		int height,
		Set<Long> exploredChunks,
		Map<Long, ChunkSurfaceSnapshot> snapshotsByChunk
	) {
		this.jobGeneration = jobGeneration;
		this.dimension = dimension;
		this.overworld = overworld;
		this.blocksPerPixel = blocksPerPixel;
		this.minX = minX;
		this.minZ = minZ;
		this.width = width;
		this.height = height;
		this.exploredChunks = exploredChunks;
		this.snapshotsByChunk = snapshotsByChunk;
	}

	public static MapRasterContext capture(
		ClientLevel level,
		ResourceKey<Level> dimension,
		double blocksPerPixel,
		double minX,
		double minZ,
		int width,
		int height,
		long jobGeneration
	) {
		double maxX = minX + width * blocksPerPixel;
		double maxZ = minZ + height * blocksPerPixel;
		int minChunkX = floorDiv(minX, 16) - 1;
		int maxChunkX = floorDiv(maxX, 16) + 1;
		int minChunkZ = floorDiv(minZ, 16) - 1;
		int maxChunkZ = floorDiv(maxZ, 16) + 1;

		boolean liveDimension = level != null && level.dimension().equals(dimension);
		Set<Long> explored = new HashSet<>();
		Map<Long, ChunkSurfaceSnapshot> snapshots = new HashMap<>();

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
					continue;
				}
				long key = ChunkPos.asLong(cx, cz);
				explored.add(key);
				ChunkSurfaceSnapshot snapshot = ChunkSurfaceColors.copySnapshot(dimension, cx, cz);
				if (snapshot == null && liveDimension && level.hasChunk(cx, cz)) {
					LevelChunk chunk = level.getChunk(cx, cz);
					snapshot = ChunkSurfaceColors.snapshotFromChunk(chunk, dimension);
				}
				if (snapshot != null) {
					snapshots.put(key, snapshot);
				}
			}
		}

		return new MapRasterContext(
			jobGeneration,
			dimension,
			dimension.equals(Level.OVERWORLD),
			blocksPerPixel,
			minX,
			minZ,
			width,
			height,
			Set.copyOf(explored),
			Map.copyOf(snapshots)
		);
	}

	long jobGeneration() {
		return jobGeneration;
	}

	ResourceKey<Level> dimension() {
		return dimension;
	}

	boolean overworld() {
		return overworld;
	}

	double blocksPerPixel() {
		return blocksPerPixel;
	}

	double minX() {
		return minX;
	}

	double minZ() {
		return minZ;
	}

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	int unexploredColor() {
		return UNEXPLORED_COLOR;
	}

	int unloadedColor() {
		return UNLOADED_COLOR;
	}

	boolean isExplored(int chunkX, int chunkZ) {
		return exploredChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
	}

	ChunkSurfaceSnapshot snapshot(int chunkX, int chunkZ) {
		return snapshotsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
	}

	private static int floorDiv(double value, int divisor) {
		return (int) Math.floor(value / divisor);
	}
}
