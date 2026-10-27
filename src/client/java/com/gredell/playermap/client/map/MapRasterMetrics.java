package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;

import java.util.concurrent.atomic.AtomicLong;

public final class MapRasterMetrics {
	private static final AtomicLong LAST_TOTAL_NANOS = new AtomicLong();
	private static final AtomicLong LAST_SAMPLE_NANOS = new AtomicLong();
	private static final AtomicLong LAST_PIXELS = new AtomicLong();
	private static final AtomicLong BASELINE_TOTAL_NANOS = new AtomicLong();

	private MapRasterMetrics() {
	}

	public static void record(long pixels, long totalNanos, long sampleNanos) {
		LAST_PIXELS.set(pixels);
		LAST_TOTAL_NANOS.set(totalNanos);
		LAST_SAMPLE_NANOS.set(sampleNanos);
		if (BASELINE_TOTAL_NANOS.get() == 0 && totalNanos > 0) {
			BASELINE_TOTAL_NANOS.set(totalNanos);
		}
		PlayersMap.LOGGER.debug(
			"Map raster: {} px in {} ms (sampling {} ms, {}% of total)",
			pixels,
			totalNanos / 1_000_000L,
			sampleNanos / 1_000_000L,
			pixels == 0 ? 0 : (sampleNanos * 100 / totalNanos)
		);
	}

	public static long lastTotalMillis() {
		return LAST_TOTAL_NANOS.get() / 1_000_000L;
	}

	public static long lastSampleMillis() {
		return LAST_SAMPLE_NANOS.get() / 1_000_000L;
	}

	public static long lastPixels() {
		return LAST_PIXELS.get();
	}

	public static void resetBaseline() {
		BASELINE_TOTAL_NANOS.set(0);
	}
}
