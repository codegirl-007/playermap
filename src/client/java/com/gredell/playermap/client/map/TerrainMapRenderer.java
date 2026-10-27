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
	private static final int ASYNC_REBUILD_TEXEL_THRESHOLD = 12_000;
	private static final int PLACEHOLDER_COLOR = 0xFF12151E;

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

	public TerrainMapRenderer(Identifier textureId, int maxTextureSize) {
		this.textureId = textureId;
		this.maxTextureSize = maxTextureSize;
	}

	public void markDirty() {
		rebuildGeneration++;
		asyncRebuild = null;
		dirty = true;
	}

	public void close(Minecraft client) {
		rebuildGeneration++;
		asyncRebuild = null;
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
		GuiGraphics graphics,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int mapLeft,
		int mapTop,
		int mapWidth,
		int mapHeight
	) {
		Minecraft client = Minecraft.getInstance();
		int targetWidth;
		int targetHeight;
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
		Minecraft client,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int targetWidth,
		int targetHeight
	) {
		boolean resized = ensureTexture(client, targetWidth, targetHeight);
		if (texture == null) {
			return;
		}

		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}

		if (tryFinishAsyncRebuild(image)) {
			// Applied finished background raster this frame.
		}

		if (asyncRebuild != null && !asyncRebuild.isDone()) {
			return;
		}

		double halfSpanX = (textureWidth * blocksPerPixel) / 2.0;
		double halfSpanZ = (textureHeight * blocksPerPixel) / 2.0;
		double minX = viewCenterX - halfSpanX;
		double minZ = viewCenterZ - halfSpanZ;

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
			switch (tryScrollRebuild(client, image, dimension, blocksPerPixel, minX, minZ)) {
				case SCROLLED -> texture.upload();
				case NEEDS_FULL_REBUILD -> startFullRebuild(
					client, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ, image
				);
				case UNCHANGED -> {
				}
			}
		}
	}

	private boolean tryFinishAsyncRebuild(NativeImage image) {
		if (asyncRebuild == null || !asyncRebuild.isDone()) {
			return false;
		}
		RasterJobResult result = asyncRebuild.join();
		asyncRebuild = null;
		if (result == null || result.generation != rebuildGeneration) {
			return false;
		}
		MapRasterEngine.writeToImage(image, result.pixels);
		texture.upload();
		dirty = false;
		storeRasterState(result.dimension, result.blocksPerPixel, result.minX, result.minZ);
		return true;
	}

	private void startFullRebuild(
		Minecraft client,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		double minX,
		double minZ,
		NativeImage image
	) {
		int texels = textureWidth * textureHeight;
		long jobGen = ++rebuildGeneration;
		MapRasterContext context = MapRasterContext.capture(
			client.level,
			dimension,
			blocksPerPixel,
			minX,
			minZ,
			textureWidth,
			textureHeight,
			jobGen
		);

		if (texels > ASYNC_REBUILD_TEXEL_THRESHOLD) {
			dirty = false;
			long capturedGen = jobGen;
			asyncRebuild = CompletableFuture.supplyAsync(
				() -> {
					int[] pixels = MapRasterEngine.raster(
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

		int[] pixels = MapRasterEngine.raster(context, () -> jobGen);
		if (pixels == null) {
			return;
		}
		MapRasterEngine.writeToImage(image, pixels);
		texture.upload();
		dirty = false;
		storeRasterState(dimension, blocksPerPixel, minX, minZ);
	}

	private enum ScrollOutcome {
		UNCHANGED,
		SCROLLED,
		NEEDS_FULL_REBUILD
	}

	private ScrollOutcome tryScrollRebuild(
		Minecraft client,
		NativeImage image,
		ResourceKey<Level> dimension,
		double blocksPerPixel,
		double minX,
		double minZ
	) {
		if (!rasterValid || !dimension.equals(lastDimension) || Double.compare(blocksPerPixel, lastBlocksPerPixel) != 0) {
			return ScrollOutcome.NEEDS_FULL_REBUILD;
		}

		int deltaPx = (int) Math.round((minX - lastMinX) / blocksPerPixel);
		int deltaPz = (int) Math.round((minZ - lastMinZ) / blocksPerPixel);
		if (deltaPx == 0 && deltaPz == 0) {
			return ScrollOutcome.UNCHANGED;
		}
		if (Math.abs(deltaPx) >= textureWidth || Math.abs(deltaPz) >= textureHeight) {
			return ScrollOutcome.NEEDS_FULL_REBUILD;
		}

		scrollImageInPlace(image, deltaPx, deltaPz);
		MapRasterContext context = MapRasterContext.capture(
			client.level,
			dimension,
			blocksPerPixel,
			minX,
			minZ,
			textureWidth,
			textureHeight,
			rebuildGeneration
		);
		fillExposedStrips(context, image, deltaPx, deltaPz);
		storeRasterState(
			dimension,
			blocksPerPixel,
			lastMinX + deltaPx * blocksPerPixel,
			lastMinZ + deltaPz * blocksPerPixel
		);
		return ScrollOutcome.SCROLLED;
	}

	private void fillExposedStrips(
		MapRasterContext context,
		NativeImage image,
		int deltaPx,
		int deltaPz
	) {
		MapRasterEngine.RasterPass pass = new MapRasterEngine.RasterPass(context);
		if (deltaPx > 0) {
			for (int px = textureWidth - deltaPx; px < textureWidth; px++) {
				for (int pz = 0; pz < textureHeight; pz++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
				}
			}
		} else if (deltaPx < 0) {
			for (int px = 0; px < -deltaPx; px++) {
				for (int pz = 0; pz < textureHeight; pz++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
				}
			}
		}

		if (deltaPz > 0) {
			for (int pz = textureHeight - deltaPz; pz < textureHeight; pz++) {
				for (int px = 0; px < textureWidth; px++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
				}
			}
		} else if (deltaPz < 0) {
			for (int pz = 0; pz < -deltaPz; pz++) {
				for (int px = 0; px < textureWidth; px++) {
					image.setPixel(px, pz, pass.sampleTexel(px, pz));
				}
			}
		}
	}

	private static void scrollImageInPlace(NativeImage image, int deltaPx, int deltaPz) {
		int width = image.getWidth();
		int height = image.getHeight();

		if (deltaPx > 0) {
			for (int pz = 0; pz < height; pz++) {
				for (int px = 0; px < width - deltaPx; px++) {
					image.setPixel(px, pz, image.getPixel(px + deltaPx, pz));
				}
			}
		} else if (deltaPx < 0) {
			int shift = -deltaPx;
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
			int shift = -deltaPz;
			for (int pz = height - shift - 1; pz >= 0; pz--) {
				for (int px = 0; px < width; px++) {
					image.setPixel(px, pz + shift, image.getPixel(px, pz));
				}
			}
		}
	}

	private void storeRasterState(ResourceKey<Level> dimension, double blocksPerPixel, double minX, double minZ) {
		lastDimension = dimension;
		lastBlocksPerPixel = blocksPerPixel;
		lastMinX = minX;
		lastMinZ = minZ;
		rasterValid = true;
	}

	private void fillPlaceholder(NativeImage image) {
		for (int pz = 0; pz < image.getHeight(); pz++) {
			for (int px = 0; px < image.getWidth(); px++) {
				image.setPixel(px, pz, PLACEHOLDER_COLOR);
			}
		}
	}

	private boolean ensureTexture(Minecraft client, int targetWidth, int targetHeight) {
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
		NativeImage image = new NativeImage(textureWidth, textureHeight, false);
		fillPlaceholder(image);
		texture = new DynamicTexture(() -> "PlayersMap " + textureId, image);
		client.getTextureManager().register(textureId, texture);
		texture.upload();
		dirty = true;
		rasterValid = false;
		rebuildGeneration++;
		asyncRebuild = null;
		return true;
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
