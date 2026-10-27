package com.gredell.playermap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class TerrainMapRenderer {
	private static final int PLACEHOLDER_COLOR = 0xFF12151E;
	private static final int STRIP_FILL_TIME_CHECK_INTERVAL = 32;

	private final Identifier textureId;
	private final int maxTextureSize;
	private final ExecutorService rasterExecutor = MapRasterExecutor.executor();

	private DynamicTexture texture;
	private int textureWidth;
	private int textureHeight;
	private boolean dirty = true;

	private boolean rasterValid;
	private ResourceKey<Level> lastDimension;
	private double lastBlocksPerPixel;
	private double lastMinX;
	private double lastMinZ;

	private volatile long rebuildGeneration;
	private CompletableFuture<RasterJobResult> asyncRebuild;
	private MapRasterContext.Prepare pendingPrepare;
	private PendingPanScroll pendingPanScroll;
	private PendingTextureUpload pendingTextureUpload;

	public TerrainMapRenderer(final Identifier textureId, final int maxTextureSize) {
		this.textureId = textureId;
		this.maxTextureSize = maxTextureSize;
	}

	public void markDirty() {
		rebuildGeneration++;
		asyncRebuild = null;
		pendingPrepare = null;
		pendingPanScroll = null;
		pendingTextureUpload = null;
		dirty = true;
	}

	public void close(final Minecraft client) {
		rebuildGeneration++;
		asyncRebuild = null;
		pendingPrepare = null;
		pendingPanScroll = null;
		pendingTextureUpload = null;
		if (texture != null) {
			texture.close();
			texture = null;
			client.getTextureManager().release(textureId);
		}
		textureWidth = 0;
		textureHeight = 0;
		dirty = true;
		rasterValid = false;
	}

	public void draw(
		final GuiGraphics graphics,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapLeft,
		final int mapTop,
		final int mapWidth,
		final int mapHeight
	) {
		final Minecraft client = Minecraft.getInstance();
		final int targetWidth;
		final int targetHeight;
		if (mapWidth >= mapHeight) {
			targetWidth = Math.min(mapWidth, maxTextureSize);
			targetHeight = Math.max(1, (int) Math.round(targetWidth * (mapHeight / (double) mapWidth)));
		} else {
			targetHeight = Math.min(mapHeight, maxTextureSize);
			targetWidth = Math.max(1, (int) Math.round(targetHeight * (mapWidth / (double) mapHeight)));
		}

		updateTexture(client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, targetWidth, targetHeight);
		if (texture == null) {
			return;
		}

		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			textureId,
			mapLeft,
			mapTop,
			0,
			0,
			mapWidth,
			mapHeight,
			textureWidth,
			textureHeight,
			textureWidth,
			textureHeight
		);
	}

	private void updateTexture(
		final Minecraft client,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int targetWidth,
		final int targetHeight
	) {
		final boolean resized = ensureTexture(client, targetWidth, targetHeight);
		if (texture == null) {
			return;
		}

		final NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}

		final long frameDeadline = MapFrameBudget.deadlineNanos();

		if (tryFinishAsyncRebuild(image, frameDeadline)) {
			// Applied finished background raster this frame.
		}

		if (pendingTextureUpload != null) {
			if (pendingTextureUpload.generation != rebuildGeneration) {
				pendingTextureUpload = null;
			} else {
				tickStagedTextureUpload(image, frameDeadline);
				return;
			}
		}

		if (asyncRebuild != null && !asyncRebuild.isDone()) {
			return;
		}
		final double halfSpanX = (textureWidth * blocksPerPixel) / 2.0;
		final double halfSpanZ = (textureHeight * blocksPerPixel) / 2.0;
		final double minX = viewCenterX - halfSpanX;
		final double minZ = viewCenterZ - halfSpanZ;

		if (pendingPanScroll != null) {
			final PendingPanScroll pending = pendingPanScroll;
			if (pending.generation != rebuildGeneration
				|| pending.targetMinX != minX
				|| pending.targetMinZ != minZ) {
				pendingPanScroll = null;
			} else {
				if (tickPendingPanScroll(client, image, frameDeadline)) {
					texture.upload();
				}
				return;
			}
		}

		if (pendingPrepare != null) {
			if (pendingPrepare.jobGeneration() != rebuildGeneration) {
				pendingPrepare = null;
			} else {
				pendingPrepare.advanceWithinBudget(client.level, frameDeadline);
				if (!pendingPrepare.isComplete()) {
					return;
				}
				final MapRasterContext context = pendingPrepare.build();
				pendingPrepare = null;
				launchRasterJob(
					client,
					dimension,
					blocksPerPixel,
					context.minX(),
					context.minZ(),
					image,
					context
				);
				return;
			}
		}

		if (dirty) {
			startFullRebuild(client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ, image);
			return;
		}

		if (!rasterValid || resized) {
			startFullRebuild(client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ, image);
			return;
		}

		if (!dimension.equals(lastDimension) || Double.compare(blocksPerPixel, lastBlocksPerPixel) != 0) {
			startFullRebuild(client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ, image);
			return;
		}

		if (minX != lastMinX || minZ != lastMinZ) {
			switch (tryScrollRebuild(client, image, dimension, blocksPerPixel, minX, minZ, frameDeadline)) {
				case SCROLLED -> texture.upload();
				case NEEDS_FULL_REBUILD -> startFullRebuild(
					client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ, image
				);
				case UNCHANGED, PENDING -> {
				}
			}
		}
	}

	private boolean tryFinishAsyncRebuild(final NativeImage image, final long frameDeadline) {
		if (asyncRebuild == null || !asyncRebuild.isDone()) {
			return false;
		}
		final RasterJobResult result = asyncRebuild.join();
		asyncRebuild = null;
		if (result == null || result.generation != rebuildGeneration) {
			return false;
		}
		applyRasterToTexture(
			image,
			result.pixels,
			result.dimension,
			result.blocksPerPixel,
			result.minX,
			result.minZ,
			frameDeadline
		);
		dirty = false;
		return true;
	}

	private void applyRasterToTexture(
		final NativeImage image,
		final int[] pixels,
		final ResourceKey<Level> dimension,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final long frameDeadline
	) {
		final int texels = textureWidth * textureHeight;
		final long copyNanos = MapRasterEngine.writeToImage(image, textureWidth, textureHeight, pixels);

		if (MapRasterMetrics.preferStagedUpload(texels)) {
			pendingTextureUpload = PendingTextureUpload.start(
				rebuildGeneration,
				texels,
				copyNanos,
				dimension,
				blocksPerPixel,
				minX,
				minZ,
				MapTextureUpload.stagedBandRows(textureHeight)
			);
			tickStagedTextureUpload(image, frameDeadline);
			return;
		}

		final long uploadNanos = MapTextureUpload.uploadFull(texture);
		MapRasterMetrics.recordApply(texels, copyNanos, uploadNanos);
		storeRasterState(dimension, blocksPerPixel, minX, minZ);
	}

	private void tickStagedTextureUpload(final NativeImage image, final long frameDeadline) {
		final PendingTextureUpload pending = pendingTextureUpload;
		if (pending == null) {
			return;
		}
		while (pending.nextRow < textureHeight && MapFrameBudget.hasTimeRemaining(frameDeadline)) {
			final int rows = Math.min(pending.bandRows, textureHeight - pending.nextRow);
			try {
				pending.accumulatedUploadNanos += MapTextureUpload.uploadBand(
					texture,
					image,
					0,
					pending.nextRow,
					pending.nextRow,
					textureWidth,
					rows
				);
			} catch (IllegalArgumentException ex) {
				pending.accumulatedUploadNanos += MapTextureUpload.uploadFull(texture);
				pending.nextRow = textureHeight;
				break;
			}
			pending.nextRow += rows;
		}
		if (pending.nextRow < textureHeight) {
			return;
		}
		MapRasterMetrics.recordApply(pending.texels, pending.copyNanos, pending.accumulatedUploadNanos);
		storeRasterState(pending.dimension, pending.blocksPerPixel, pending.minX, pending.minZ);
		pendingTextureUpload = null;
	}

	private void startFullRebuild(
		final Minecraft client,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final NativeImage image
	) {
		final long jobGen = ++rebuildGeneration;
		pendingPanScroll = null;
		pendingTextureUpload = null;
		pendingPrepare = MapRasterContext.beginPrepare(
			dimension,
			blocksPerPixel,
			minX,
			minZ,
			textureWidth,
			textureHeight,
			jobGen
		);
		dirty = false;
		pendingPrepare.advanceWithinBudget(client.level, MapFrameBudget.deadlineNanos());
		if (!pendingPrepare.isComplete()) {
			return;
		}
		final MapRasterContext context = pendingPrepare.build();
		pendingPrepare = null;
		launchRasterJob(client, dimension, blocksPerPixel, minX, minZ, image, context);
	}

	private void launchRasterJob(
		final Minecraft client,
		final ResourceKey<Level> dimension,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final NativeImage image,
		final MapRasterContext context
	) {
		final int texels = textureWidth * textureHeight;
		final long jobGen = context.jobGeneration();

		if (MapFrameBudget.shouldRasterOnWorker(texels)) {
			final long capturedGen = jobGen;
			asyncRebuild = CompletableFuture.supplyAsync(
				() -> {
					final int[] pixels = MapRasterEngine.raster(
						context,
						() -> rebuildGeneration == capturedGen ? capturedGen : -1L
					);
					if (pixels == null) {
						return null;
					}
					return new RasterJobResult(capturedGen, pixels, dimension, blocksPerPixel, minX, minZ);
				},
				rasterExecutor
			);
			return;
		}

		final int[] pixels = MapRasterEngine.raster(context, () -> jobGen);
		if (pixels == null) {
			return;
		}
		applyRasterToTexture(image, pixels, dimension, blocksPerPixel, minX, minZ, MapFrameBudget.deadlineNanos());
	}

	private enum ScrollOutcome {
		UNCHANGED,
		PENDING,
		SCROLLED,
		NEEDS_FULL_REBUILD
	}

	private ScrollOutcome tryScrollRebuild(
		final Minecraft client,
		final NativeImage image,
		final ResourceKey<Level> dimension,
		final double blocksPerPixel,
		final double minX,
		final double minZ,
		final long frameDeadline
	) {
		if (!rasterValid || !dimension.equals(lastDimension) || Double.compare(blocksPerPixel, lastBlocksPerPixel) != 0) {
			return ScrollOutcome.NEEDS_FULL_REBUILD;
		}

		final int deltaPx = (int) Math.round((minX - lastMinX) / blocksPerPixel);
		final int deltaPz = (int) Math.round((minZ - lastMinZ) / blocksPerPixel);
		if (deltaPx == 0 && deltaPz == 0) {
			return ScrollOutcome.UNCHANGED;
		}
		if (Math.abs(deltaPx) >= textureWidth || Math.abs(deltaPz) >= textureHeight) {
			return ScrollOutcome.NEEDS_FULL_REBUILD;
		}

		final MapRasterContext.Prepare prep = MapRasterContext.beginPrepare(
			dimension,
			blocksPerPixel,
			minX,
			minZ,
			textureWidth,
			textureHeight,
			rebuildGeneration
		);
		prep.advanceWithinBudget(client.level, frameDeadline);
		if (!prep.isComplete()) {
			pendingPanScroll = PendingPanScroll.preparing(
				rebuildGeneration,
				prep,
				deltaPx,
				deltaPz,
				dimension,
				blocksPerPixel,
				minX,
				minZ
			);
			return ScrollOutcome.PENDING;
		}

		final MapRasterContext context = prep.build();
		scrollImageInPlace(image, deltaPx, deltaPz);
		final StripFillProgress progress = new StripFillProgress(deltaPx, deltaPz);
		if (!fillExposedStripsWithinBudget(context, image, deltaPx, deltaPz, progress, frameDeadline)) {
			pendingPanScroll = PendingPanScroll.filling(
				rebuildGeneration,
				context,
				deltaPx,
				deltaPz,
				dimension,
				blocksPerPixel,
				minX,
				minZ,
				progress
			);
			return ScrollOutcome.PENDING;
		}
		storeRasterState(
			dimension,
			blocksPerPixel,
			lastMinX + deltaPx * blocksPerPixel,
			lastMinZ + deltaPz * blocksPerPixel
		);
		return ScrollOutcome.SCROLLED;
	}

	private boolean tickPendingPanScroll(final Minecraft client, final NativeImage image, final long frameDeadline) {
		final PendingPanScroll pending = pendingPanScroll;
		if (pending.prepare != null) {
			pending.prepare.advanceWithinBudget(client.level, frameDeadline);
			if (!pending.prepare.isComplete()) {
				return false;
			}
			pending.context = pending.prepare.build();
			pending.prepare = null;
			scrollImageInPlace(image, pending.deltaPx, pending.deltaPz);
		}

		if (fillExposedStripsWithinBudget(
			pending.context,
			image,
			pending.deltaPx,
			pending.deltaPz,
			pending.fillProgress,
			frameDeadline
		)) {
			storeRasterState(
				pending.dimension,
				pending.blocksPerPixel,
				pending.targetMinX,
				pending.targetMinZ
			);
			pendingPanScroll = null;
			return true;
		}
		return false;
	}

	private boolean fillExposedStripsWithinBudget(
		final MapRasterContext context,
		final NativeImage image,
		final int deltaPx,
		final int deltaPz,
		final StripFillProgress progress,
		final long frameDeadline
	) {
		final MapRasterEngine.RasterPass pass = new MapRasterEngine.RasterPass(context);
		int samplesSinceCheck = 0;

		if (!progress.xStripsDone) {
			final int pxStart = deltaPx > 0 ? textureWidth - deltaPx : 0;
			final int pxEnd = deltaPx > 0 ? textureWidth : -deltaPx;
			for (int px = Math.max(progress.xPx, pxStart); px < pxEnd; px++) {
				final int pzStart = px == progress.xPx ? progress.xPz : 0;
				for (int pz = pzStart; pz < textureHeight; pz++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
					if (++samplesSinceCheck >= STRIP_FILL_TIME_CHECK_INTERVAL
						&& !MapFrameBudget.hasTimeRemaining(frameDeadline)) {
						progress.xPx = px;
						progress.xPz = pz + 1;
						if (progress.xPz >= textureHeight) {
							progress.xPx = px + 1;
							progress.xPz = 0;
						}
						return false;
					}
				}
			}
			progress.xStripsDone = true;
			progress.xPx = 0;
			progress.xPz = 0;
		}

		if (!progress.zStripsDone) {
			final int pzStart = deltaPz > 0 ? textureHeight - deltaPz : 0;
			final int pzEnd = deltaPz > 0 ? textureHeight : -deltaPz;
			for (int pz = Math.max(progress.zPz, pzStart); pz < pzEnd; pz++) {
				final int pxStart = pz == progress.zPz ? progress.zPx : 0;
				for (int px = pxStart; px < textureWidth; px++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
					if (++samplesSinceCheck >= STRIP_FILL_TIME_CHECK_INTERVAL
						&& !MapFrameBudget.hasTimeRemaining(frameDeadline)) {
						progress.zPz = pz;
						progress.zPx = px + 1;
						if (progress.zPx >= textureWidth) {
							progress.zPz = pz + 1;
							progress.zPx = 0;
						}
						return false;
					}
				}
			}
			progress.zStripsDone = true;
		}

		return true;
	}

	private static void scrollImageInPlace(final NativeImage image, final int deltaPx, final int deltaPz) {
		final int width = image.getWidth();
		final int height = image.getHeight();

		if (deltaPx > 0) {
			for (int pz = 0; pz < height; pz++) {
				for (int px = 0; px < width - deltaPx; px++) {
					image.setPixel(px, pz, image.getPixel(px + deltaPx, pz));
				}
			}
		} else if (deltaPx < 0) {
			final int shift = -deltaPx;
			for (int pz = 0; pz < height; pz++) {
				for (int px = width - shift - 1; px >= 0; px--) {
					image.setPixel(px + shift, pz, image.getPixel(px, pz));
				}
			}
		}

		if (deltaPz > 0) {
			for (int pz = 0; pz < height - deltaPz; pz++) {
				for (int px = 0; px < width; px++) {
					image.setPixel(px, pz, image.getPixel(px, pz + deltaPz));
				}
			}
		} else if (deltaPz < 0) {
			final int shift = -deltaPz;
			for (int pz = height - shift - 1; pz >= 0; pz--) {
				for (int px = 0; px < width; px++) {
					image.setPixel(px, pz + shift, image.getPixel(px, pz));
				}
			}
		}
	}

	private void storeRasterState(
		final ResourceKey<Level> dimension,
		final double blocksPerPixel,
		final double minX,
		final double minZ
	) {
		lastDimension = dimension;
		lastBlocksPerPixel = blocksPerPixel;
		lastMinX = minX;
		lastMinZ = minZ;
		rasterValid = true;
	}

	private void fillPlaceholder(final NativeImage image) {
		for (int pz = 0; pz < image.getHeight(); pz++) {
			for (int px = 0; px < image.getWidth(); px++) {
				image.setPixel(px, pz, PLACEHOLDER_COLOR);
			}
		}
	}

	private boolean ensureTexture(final Minecraft client, final int targetWidth, final int targetHeight) {
		if (texture != null && textureWidth == targetWidth && textureHeight == targetHeight) {
			return false;
		}

		if (texture != null) {
			texture.close();
			client.getTextureManager().release(textureId);
			texture = null;
		}

		textureWidth = targetWidth;
		textureHeight = targetHeight;
		final NativeImage image = new NativeImage(textureWidth, textureHeight, false);
		fillPlaceholder(image);
		texture = new DynamicTexture(() -> "PlayersMap " + textureId, image);
		client.getTextureManager().register(textureId, texture);
		texture.upload();
		dirty = true;
		rasterValid = false;
		rebuildGeneration++;
		asyncRebuild = null;
		pendingPrepare = null;
		pendingPanScroll = null;
		pendingTextureUpload = null;
		return true;
	}

	private static final class PendingTextureUpload {
		final long generation;
		final int texels;
		final long copyNanos;
		final ResourceKey<Level> dimension;
		final double blocksPerPixel;
		final double minX;
		final double minZ;
		final int bandRows;
		int nextRow;
		long accumulatedUploadNanos;

		private PendingTextureUpload(
			final long generation,
			final int texels,
			final long copyNanos,
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double minX,
			final double minZ,
			final int bandRows
		) {
			this.generation = generation;
			this.texels = texels;
			this.copyNanos = copyNanos;
			this.dimension = dimension;
			this.blocksPerPixel = blocksPerPixel;
			this.minX = minX;
			this.minZ = minZ;
			this.bandRows = bandRows;
		}

		static PendingTextureUpload start(
			final long generation,
			final int texels,
			final long copyNanos,
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double minX,
			final double minZ,
			final int bandRows
		) {
			return new PendingTextureUpload(
				generation,
				texels,
				copyNanos,
				dimension,
				blocksPerPixel,
				minX,
				minZ,
				bandRows
			);
		}
	}

	private static final class StripFillProgress {
		boolean xStripsDone;
		int xPx;
		int xPz;
		boolean zStripsDone;
		int zPx;
		int zPz;

		StripFillProgress(final int deltaPx, final int deltaPz) {
			xStripsDone = deltaPx == 0;
			zStripsDone = deltaPz == 0;
		}
	}

	private static final class PendingPanScroll {
		final long generation;
		MapRasterContext.Prepare prepare;
		MapRasterContext context;
		final int deltaPx;
		final int deltaPz;
		final ResourceKey<Level> dimension;
		final double blocksPerPixel;
		final double targetMinX;
		final double targetMinZ;
		final StripFillProgress fillProgress;

		private PendingPanScroll(
			final long generation,
			final MapRasterContext.Prepare prepare,
			final MapRasterContext context,
			final int deltaPx,
			final int deltaPz,
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double targetMinX,
			final double targetMinZ,
			final StripFillProgress fillProgress
		) {
			this.generation = generation;
			this.prepare = prepare;
			this.context = context;
			this.deltaPx = deltaPx;
			this.deltaPz = deltaPz;
			this.dimension = dimension;
			this.blocksPerPixel = blocksPerPixel;
			this.targetMinX = targetMinX;
			this.targetMinZ = targetMinZ;
			this.fillProgress = fillProgress;
		}

		static PendingPanScroll preparing(
			final long generation,
			final MapRasterContext.Prepare prepare,
			final int deltaPx,
			final int deltaPz,
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double targetMinX,
			final double targetMinZ
		) {
			return new PendingPanScroll(
				generation,
				prepare,
				null,
				deltaPx,
				deltaPz,
				dimension,
				blocksPerPixel,
				targetMinX,
				targetMinZ,
				new StripFillProgress(deltaPx, deltaPz)
			);
		}

		static PendingPanScroll filling(
			final long generation,
			final MapRasterContext context,
			final int deltaPx,
			final int deltaPz,
			final ResourceKey<Level> dimension,
			final double blocksPerPixel,
			final double targetMinX,
			final double targetMinZ,
			final StripFillProgress fillProgress
		) {
			return new PendingPanScroll(
				generation,
				null,
				context,
				deltaPx,
				deltaPz,
				dimension,
				blocksPerPixel,
				targetMinX,
				targetMinZ,
				fillProgress
			);
		}
	}

	private record RasterJobResult(
		long generation,
		int[] pixels,
		ResourceKey<Level> dimension,
		double blocksPerPixel,
		double minX,
		double minZ
	) {
	}
}
