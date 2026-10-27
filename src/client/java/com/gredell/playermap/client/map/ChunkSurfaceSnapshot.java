package com.gredell.playermap.client.map;

/**
 * Immutable copy of one chunk's surface columns for off-thread rasterization.
 */
public final class ChunkSurfaceSnapshot {
	private final int chunkX;
	private final int chunkZ;
	private final int[] colors;
	private final short[] heights;
	private final byte[] flags;
	private final boolean exploredFrontier;

	ChunkSurfaceSnapshot(
		int chunkX,
		int chunkZ,
		int[] colors,
		short[] heights,
		byte[] flags,
		boolean exploredFrontier
	) {
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		this.colors = colors;
		this.heights = heights;
		this.flags = flags;
		this.exploredFrontier = exploredFrontier;
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

	public boolean exploredFrontier() {
		return exploredFrontier;
	}

	public int color(int worldX, int worldZ) {
		return colors[index(worldX, worldZ)];
	}

	public short height(int worldX, int worldZ) {
		return heights[index(worldX, worldZ)];
	}

	public boolean water(int worldX, int worldZ) {
		return (flags[index(worldX, worldZ)] & ChunkSurfaceColors.FLAG_WATER) != 0;
	}

	private static int index(int worldX, int worldZ) {
		return (worldX & 15) + (worldZ & 15) * 16;
	}
}
