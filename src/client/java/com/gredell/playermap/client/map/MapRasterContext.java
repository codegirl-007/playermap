package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable world snapshot for one raster job. Built on the render/main thread only.
 */
public final class MapRasterContext {
	private static final int MISSING_DATA_COLOR = 0xFF3A4558;

	private final long jobGeneration;
	private final ResourceKey<Level> dimension;
	private final boolean overworld;
	private final double blocksPerPixel;
	private final double minX;
	private final double minZ;
	private final int width;
	private final int height;
	private final Map<Long, ChunkSurfaceSnapshot> snapshotsByChunk;

	private MapRasterContext(
		final long jobGeneration,
		final ResourceKey<Level> dimension,
		final boolean overworld,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final int width,
		final int height,
		final Map<Long, ChunkSurfaceSnapshot> snapshotsByChunk
	) {
		this.jobGeneration = jobGeneration;
		this.dimension = dimension;
		this.overworld = overworld;
		this.blocksPerPixel = blocksPerPixel;
		this.minX = minX;
		this.minZ = minZ;
		this.width = width;
		this.height = height;
		this.snapshotsByChunk = snapshotsByChunk;
	}

	public static Prepare beginPrepare(
		final ResourceKey<Level> dimension,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final int width,
		final int height,
		final long jobGeneration
	) {
		return new Prepare(dimension, blocksPerPixel, minX, minZ, width, height, jobGeneration);
	}

	public static final class Prepare {
		private final ResourceKey<Level> dimension;
		private final boolean overworld;
		private final double blocksPerPixel;
		private final double minX;
		private final double minZ;
		private final int width;
		private final int height;
		private final long jobGeneration;
		private final List<Long> resolveQueue;
		private final Map<Long, ChunkSurfaceSnapshot> snapshotsByChunk = new HashMap<>();
		private int resolveIndex;

		private Prepare(
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double minX,
			final double minZ,
			final int width,
			final int height,
			final long jobGeneration
		) {
			this.dimension = dimension;
			this.overworld = dimension.equals(Level.OVERWORLD);
			this.blocksPerPixel = blocksPerPixel;
			this.minX = minX;
			this.minZ = minZ;
			this.width = width;
			this.height = height;
			this.jobGeneration = jobGeneration;

			final double maxX = minX + width * blocksPerPixel;
			final double maxZ = minZ + height * blocksPerPixel;
			final int minChunkX = floorDiv(minX, 16) - 1;
			final int maxChunkX = floorDiv(maxX, 16) + 1;
			final int minChunkZ = floorDiv(minZ, 16) - 1;
			final int maxChunkZ = floorDiv(maxZ, 16) + 1;

			this.resolveQueue = new ArrayList<>();
			for (int cx = minChunkX; cx <= maxChunkX; cx++) {
				for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
					resolveQueue.add(ChunkPos.asLong(cx, cz));
				}
			}
		}

		public long jobGeneration() {
			return jobGeneration;
		}

		public boolean isComplete() {
			return resolveIndex >= resolveQueue.size();
		}

		public int remainingChunks() {
			return Math.max(0, resolveQueue.size() - resolveIndex);
		}

		public void advanceWithinBudget(final ClientLevel level, final long deadlineNanos) {
			final boolean liveDimension = level != null && level.dimension().equals(dimension);
			while (resolveIndex < resolveQueue.size() && MapFrameBudget.hasTimeRemaining(deadlineNanos)) {
				final long key = resolveQueue.get(resolveIndex++);
				final int cx = ChunkPos.getX(key);
				final int cz = ChunkPos.getZ(key);
				final long start = System.nanoTime();
				ChunkSurfaceSnapshot snapshot = ChunkSurfaceColors.getImmutableSnapshot(dimension, cx, cz);
				boolean captured = false;
				if (snapshot == null && liveDimension) {
					snapshot = ChunkSurfaceColors.ensureImmutableSnapshot(level, dimension, cx, cz);
					captured = snapshot != null;
				}
				MapFrameBudget.recordChunkResolve(captured, System.nanoTime() - start);
				if (snapshot != null) {
					snapshotsByChunk.put(key, snapshot);
				}
			}
		}

		public MapRasterContext build() {
			return new MapRasterContext(
				jobGeneration,
				dimension,
				overworld,
				blocksPerPixel,
				minX,
				minZ,
				width,
				height,
				Map.copyOf(snapshotsByChunk)
			);
		}
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

	int missingDataColor() {
		return MISSING_DATA_COLOR;
	}

	ChunkSurfaceSnapshot snapshot(final int chunkX, final int chunkZ) {
		return snapshotsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
	}

	private static int floorDiv(final double value, final int divisor) {
		return (int) Math.floor(value / divisor);
	}
}
