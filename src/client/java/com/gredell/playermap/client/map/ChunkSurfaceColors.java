package com.gredell.playermap.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkSurfaceColors {
	public static final byte FLAG_WATER = 1;

	private static final short HEIGHT_MISSING = Short.MIN_VALUE;

	private static final Map<Long, int[]> colorsByChunk = new ConcurrentHashMap<>();
	private static final Map<Long, short[]> heightsByChunk = new ConcurrentHashMap<>();
	private static final Map<Long, byte[]> flagsByChunk = new ConcurrentHashMap<>();

	private ChunkSurfaceColors() {
	}

	public static void capture(LevelChunk chunk) {
		int[] colors = new int[256];
		short[] heights = new short[256];
		byte[] flags = new byte[256];
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		int minY = chunk.getMinY();

		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldX = chunk.getPos().getMinBlockX() + localX;
				int worldZ = chunk.getPos().getMinBlockZ() + localZ;
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

		long key = chunk.getPos().toLong();
		colorsByChunk.put(key, colors);
		heightsByChunk.put(key, heights);
		flagsByChunk.put(key, flags);
	}

	public static int getColor(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		int[] colors = colorsByChunk.get(chunk.getPos().toLong());
		if (colors == null) {
			return 0;
		}
		return colors[index(worldX, worldZ)];
	}

	public static short getHeight(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		short[] heights = heightsByChunk.get(chunk.getPos().toLong());
		if (heights == null) {
			return HEIGHT_MISSING;
		}
		return heights[index(worldX, worldZ)];
	}

	public static boolean isWaterSurface(LevelChunk chunk, int worldX, int worldZ) {
		ensureCaptured(chunk);
		byte[] flags = flagsByChunk.get(chunk.getPos().toLong());
		if (flags == null) {
			return false;
		}
		return (flags[index(worldX, worldZ)] & FLAG_WATER) != 0;
	}

	public static int getCachedColor(int chunkX, int chunkZ, int worldX, int worldZ) {
		int[] colors = colorsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
		if (colors == null) {
			return 0;
		}
		return colors[index(worldX, worldZ)];
	}

	public static short getCachedHeight(int chunkX, int chunkZ, int worldX, int worldZ) {
		short[] heights = heightsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
		if (heights == null) {
			return HEIGHT_MISSING;
		}
		return heights[index(worldX, worldZ)];
	}

	public static boolean getCachedWater(int chunkX, int chunkZ, int worldX, int worldZ) {
		byte[] flags = flagsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
		if (flags == null) {
			return false;
		}
		return (flags[index(worldX, worldZ)] & FLAG_WATER) != 0;
	}

	public static void clear() {
		colorsByChunk.clear();
		heightsByChunk.clear();
		flagsByChunk.clear();
	}

	private static void ensureCaptured(LevelChunk chunk) {
		if (!colorsByChunk.containsKey(chunk.getPos().toLong())) {
			capture(chunk);
		}
	}

	private static int index(int worldX, int worldZ) {
		return (worldX & 15) + (worldZ & 15) * 16;
	}
}
