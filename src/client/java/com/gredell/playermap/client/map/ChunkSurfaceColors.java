package com.gredell.playermap.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkSurfaceColors {
	private static final Map<Long, int[]> colorsByChunk = new ConcurrentHashMap<>();

	private ChunkSurfaceColors() {
	}

	public static void capture(LevelChunk chunk) {
		int[] colors = new int[256];
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
				colors[localX + localZ * 16] = 0xFF000000 | (argb & 0x00FFFFFF);
			}
		}

		colorsByChunk.put(chunk.getPos().toLong(), colors);
	}

	public static int getColor(LevelChunk chunk, int worldX, int worldZ) {
		int[] colors = colorsByChunk.get(chunk.getPos().toLong());
		if (colors == null) {
			capture(chunk);
			colors = colorsByChunk.get(chunk.getPos().toLong());
		}
		if (colors == null) {
			return 0xFF000000;
		}

		int localX = worldX & 15;
		int localZ = worldZ & 15;
		return colors[localX + localZ * 16];
	}

	public static int getCachedColor(int chunkX, int chunkZ, int worldX, int worldZ) {
		int[] colors = colorsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
		if (colors == null) {
			return 0;
		}

		int localX = worldX & 15;
		int localZ = worldZ & 15;
		return colors[localX + localZ * 16];
	}

	public static void clear() {
		colorsByChunk.clear();
	}
}
