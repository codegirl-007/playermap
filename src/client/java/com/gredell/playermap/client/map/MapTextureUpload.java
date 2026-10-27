package com.gredell.playermap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * GPU upload helpers for map {@link DynamicTexture}s, including partial row bands via
 * {@code CommandEncoder.writeToTexture}.
 */
public final class MapTextureUpload {
	private static final int DEFAULT_STAGED_BAND_ROWS = 64;

	private MapTextureUpload() {
	}

	public static long uploadFull(final DynamicTexture texture) {
		final long start = System.nanoTime();
		texture.upload();
		return System.nanoTime() - start;
	}

	/**
	 * Uploads a horizontal band from {@code image} into the texture at the same Y offset.
	 *
	 * @param mipLevel 0 for base level
	 * @param destY    destination Y in texels
	 * @param srcY     source Y in {@code image}
	 * @param width    band width in texels
	 * @param height   band height in texels
	 */
	public static long uploadBand(
		final DynamicTexture texture,
		final NativeImage image,
		final int mipLevel,
		final int destY,
		final int srcY,
		final int width,
		final int height
	) {
		final long start = System.nanoTime();
		// Parameter order matches SpriteContents.uploadFirstFrame: mip, layer, destX, destY, width, height, srcX, srcY.
		RenderSystem.getDevice()
			.createCommandEncoder()
			.writeToTexture(
				texture.getTexture(),
				image,
				mipLevel,
				0,
				0,
				destY,
				width,
				height,
				0,
				srcY
			);
		return System.nanoTime() - start;
	}

	public static int stagedBandRows(final int textureHeight) {
		final int rows = DEFAULT_STAGED_BAND_ROWS;
		if (textureHeight <= 128) {
			return textureHeight;
		}
		return Math.min(rows, Math.max(32, textureHeight / 8));
	}

	public static int bandsForHeight(final int textureHeight, final int bandRows) {
		return (textureHeight + bandRows - 1) / bandRows;
	}
}
