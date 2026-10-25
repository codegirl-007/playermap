package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public final class MapWorldSampler {
	private static final int UNEXPLORED_COLOR = 0xFF12151E;
	private static final int UNLOADED_COLOR = 0xFF3A4558;

	private static final Map<Integer, Integer> DOMINANT_COUNTS = new HashMap<>();

	private MapWorldSampler() {
	}

	public static int sampleTexel(
		ClientLevel level,
		ResourceKey<Level> dimension,
		double minX,
		double minZ,
		double blocksPerPixel,
		int pixelX,
		int pixelZ
	) {
		if (blocksPerPixel <= 1.0) {
			int blockX = (int) Math.floor(minX + (pixelX + 0.5) * blocksPerPixel);
			int blockZ = (int) Math.floor(minZ + (pixelZ + 0.5) * blocksPerPixel);
			return sampleBlock(level, dimension, blockX, blockZ);
		}
		return sampleDominantFootprint(level, dimension, minX, minZ, blocksPerPixel, pixelX, pixelZ);
	}

	public static int sampleBlock(ClientLevel level, ResourceKey<Level> dimension, int blockX, int blockZ) {
		int cx = blockX >> 4;
		int cz = blockZ >> 4;

		if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
			return UNEXPLORED_COLOR;
		}

		int base = resolveBaseColor(level, dimension, blockX, blockZ, cx, cz);
		if (base == UNLOADED_COLOR || base == UNEXPLORED_COLOR) {
			return base;
		}

		SurfaceSample surface = resolveSurface(level, dimension, blockX, blockZ, cx, cz);
		if (surface == null) {
			return MapColorUtil.adjustForReadability(base);
		}

		short north = heightAt(level, dimension, blockX, blockZ - 1);
		short south = heightAt(level, dimension, blockX, blockZ + 1);
		short east = heightAt(level, dimension, blockX + 1, blockZ);
		short west = heightAt(level, dimension, blockX - 1, blockZ);

		return finishTerrain(
			base,
			surface.water,
			dimension,
			blockX,
			blockZ,
			cx,
			cz,
			surface.height,
			north,
			south,
			east,
			west
		);
	}

	private static int sampleDominantFootprint(
		ClientLevel level,
		ResourceKey<Level> dimension,
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

		DOMINANT_COUNTS.clear();
		int bestColor = UNLOADED_COLOR;
		int bestCount = 0;

		for (int bx = x0; bx <= x1; bx += stepX) {
			for (int bz = z0; bz <= z1; bz += stepZ) {
				int color = sampleBlock(level, dimension, bx, bz);
				int count = DOMINANT_COUNTS.merge(color, 1, Integer::sum);
				if (count > bestCount) {
					bestCount = count;
					bestColor = color;
				}
			}
		}

		if (bestCount == 0) {
			return sampleBlock(level, dimension, (x0 + x1) / 2, (z0 + z1) / 2);
		}
		return bestColor;
	}

	private static int resolveBaseColor(ClientLevel level, ResourceKey<Level> dimension, int blockX, int blockZ, int cx, int cz) {
		if (level != null && level.dimension().equals(dimension) && level.hasChunk(cx, cz)) {
			LevelChunk chunk = level.getChunk(cx, cz);
			return ChunkSurfaceColors.getColor(chunk, blockX, blockZ);
		}

		int cached = ChunkSurfaceColors.getCachedColor(cx, cz, blockX, blockZ);
		return cached == 0 ? UNLOADED_COLOR : cached;
	}

	private static SurfaceSample resolveSurface(
		ClientLevel level,
		ResourceKey<Level> dimension,
		int blockX,
		int blockZ,
		int cx,
		int cz
	) {
		if (level != null && level.dimension().equals(dimension) && level.hasChunk(cx, cz)) {
			LevelChunk chunk = level.getChunk(cx, cz);
			return new SurfaceSample(
				ChunkSurfaceColors.getHeight(chunk, blockX, blockZ),
				ChunkSurfaceColors.isWaterSurface(chunk, blockX, blockZ)
			);
		}

		short height = ChunkSurfaceColors.getCachedHeight(cx, cz, blockX, blockZ);
		if (height == Short.MIN_VALUE) {
			return null;
		}
		return new SurfaceSample(height, ChunkSurfaceColors.getCachedWater(cx, cz, blockX, blockZ));
	}

	private static short heightAt(ClientLevel level, ResourceKey<Level> dimension, int blockX, int blockZ) {
		int cx = blockX >> 4;
		int cz = blockZ >> 4;
		if (!ExploredMapStorage.isExplored(dimension, cx, cz)) {
			return Short.MIN_VALUE;
		}
		if (level != null && level.dimension().equals(dimension) && level.hasChunk(cx, cz)) {
			return ChunkSurfaceColors.getHeight(level.getChunk(cx, cz), blockX, blockZ);
		}
		return ChunkSurfaceColors.getCachedHeight(cx, cz, blockX, blockZ);
	}

	private static int finishTerrain(
		int base,
		boolean water,
		ResourceKey<Level> dimension,
		int blockX,
		int blockZ,
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
		if (isExploredFrontier(dimension, cx, cz)) {
			return MapColorUtil.darken(tinted, 0.90F);
		}
		return tinted;
	}

	private static boolean isExploredFrontier(ResourceKey<Level> dimension, int cx, int cz) {
		return !ExploredMapStorage.isExplored(dimension, cx - 1, cz)
			|| !ExploredMapStorage.isExplored(dimension, cx + 1, cz)
			|| !ExploredMapStorage.isExplored(dimension, cx, cz - 1)
			|| !ExploredMapStorage.isExplored(dimension, cx, cz + 1);
	}

	private record SurfaceSample(short height, boolean water) {
	}
}
