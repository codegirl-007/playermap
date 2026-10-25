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
	private final Identifier textureId;
	private final int maxTextureSize;
	private DynamicTexture texture;
	private int textureWidth;
	private int textureHeight;
	private boolean dirty = true;

	public TerrainMapRenderer(Identifier textureId, int maxTextureSize) {
		this.textureId = textureId;
		this.maxTextureSize = maxTextureSize;
	}

	public void markDirty() {
		dirty = true;
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
		ensureTexture(client, targetWidth, targetHeight);
		if (texture == null) {
			return;
		}
		if (!dirty) {
			return;
		}

		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		var level = client.level;
		double halfSpanX = (textureWidth * blocksPerPixel) / 2.0;
		double halfSpanZ = (textureHeight * blocksPerPixel) / 2.0;
		double minX = viewCenterX - halfSpanX;
		double minZ = viewCenterZ - halfSpanZ;

		for (int pixelX = 0; pixelX < textureWidth; pixelX++) {
			for (int pixelZ = 0; pixelZ < textureHeight; pixelZ++) {
				int blockX = (int) Math.floor(minX + (pixelX + 0.5) * blocksPerPixel);
				int blockZ = (int) Math.floor(minZ + (pixelZ + 0.5) * blocksPerPixel);
				int color = MapWorldSampler.sampleColor(level, dimension, blockX, blockZ);
				// sampleColor is ARGB; setPixel converts to ABGR for NativeImage.
				image.setPixel(pixelX, pixelZ, color);
			}
		}

		texture.upload();
		dirty = false;
	}

	private void ensureTexture(Minecraft client, int targetWidth, int targetHeight) {
		if (texture != null && textureWidth == targetWidth && textureHeight == targetHeight) {
			return;
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
	}
}
