package com.gredell.playermap.client.map;

/**
 * Immutable surface columns for one chunk. Arrays are owned by this snapshot and must not be mutated
 * after publish (new captures replace the cache entry with a new snapshot instance).
 */
public final class ChunkSurfaceSnapshot {
	private final int chunkX;
	private final int chunkZ;
	private final int[] colors;
	private final short[] heights;
	private final byte[] flags;

	ChunkSurfaceSnapshot(
		final int chunkX,
		final int chunkZ,
		final int[] colors,
		final short[] heights,
		final byte[] flags
	) {
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		this.colors = colors;
		this.heights = heights;
		this.flags = flags;
	}

	public int chunkX() {
		return chunkX;
	}

	public int chunkZ() {
		return chunkZ;
	}

	public long chunkKey() {
		return net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
	}

	public int color(final int worldX, final int worldZ) {
		return colors[index(worldX, worldZ)];
	}

	public short height(final int worldX, final int worldZ) {
		return heights[index(worldX, worldZ)];
	}

	public boolean water(final int worldX, final int worldZ) {
		return (flags[index(worldX, worldZ)] & ChunkSurfaceColors.FLAG_WATER) != 0;
	}

	private static int index(final int worldX, final int worldZ) {
		return (worldX & 15) + (worldZ & 15) * 16;
	}
}
