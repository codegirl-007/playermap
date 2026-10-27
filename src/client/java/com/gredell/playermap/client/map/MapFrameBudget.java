package com.gredell.playermap.client.map;

/**
 * Limits map work on the render/main thread using measured costs and a per-frame time budget.
 */
public final class MapFrameBudget {
	private static final long FRAME_BUDGET_NANOS = 2_500_000L;
	private static final long SYNC_RASTER_MAX_NANOS = 2_000_000L;

	private static final double DEFAULT_NANOS_PER_TEXEL = 900.0;
	private static final double DEFAULT_CACHE_RESOLVE_NANOS = 400.0;
	private static final double DEFAULT_CAPTURE_NANOS = 120_000.0;

	private static double emaNanosPerTexel = DEFAULT_NANOS_PER_TEXEL;
	private static double emaCacheResolveNanos = DEFAULT_CACHE_RESOLVE_NANOS;
	private static double emaCaptureNanos = DEFAULT_CAPTURE_NANOS;

	private MapFrameBudget() {
	}

	public static long frameBudgetNanos() {
		return FRAME_BUDGET_NANOS;
	}

	public static long deadlineNanos() {
		return System.nanoTime() + FRAME_BUDGET_NANOS;
	}

	public static boolean hasTimeRemaining(final long deadlineNanos) {
		return System.nanoTime() < deadlineNanos;
	}

	public static void recordRaster(final long texels, final long totalNanos) {
		if (texels <= 0 || totalNanos <= 0) {
			return;
		}
		emaNanosPerTexel = ema(emaNanosPerTexel, (double) totalNanos / texels);
	}

	public static void recordChunkResolve(final boolean liveCapture, final long nanos) {
		if (liveCapture) {
			emaCaptureNanos = ema(emaCaptureNanos, nanos);
		} else {
			emaCacheResolveNanos = ema(emaCacheResolveNanos, nanos);
		}
	}

	public static long estimateSyncRasterNanos(final int texels) {
		if (texels <= 0) {
			return 0;
		}
		return (long) (texels * emaNanosPerTexel);
	}

	/**
	 * Offload raster when measured per-texel cost would exceed the sync frame budget.
	 * Small, warm-cache maps typically stay on the main thread.
	 */
	public static boolean shouldRasterOnWorker(final int texels) {
		return estimateSyncRasterNanos(texels) > SYNC_RASTER_MAX_NANOS;
	}

	static double estimatedCacheResolveNanos() {
		return emaCacheResolveNanos;
	}

	static double estimatedCaptureNanos() {
		return emaCaptureNanos;
	}

	private static double ema(final double previous, final double sample) {
		return previous * 0.85 + sample * 0.15;
	}
}
