package com.gredell.playermap.client.map;

public final class MapViewMath {
	private MapViewMath() {
	}

	public static int worldToScreenX(double worldX, double viewCenterX, double blocksPerPixel, int mapLeft, int mapWidth) {
		return (int) Math.round(mapLeft + mapWidth / 2.0 + (worldX - viewCenterX) / blocksPerPixel);
	}

	public static int worldToScreenZ(double worldZ, double viewCenterZ, double blocksPerPixel, int mapTop, int mapHeight) {
		return (int) Math.round(mapTop + mapHeight / 2.0 + (worldZ - viewCenterZ) / blocksPerPixel);
	}

	public static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
