package com.gredell.playermap.client.map;

public final class MapTerrainShading {
	private static final float SLOPE_FACTOR = 0.04F;
	private static final float MAX_SLOPE_DARK = 0.30F;
	private static final float HIGHLAND_START = 80.0F;
	private static final float HIGHLAND_BRIGHTNESS = 0.03F;

	private MapTerrainShading() {
	}

	public static int apply(
		final int argb,
		final short surfaceY,
		final short northY,
		final short southY,
		final short eastY,
		final short westY,
		final boolean overworld
	) {
		final int steepness = maxSlope(surfaceY, northY, southY, eastY, westY);
		float shade = 1.0F - Math.min(MAX_SLOPE_DARK, steepness * SLOPE_FACTOR);

		if (overworld && surfaceY > HIGHLAND_START) {
			shade += Math.min(HIGHLAND_BRIGHTNESS, (surfaceY - HIGHLAND_START) * 0.0008F);
		}

		final int a = (argb >> 24) & 0xFF;
		final int r = clampChannel(((argb >> 16) & 0xFF) * shade);
		final int g = clampChannel(((argb >> 8) & 0xFF) * shade);
		final int b = clampChannel((argb & 0xFF) * shade);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int maxSlope(
		final short center,
		final short north,
		final short south,
		final short east,
		final short west
	) {
		int max = 0;
		max = Math.max(max, Math.abs(center - coalesce(north, center)));
		max = Math.max(max, Math.abs(center - coalesce(south, center)));
		max = Math.max(max, Math.abs(center - coalesce(east, center)));
		max = Math.max(max, Math.abs(center - coalesce(west, center)));
		return max;
	}

	private static short coalesce(final short value, final short fallback) {
		return value == Short.MIN_VALUE ? fallback : value;
	}

	private static int clampChannel(final float value) {
		return Math.max(0, Math.min(255, Math.round(value)));
	}
}
