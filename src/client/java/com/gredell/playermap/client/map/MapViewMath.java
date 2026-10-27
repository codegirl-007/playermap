package com.gredell.playermap.client.map;

public final class MapViewMath {
	private MapViewMath() {
	}

	public static int worldToScreenX(
		final double worldX,
		final double viewCenterX,
		final double blocksPerPixel,
		final int mapLeft,
		final int mapWidth
	) {
		return (int) Math.round(mapLeft + mapWidth / 2.0 + (worldX - viewCenterX) / blocksPerPixel);
	}

	public static int worldToScreenZ(
		final double worldZ,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapTop,
		final int mapHeight
	) {
		return (int) Math.round(mapTop + mapHeight / 2.0 + (worldZ - viewCenterZ) / blocksPerPixel);
	}

	public static double clamp(final double value, final double min, final double max) {
		return Math.max(min, Math.min(max, value));
	}
}
