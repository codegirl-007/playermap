package com.gredell.playermap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class TerrainMapRenderer {
	private static final int FULL_REBUILD_SLICE_THRESHOLD = 12_000;
	private static final long SLICE_NANOS_BUDGET_MINIMAP = 2_000_000L;
	private static final long SLICE_NANOS_BUDGET_FULLSCREEN = 3_000_000L;

	private final Identifier textureId;
	private final int maxTextureSize;
	private DynamicTexture texture;
	private int textureWidth;
	private int textureHeight;
	private boolean dirty = true;

	private boolean rasterValid;
	private ResourceKey<Level> lastDimension;
	private double lastBlocksPerPixel;
	private double lastMinX;
	private double lastMinZ;

	private boolean slicing;
	private int sliceIndex;
	private ResourceKey<Level> sliceDimension;
	private double sliceCenterX;
	private double sliceCenterZ;
	private double sliceBlocksPerPixel;
	private double sliceMinX;
	private double sliceMinZ;

	public TerrainMapRenderer(Identifier textureId, int maxTextureSize) {
		this.textureId = textureId;
		this.maxTextureSize = maxTextureSize;
	}

	public void markDirty() {
		dirty = true;
		slicing = false;
		sliceIndex = 0;
	}

	public void close(Minecraft client) {
		if (texture != null) {
			texture.close();
			texture = null;
			client.getTextureManager().release(textureId);
		}
		textureWidth = 0;
		textureHeight = 0;
		dirty = true;
		rasterValid = false;
		slicing = false;
		sliceIndex = 0;
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

		if (slicing) {
			advanceSlice(client, image);
			return;
		}

		double halfSpanX = (textureWidth * blocksPerPixel) / 2.0;
		double halfSpanZ = (textureHeight * blocksPerPixel) / 2.0;
		double minX = viewCenterX - halfSpanX;
		double minZ = viewCenterZ - halfSpanZ;

		if (dirty) {
			startFullRebuild(client, image, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ);
			return;
		}

		if (!rasterValid || resized) {
			startFullRebuild(client, image, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ);
			return;
		}

		if (!dimension.equals(lastDimension) || Double.compare(blocksPerPixel, lastBlocksPerPixel) != 0) {
			startFullRebuild(client, image, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ);
			return;
		}

		if (minX != lastMinX || minZ != lastMinZ) {
			switch (tryScrollRebuild(client, image, dimension, blocksPerPixel, minX, minZ)) {
				case SCROLLED -> texture.upload();
				case NEEDS_FULL_REBUILD -> startFullRebuild(
					client, image, dimension, viewCenterX, viewCenterZ, blocksPerPixel, minX, minZ
				);
				case UNCHANGED -> {
				}
			}
		}
	}

	private enum ScrollOutcome {
		UNCHANGED,
		SCROLLED,
		NEEDS_FULL_REBUILD
	}

	private void startFullRebuild(
		Minecraft client,
		NativeImage image,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		double minX,
		double minZ
	) {
		int texels = textureWidth * textureHeight;
		if (texels > FULL_REBUILD_SLICE_THRESHOLD) {
			slicing = true;
			sliceIndex = 0;
			sliceDimension = dimension;
			sliceCenterX = viewCenterX;
			sliceCenterZ = viewCenterZ;
			sliceBlocksPerPixel = blocksPerPixel;
			sliceMinX = minX;
			sliceMinZ = minZ;
			advanceSlice(client, image);
			return;
		}

		fillFull(client, image, dimension, blocksPerPixel, minX, minZ);
		texture.upload();
		dirty = false;
		slicing = false;
		storeRasterState(dimension, blocksPerPixel, minX, minZ);
	}

	private void advanceSlice(Minecraft client, NativeImage image) {
		int texels = textureWidth * textureHeight;
		long budget = texels > FULL_REBUILD_SLICE_THRESHOLD
			? SLICE_NANOS_BUDGET_FULLSCREEN
			: SLICE_NANOS_BUDGET_MINIMAP;
		long deadline = System.nanoTime() + budget;
		var level = client.level;
		MapWorldSampler.RasterPass pass = MapWorldSampler.beginRaster(level, sliceDimension);
		while (sliceIndex < texels && System.nanoTime() < deadline) {
			int pixelX = sliceIndex % textureWidth;
			int pixelZ = sliceIndex / textureWidth;
			writeTexel(pass, image, sliceBlocksPerPixel, sliceMinX, sliceMinZ, pixelX, pixelZ);
			sliceIndex++;
		}

		if (sliceIndex >= texels) {
			texture.upload();
			slicing = false;
			dirty = false;
			double halfSpanX = (textureWidth * sliceBlocksPerPixel) / 2.0;
			double halfSpanZ = (textureHeight * sliceBlocksPerPixel) / 2.0;
			storeRasterState(sliceDimension, sliceBlocksPerPixel, sliceCenterX - halfSpanX, sliceCenterZ - halfSpanZ);
		}
	}

	private void fillFull(
		Minecraft client,
		NativeImage image,
		ResourceKey<Level> dimension,
		double blocksPerPixel,
		double minX,
		double minZ
	) {
		MapWorldSampler.RasterPass pass = MapWorldSampler.beginRaster(client.level, dimension);
		for (int pixelX = 0; pixelX < textureWidth; pixelX++) {
			for (int pixelZ = 0; pixelZ < textureHeight; pixelZ++) {
				writeTexel(pass, image, blocksPerPixel, minX, minZ, pixelX, pixelZ);
			}
		}
	}

	private void writeTexel(
		MapWorldSampler.RasterPass pass,
		NativeImage image,
		double blocksPerPixel,
		double minX,
		double minZ,
		int pixelX,
		int pixelZ
	) {
		int color = pass.sampleTexel(minX, minZ, blocksPerPixel, pixelX, pixelZ);
		image.setPixel(pixelX, pixelZ, color);
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

		scrollImage(image, deltaPx, deltaPz);
		fillExposedStrips(client, image, dimension, blocksPerPixel, minX, minZ, deltaPx, deltaPz);
		storeRasterState(
			dimension,
			blocksPerPixel,
			lastMinX + deltaPx * blocksPerPixel,
			lastMinZ + deltaPz * blocksPerPixel
		);
		return ScrollOutcome.SCROLLED;
	}

	private void fillExposedStrips(
		Minecraft client,
		NativeImage image,
		ResourceKey<Level> dimension,
		double blocksPerPixel,
		double minX,
		double minZ,
		int deltaPx,
		int deltaPz
	) {
		MapWorldSampler.RasterPass pass = MapWorldSampler.beginRaster(client.level, dimension);
		if (deltaPx > 0) {
			for (int px = textureWidth - deltaPx; px < textureWidth; px++) {
				for (int pz = 0; pz < textureHeight; pz++) {
					writeTexel(pass, image, blocksPerPixel, minX, minZ, px, pz);
				}
			}
		} else if (deltaPx < 0) {
			for (int px = 0; px < -deltaPx; px++) {
				for (int pz = 0; pz < textureHeight; pz++) {
					writeTexel(pass, image, blocksPerPixel, minX, minZ, px, pz);
				}
			}
		}

		if (deltaPz > 0) {
			for (int pz = textureHeight - deltaPz; pz < textureHeight; pz++) {
				for (int px = 0; px < textureWidth; px++) {
					writeTexel(pass, image, blocksPerPixel, minX, minZ, px, pz);
				}
			}
		} else if (deltaPz < 0) {
			for (int pz = 0; pz < -deltaPz; pz++) {
				for (int px = 0; px < textureWidth; px++) {
					writeTexel(pass, image, blocksPerPixel, minX, minZ, px, pz);
				}
			}
		}
	}

	private static void scrollImage(NativeImage image, int deltaPx, int deltaPz) {
		int width = image.getWidth();
		int height = image.getHeight();

		if (deltaPx != 0) {
			NativeImage copy = new NativeImage(width, height, false);
			for (int px = 0; px < width; px++) {
				for (int pz = 0; pz < height; pz++) {
					copy.setPixel(px, pz, image.getPixel(px, pz));
				}
			}
			if (deltaPx > 0) {
				for (int px = 0; px < width - deltaPx; px++) {
					for (int pz = 0; pz < height; pz++) {
						image.setPixel(px, pz, copy.getPixel(px + deltaPx, pz));
					}
				}
			} else {
				int shift = -deltaPx;
				for (int px = shift; px < width; px++) {
					for (int pz = 0; pz < height; pz++) {
						image.setPixel(px, pz, copy.getPixel(px - shift, pz));
					}
				}
			}
			copy.close();
		}

		if (deltaPz != 0) {
			NativeImage copy = new NativeImage(width, height, false);
			for (int px = 0; px < width; px++) {
				for (int pz = 0; pz < height; pz++) {
					copy.setPixel(px, pz, image.getPixel(px, pz));
				}
			}
			if (deltaPz > 0) {
				for (int pz = 0; pz < height - deltaPz; pz++) {
					for (int px = 0; px < width; px++) {
						image.setPixel(px, pz, copy.getPixel(px, pz + deltaPz));
					}
				}
			} else {
				int shift = -deltaPz;
				for (int pz = shift; pz < height; pz++) {
					for (int px = 0; px < width; px++) {
						image.setPixel(px, pz, copy.getPixel(px, pz - shift));
					}
				}
			}
			copy.close();
		}
	}

	private void storeRasterState(ResourceKey<Level> dimension, double blocksPerPixel, double minX, double minZ) {
		lastDimension = dimension;
		lastBlocksPerPixel = blocksPerPixel;
		lastMinX = minX;
		lastMinZ = minZ;
		rasterValid = true;
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
		texture = new DynamicTexture(() -> "PlayersMap " + textureId, image);
		client.getTextureManager().register(textureId, texture);
		dirty = true;
		rasterValid = false;
		slicing = false;
		sliceIndex = 0;
		return true;
	}
}
