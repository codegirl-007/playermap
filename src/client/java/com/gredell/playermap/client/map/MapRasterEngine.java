package com.gredell.playermap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class MapRasterEngine {
	private MapRasterEngine() {
	}

	public static int[] raster(MapRasterContext context, LongSupplier cancelled) {
		int texels = context.width() * context.height();
		int[] pixels = new int[texels];
		RasterPass pass = new RasterPass(context);
		long start = System.nanoTime();
		long sampleNanos = 0;
		for (int pixelZ = 0; pixelZ < context.height(); pixelZ++) {
			if ((pixelZ & 7) == 0 && cancelled.getAsLong() != context.jobGeneration()) {
				return null;
			}
			for (int pixelX = 0; pixelX < context.width(); pixelX++) {
				long t0 = System.nanoTime();
				pixels[pixelZ * context.width() + pixelX] = pass.sampleTexel(pixelX, pixelZ);
				sampleNanos += System.nanoTime() - t0;
			}
		}
		MapRasterMetrics.record(texels, System.nanoTime() - start, sampleNanos);
		return pixels;
	}

	/**
	 * Copies raster output into a {@link NativeImage} using row-major order (no per-pixel division).
	 *
	 * @return CPU time spent copying, in nanoseconds
	 */
	public static long writeToImage(NativeImage image, int width, int height, int[] pixels) {
		long start = System.nanoTime();
		int rowBase = 0;
		for (int z = 0; z < height; z++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, z, pixels[rowBase + x]);
			}
			rowBase += width;
		}
		return System.nanoTime() - start;
	}

	public static final class RasterPass {
		private final MapRasterContext context;
		private final Map<Integer, Integer> dominantCounts = new HashMap<>();

		RasterPass(MapRasterContext context) {
			this.context = context;
		}

		public int sampleTexel(int pixelX, int pixelZ) {
			double bpp = context.blocksPerPixel();
			if (bpp <= 1.0) {
				int blockX = (int) Math.floor(context.minX() + (pixelX + 0.5) * bpp);
				int blockZ = (int) Math.floor(context.minZ() + (pixelZ + 0.5) * bpp);
				return sampleBlock(blockX, blockZ);
			}
			return sampleDominantFootprint(pixelX, pixelZ);
		}

		public int sampleBlock(int blockX, int blockZ) {
			int cx = blockX >> 4;
			int cz = blockZ >> 4;
			ChunkSurfaceSnapshot snapshot = context.snapshot(cx, cz);
			if (snapshot == null) {
				return context.missingDataColor();
			}

			int base = snapshot.color(blockX, blockZ);
			if (base == 0) {
				return context.missingDataColor();
			}

			short height = snapshot.height(blockX, blockZ);
			return finishTerrain(
				base,
				snapshot.water(blockX, blockZ),
				height,
				heightAt(blockX, blockZ - 1),
				heightAt(blockX, blockZ + 1),
				heightAt(blockX + 1, blockZ),
				heightAt(blockX - 1, blockZ)
			);
		}

		private int sampleDominantFootprint(int pixelX, int pixelZ) {
			double bpp = context.blocksPerPixel();
			double startX = context.minX() + pixelX * bpp;
			double startZ = context.minZ() + pixelZ * bpp;
			int x0 = (int) Math.floor(startX);
			int z0 = (int) Math.floor(startZ);
			int x1 = (int) Math.floor(startX + bpp - 1e-6);
			int z1 = (int) Math.floor(startZ + bpp - 1e-6);
			int stepX = Math.max(1, (x1 - x0 + 1) / 7);
			int stepZ = Math.max(1, (z1 - z0 + 1) / 7);

			dominantCounts.clear();
			int bestColor = context.missingDataColor();
			int bestCount = 0;

			for (int bx = x0; bx <= x1; bx += stepX) {
				for (int bz = z0; bz <= z1; bz += stepZ) {
					int color = sampleBlock(bx, bz);
					int count = dominantCounts.merge(color, 1, Integer::sum);
					if (count > bestCount) {
						bestCount = count;
						bestColor = color;
					}
				}
			}

			if (bestCount == 0) {
				return sampleBlock((x0 + x1) / 2, (z0 + z1) / 2);
			}
			return bestColor;
		}

		private short heightAt(int blockX, int blockZ) {
			int cx = blockX >> 4;
			int cz = blockZ >> 4;
			ChunkSurfaceSnapshot snapshot = context.snapshot(cx, cz);
			if (snapshot == null) {
				return Short.MIN_VALUE;
			}
			return snapshot.height(blockX, blockZ);
		}

		private int finishTerrain(
			int base,
			boolean water,
			short surfaceY,
			short northY,
			short southY,
			short eastY,
			short westY
		) {
			int shaded = MapTerrainShading.apply(base, surfaceY, northY, southY, eastY, westY, context.overworld());
			int readable = MapColorUtil.adjustForReadability(shaded);
			return MapColorUtil.tintWater(readable, water);
		}
	}
}
