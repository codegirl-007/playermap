package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;

import java.util.concurrent.atomic.AtomicLong;

public final class MapRasterMetrics {
	private static final AtomicLong LAST_TOTAL_NANOS = new AtomicLong();
	private static final AtomicLong LAST_SAMPLE_NANOS = new AtomicLong();
	private static final AtomicLong LAST_PIXELS = new AtomicLong();
	private static final AtomicLong LAST_COPY_NANOS = new AtomicLong();
	private static final AtomicLong LAST_UPLOAD_NANOS = new AtomicLong();
	private static final AtomicLong BASELINE_TOTAL_NANOS = new AtomicLong();

	private static volatile double emaCopyNanosPerTexel;
	private static volatile double emaUploadNanosPerTexel;
	private static final double DEFAULT_COPY_NANOS_PER_TEXEL = 12.0;
	private static final double DEFAULT_UPLOAD_NANOS_PER_TEXEL = 18.0;

	private MapRasterMetrics() {
	}

	public static void record(long pixels, long totalNanos, long sampleNanos) {
		LAST_PIXELS.set(pixels);
		LAST_TOTAL_NANOS.set(totalNanos);
		LAST_SAMPLE_NANOS.set(sampleNanos);
		MapFrameBudget.recordRaster(pixels, totalNanos);
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

	public static void recordApply(long texels, long copyNanos, long uploadNanos) {
		LAST_COPY_NANOS.set(copyNanos);
		LAST_UPLOAD_NANOS.set(uploadNanos);
		if (texels > 0) {
			emaCopyNanosPerTexel = ema(
				emaCopyNanosPerTexel == 0 ? DEFAULT_COPY_NANOS_PER_TEXEL : emaCopyNanosPerTexel,
				(double) copyNanos / texels
			);
			emaUploadNanosPerTexel = ema(
				emaUploadNanosPerTexel == 0 ? DEFAULT_UPLOAD_NANOS_PER_TEXEL : emaUploadNanosPerTexel,
				(double) uploadNanos / texels
			);
		}
		PlayersMap.LOGGER.debug(
			"Map apply: {} px, CPU copy {} ms, GPU upload {} ms (upload {}% of apply)",
			texels,
			copyNanos / 1_000_000L,
			uploadNanos / 1_000_000L,
			copyNanos + uploadNanos == 0 ? 0 : (uploadNanos * 100 / (copyNanos + uploadNanos))
		);
	}

	public static boolean preferStagedUpload(int texels) {
		long estimatedUpload = estimateUploadNanos(texels);
		long estimatedCopy = estimateCopyNanos(texels);
		if (estimatedUpload > MapFrameBudget.frameBudgetNanos() / 3) {
			return true;
		}
		return estimatedUpload > estimatedCopy * 2 && estimatedUpload > 500_000L;
	}

	public static long estimateCopyNanos(int texels) {
		double perTexel = emaCopyNanosPerTexel == 0 ? DEFAULT_COPY_NANOS_PER_TEXEL : emaCopyNanosPerTexel;
		return (long) (texels * perTexel);
	}

	public static long estimateUploadNanos(int texels) {
		double perTexel = emaUploadNanosPerTexel == 0 ? DEFAULT_UPLOAD_NANOS_PER_TEXEL : emaUploadNanosPerTexel;
		return (long) (texels * perTexel);
	}

	public static void resetBaseline() {
		BASELINE_TOTAL_NANOS.set(0);
	}

	private static double ema(double previous, double sample) {
		return previous * 0.85 + sample * 0.15;
	}
}
