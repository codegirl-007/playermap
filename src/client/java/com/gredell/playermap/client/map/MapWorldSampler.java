package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public final class MapWorldSampler {
	private static final int UNEXPLORED_COLOR = 0xFF12151E;
	private static final int UNLOADED_COLOR = 0xFF3A4558;

	private MapWorldSampler() {
	}

	public static RasterPass beginRaster(ClientLevel level, ResourceKey<Level> dimension) {
		return new RasterPass(level, dimension);
	}

	public static final class RasterPass {
		private final ClientLevel level;
		private final ResourceKey<Level> dimension;
		private final boolean liveDimension;
		private final Map<Integer, Integer> dominantCounts = new HashMap<>();
		private long frontierChunkKey = Long.MIN_VALUE;
		private boolean frontierCached;

		private RasterPass(ClientLevel level, ResourceKey<Level> dimension) {
			this.level = level;
			this.dimension = dimension;
			this.liveDimension = level != null && level.dimension().equals(dimension);
		}

		public int sampleTexel(
			double minX,
			double minZ,
			double blocksPerPixel,
			int pixelX,
			int pixelZ
		) {
			if (blocksPerPixel <= 1.0) {
				int blockX = (int) Math.floor(minX + (pixelX + 0.5) * blocksPerPixel);
				int blockZ = (int) Math.floor(minZ + (pixelZ + 0.5) * blocksPerPixel);
				return sampleBlock(blockX, blockZ);
			}
			return sampleDominantFootprint(minX, minZ, blocksPerPixel, pixelX, pixelZ);
		}

		public int sampleBlock(int blockX, int blockZ) {
			int cx = blockX >> 4;
			int cz = blockZ >> 4;

			if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
				return UNEXPLORED_COLOR;
			}

			int base = resolveBaseColor(blockX, blockZ, cx, cz);
			if (base == UNLOADED_COLOR || base == UNEXPLORED_COLOR) {
				return base;
			}

			ColumnSample center = resolveColumn(blockX, blockZ, cx, cz);
			if (center == null) {
				return MapColorUtil.adjustForReadability(base);
			}

			return finishTerrain(
				base,
				center.water,
				cx,
				cz,
				center.height,
				heightAt(blockX, blockZ - 1),
				heightAt(blockX, blockZ + 1),
				heightAt(blockX + 1, blockZ),
				heightAt(blockX - 1, blockZ)
			);
		}

		private int sampleDominantFootprint(
			double minX,
			double minZ,
			double blocksPerPixel,
			int pixelX,
			int pixelZ
		) {
			double startX = minX + pixelX * blocksPerPixel;
			double startZ = minZ + pixelZ * blocksPerPixel;
			int x0 = (int) Math.floor(startX);
			int z0 = (int) Math.floor(startZ);
			int x1 = (int) Math.floor(startX + blocksPerPixel - 1e-6);
			int z1 = (int) Math.floor(startZ + blocksPerPixel - 1e-6);

			int stepX = Math.max(1, (x1 - x0 + 1) / 7);
			int stepZ = Math.max(1, (z1 - z0 + 1) / 7);

			dominantCounts.clear();
			int bestColor = UNLOADED_COLOR;
			int bestCount = 0;

			for (int bx = x0; bx <= x1; bx += stepX) {
				for (int bz = z0; bz <= z1; bz += stepZ) {
					int color = sampleBlock(bx, bz);
					int count = dominantCounts.merge(color, 1, Integer::sum);
					if (count > bestCount) {
						bestCount = count;
						bestColor = color;
					}
				}
			}

			if (bestCount == 0) {
				return sampleBlock((x0 + x1) / 2, (z0 + z1) / 2);
			}
			return bestColor;
		}

		private int resolveBaseColor(int blockX, int blockZ, int cx, int cz) {
			int cached = ChunkSurfaceColors.getCachedColor(cx, cz, blockX, blockZ);
			if (cached != 0) {
				return cached;
			}
			if (liveDimension && level.hasChunk(cx, cz)) {
				return ChunkSurfaceColors.getColor(level.getChunk(cx, cz), blockX, blockZ);
			}
			return UNLOADED_COLOR;
		}

		private ColumnSample resolveColumn(int blockX, int blockZ, int cx, int cz) {
			int cachedColor = ChunkSurfaceColors.getCachedColor(cx, cz, blockX, blockZ);
			if (cachedColor != 0) {
				short height = ChunkSurfaceColors.getCachedHeight(cx, cz, blockX, blockZ);
				if (height == Short.MIN_VALUE) {
					return null;
				}
				return new ColumnSample(height, ChunkSurfaceColors.getCachedWater(cx, cz, blockX, blockZ));
			}
			if (liveDimension && level.hasChunk(cx, cz)) {
				LevelChunk chunk = level.getChunk(cx, cz);
				return new ColumnSample(
					ChunkSurfaceColors.getHeight(chunk, blockX, blockZ),
					ChunkSurfaceColors.isWaterSurface(chunk, blockX, blockZ)
				);
			}
			return null;
		}

		private short heightAt(int blockX, int blockZ) {
			int cx = blockX >> 4;
			int cz = blockZ >> 4;
			if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
				return Short.MIN_VALUE;
			}
			short cached = ChunkSurfaceColors.getCachedHeight(cx, cz, blockX, blockZ);
			if (cached != Short.MIN_VALUE) {
				return cached;
			}
			if (liveDimension && level.hasChunk(cx, cz)) {
				return ChunkSurfaceColors.getHeight(level.getChunk(cx, cz), blockX, blockZ);
			}
			return Short.MIN_VALUE;
		}

		private int finishTerrain(
			int base,
			boolean water,
			int cx,
			int cz,
			short surfaceY,
			short northY,
			short southY,
			short eastY,
			short westY
		) {
			boolean overworld = dimension.equals(Level.OVERWORLD);
			int shaded = MapTerrainShading.apply(base, surfaceY, northY, southY, eastY, westY, overworld);
			int readable = MapColorUtil.adjustForReadability(shaded);
			int tinted = MapColorUtil.tintWater(readable, water);
			if (isExploredFrontier(cx, cz)) {
				return MapColorUtil.darken(tinted, 0.90F);
			}
			return tinted;
		}

		private boolean isExploredFrontier(int cx, int cz) {
			long key = ChunkPos.asLong(cx, cz);
			if (key != frontierChunkKey) {
				frontierChunkKey = key;
				frontierCached = !ExploredMapStorage.isExplored(dimension, cx - 1, cz)
					|| !ExploredMapStorage.isExplored(dimension, cx + 1, cz)
					|| !ExploredMapStorage.isExplored(dimension, cx, cz - 1)
					|| !ExploredMapStorage.isExplored(dimension, cx, cz + 1);
			}
			return frontierCached;
		}

		private record ColumnSample(short height, boolean water) {
		}
	}
}
