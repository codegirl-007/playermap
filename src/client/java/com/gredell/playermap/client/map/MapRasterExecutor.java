package com.gredell.playermap.client.map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

final class MapRasterExecutor {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "playersmap-raster");
			thread.setDaemon(true);
			return thread;
		}
	});

	private MapRasterExecutor() {
	}

	static ExecutorService executor() {
		return EXECUTOR;
	}
}
