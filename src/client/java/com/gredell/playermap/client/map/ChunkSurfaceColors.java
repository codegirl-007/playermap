package com.gredell.playermap.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkSurfaceColors {
	public static final byte FLAG_WATER = 1;

	private static final short HEIGHT_MISSING = Short.MIN_VALUE;

	private record CacheKey(ResourceKey<Level> dimension, long chunkPos) {
		static CacheKey of(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
			return new CacheKey(dimension, ChunkPos.asLong(chunkX, chunkZ));
		}
	}

	private static final Map<CacheKey, int[]> colorsByChunk = new ConcurrentHashMap<>();
	private static final Map<CacheKey, short[]> heightsByChunk = new ConcurrentHashMap<>();
	private static final Map<CacheKey, byte[]> flagsByChunk = new ConcurrentHashMap<>();
	private static final Map<CacheKey, ChunkSurfaceSnapshot> immutableSnapshotByChunk = new ConcurrentHashMap<>();
	private static final Set<ResourceKey<Level>> dimensionsWithData = ConcurrentHashMap.newKeySet();

	private ChunkSurfaceColors() {
	}

	public static void capture(LevelChunk chunk) {
		ResourceKey<Level> dimension = chunk.getLevel().dimension();
		int[] colors = new int[256];
		short[] heights = new short[256];
		byte[] flags = new byte[256];
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		int minY = chunk.getMinY();
		ChunkPos pos = chunk.getPos();

		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldX = pos.getMinBlockX() + localX;
				int worldZ = pos.getMinBlockZ() + localZ;
				int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ);
				mutable.set(worldX, y, worldZ);
				BlockState state = chunk.getBlockState(mutable);

				while (state.isAir() && y > minY) {
					y--;
					mutable.setY(y);
					state = chunk.getBlockState(mutable);
				}

				MapColor mapColor = state.getMapColor(chunk, mutable);
				int argb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
				int index = localX + localZ * 16;
				colors[index] = 0xFF000000 | (argb & 0x00FFFFFF);
				heights[index] = (short) y;
				if (!state.getFluidState().isEmpty() && state.getFluidState().is(Fluids.WATER)) {
					flags[index] = FLAG_WATER;
				}
			}
		}

		CacheKey key = new CacheKey(dimension, pos.toLong());
		colorsByChunk.put(key, colors);
		heightsByChunk.put(key, heights);
		flagsByChunk.put(key, flags);
		immutableSnapshotByChunk.put(
			key,
			new ChunkSurfaceSnapshot(pos.x, pos.z, colors, heights, flags)
		);
		dimensionsWithData.add(dimension);
	}

	public static boolean hasDataForDimension(ResourceKey<Level> dimension) {
		return dimensionsWithData.contains(dimension);
	}

	public static ChunkSurfaceSnapshot getImmutableSnapshot(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
		return immutableSnapshotByChunk.get(CacheKey.of(dimension, chunkX, chunkZ));
	}

	public static ChunkSurfaceSnapshot ensureImmutableSnapshot(
		ClientLevel level,
		ResourceKey<Level> dimension,
		int chunkX,
		int chunkZ
	) {
		ChunkSurfaceSnapshot existing = getImmutableSnapshot(dimension, chunkX, chunkZ);
		if (existing != null) {
			return existing;
		}
		if (level == null || !level.dimension().equals(dimension) || !level.hasChunk(chunkX, chunkZ)) {
			return null;
		}
		capture(level.getChunk(chunkX, chunkZ));
		return getImmutableSnapshot(dimension, chunkX, chunkZ);
	}

	public static int getColor(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		CacheKey key = new CacheKey(chunk.getLevel().dimension(), chunk.getPos().toLong());
		int[] colors = colorsByChunk.get(key);
		if (colors == null) {
			return 0;
		}
		return colors[index(worldX, worldZ)];
	}

	public static short getHeight(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		CacheKey key = new CacheKey(chunk.getLevel().dimension(), chunk.getPos().toLong());
		short[] heights = heightsByChunk.get(key);
		if (heights == null) {
			return HEIGHT_MISSING;
		}
		return heights[index(worldX, worldZ)];
	}

	public static boolean isWaterSurface(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		CacheKey key = new CacheKey(chunk.getLevel().dimension(), chunk.getPos().toLong());
		byte[] flags = flagsByChunk.get(key);
		if (flags == null) {
			return false;
		}
		return (flags[index(worldX, worldZ)] & FLAG_WATER) != 0;
	}

	public static int getCachedColor(ResourceKey<Level> dimension, int chunkX, int chunkZ, int worldX, int worldZ) {
		ChunkSurfaceSnapshot snapshot = getImmutableSnapshot(dimension, chunkX, chunkZ);
		if (snapshot == null) {
			return 0;
		}
		return snapshot.color(worldX, worldZ);
	}

	public static short getCachedHeight(ResourceKey<Level> dimension, int chunkX, int chunkZ, int worldX, int worldZ) {
		ChunkSurfaceSnapshot snapshot = getImmutableSnapshot(dimension, chunkX, chunkZ);
		if (snapshot == null) {
			return HEIGHT_MISSING;
		}
		return snapshot.height(worldX, worldZ);
	}

	public static boolean getCachedWater(ResourceKey<Level> dimension, int chunkX, int chunkZ, int worldX, int worldZ) {
		ChunkSurfaceSnapshot snapshot = getImmutableSnapshot(dimension, chunkX, chunkZ);
		if (snapshot == null) {
			return false;
		}
		return snapshot.water(worldX, worldZ);
	}

	public static void clear() {
		colorsByChunk.clear();
		heightsByChunk.clear();
		flagsByChunk.clear();
		immutableSnapshotByChunk.clear();
		dimensionsWithData.clear();
	}

	private static void ensureCaptured(LevelChunk chunk) {
		CacheKey key = new CacheKey(chunk.getLevel().dimension(), chunk.getPos().toLong());
		if (!colorsByChunk.containsKey(key)) {
			capture(chunk);
		}
	}

	private static int index(int worldX, int worldZ) {
		return (worldX & 15) + (worldZ & 15) * 16;
	}
}
