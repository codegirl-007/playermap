package com.gredell.playermap.client.map;

public final class MapColorUtil {
	private static final float CONTRAST = 0.10F;
	private static final float SATURATION_BOOST = 1.12F;

	private MapColorUtil() {
	}

	public static int adjustForReadability(final int argb) {
		final int a = (argb >> 24) & 0xFF;
		final int r = (argb >> 16) & 0xFF;
		final int g = (argb >> 8) & 0xFF;
		final int b = argb & 0xFF;

		final float rf = r / 255.0F;
		final float gf = g / 255.0F;
		final float bf = b / 255.0F;

		final float max = Math.max(rf, Math.max(gf, bf));
		final float min = Math.min(rf, Math.min(gf, bf));
		final float delta = max - min;
		if (delta < 0.02F) {
			final float gray = (rf + gf + bf) / 3.0F;
			final float adjusted = gray + (gray - 0.5F) * CONTRAST;
			final int channel = clampChannel(adjusted);
			return (a << 24) | (channel << 16) | (channel << 8) | channel;
		}

		float l = (max + min) * 0.5F;
		float s = delta / (1.0F - Math.abs(2.0F * l - 1.0F));
		s = Math.min(1.0F, s * SATURATION_BOOST);
		l = l + (l - 0.5F) * CONTRAST;
		l = Math.max(0.0F, Math.min(1.0F, l));

		final float c = (1.0F - Math.abs(2.0F * l - 1.0F)) * s;
		float hp = (rf - gf) / delta;
		if (Float.isNaN(hp)) {
			hp = 0.0F;
		} else if (max == gf) {
			hp = 2.0F + (bf - rf) / delta;
		} else if (max == bf) {
			hp = 4.0F + (rf - gf) / delta;
		}
		hp *= 60.0F;
		if (hp < 0.0F) {
			hp += 360.0F;
		}

		final float x = c * (1.0F - Math.abs((hp / 60.0F) % 2.0F - 1.0F));
		final float m = l - c / 2.0F;
		final float rf2;
		final float gf2;
		final float bf2;
		if (hp < 60.0F) {
			rf2 = c;
			gf2 = x;
			bf2 = 0.0F;
		} else if (hp < 120.0F) {
			rf2 = x;
			gf2 = c;
			bf2 = 0.0F;
		} else if (hp < 180.0F) {
			rf2 = 0.0F;
			gf2 = c;
			bf2 = x;
		} else if (hp < 240.0F) {
			rf2 = 0.0F;
			gf2 = x;
			bf2 = c;
		} else if (hp < 300.0F) {
			rf2 = x;
			gf2 = 0.0F;
			bf2 = c;
		} else {
			rf2 = c;
			gf2 = 0.0F;
			bf2 = x;
		}

		return (a << 24)
			| (clampChannel(rf2 + m) << 16)
			| (clampChannel(gf2 + m) << 8)
			| clampChannel(bf2 + m);
	}

	public static int tintWater(final int argb, final boolean water) {
		if (!water) {
			return argb;
		}
		final int a = (argb >> 24) & 0xFF;
		final int r = (int) (((argb >> 16) & 0xFF) * 0.72F);
		final int g = (int) (((argb >> 8) & 0xFF) * 0.82F);
		final int b = Math.min(255, (int) ((argb & 0xFF) * 1.08F + 12));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int darken(final int argb, final float factor) {
		final int a = (argb >> 24) & 0xFF;
		final int r = clampChannel(((argb >> 16) & 0xFF) * factor / 255.0F);
		final int g = clampChannel(((argb >> 8) & 0xFF) * factor / 255.0F);
		final int b = clampChannel((argb & 0xFF) * factor / 255.0F);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int clampChannel(final float value) {
		return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
	}
}
