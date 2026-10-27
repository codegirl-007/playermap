package com.gredell.playermap.client.map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;

public final class MapUiStyles {
	private static final int ACCENT = ARGB.color(255, 86, 196, 214);
	private static final int ACCENT_DIM = ARGB.color(255, 52, 118, 132);
	private static final int PANEL = ARGB.color(245, 22, 26, 36);
	private static final int PANEL_EDGE = ARGB.color(255, 58, 66, 82);
	private static final int SHADOW = ARGB.color(100, 0, 0, 0);

	private MapUiStyles() {
	}

	public static void drawFullscreenBackdrop(final GuiGraphics graphics, final int width, final int height) {
		graphics.fillGradient(0, 0, width, height, ARGB.color(255, 14, 18, 28), ARGB.color(255, 6, 8, 14));
		graphics.fillGradient(0, 0, width, height / 3, ARGB.color(60, 86, 196, 214), ARGB.color(0, 0, 0, 0));
	}

	public static void drawMapFrame(final GuiGraphics graphics, final int left, final int top, final int size, final boolean large) {
		final int pad = large ? 5 : 3;
		graphics.fill(left - pad - 2, top - pad - 2, left + size + pad + 2, top + size + pad + 2, SHADOW);
		graphics.fill(left - pad, top - pad, left + size + pad, top + size + pad, PANEL_EDGE);
		graphics.fill(left - pad + 1, top - pad + 1, left + size + pad - 1, top + size + pad - 1, PANEL);
		graphics.fill(left - 1, top - 1, left + size + 1, top + size + 1, ACCENT_DIM);
		graphics.renderOutline(left - 1, top - 1, size + 2, size + 2, ACCENT);
		graphics.fill(left, top, left + size, top + 1, ARGB.color(90, 255, 255, 255));
	}

	/** Outer inset from map content edge (pad + shadow), matches {@link #drawMapFrame}. */
	public static int minimapFrameOuterInset() {
		return 5;
	}

	public static void drawMinimapCoords(
		final GuiGraphics graphics,
		final Font font,
		final int left,
		final int top,
		final int width,
		final int blockX,
		final int blockY,
		final int blockZ
	) {
		final String text = String.format("x %d  ·  y %d  ·  z %d", blockX, blockY, blockZ);
		final int barHeight = 12;
		graphics.fill(left, top, left + width, top + barHeight, ARGB.color(200, 12, 16, 24));
		graphics.renderOutline(left, top, width, barHeight, ACCENT_DIM);
		final int textX = left + Math.max(4, (width - font.width(text)) / 2);
		graphics.drawString(font, Component.literal(text), textX, top + 2, ARGB.color(255, 190, 200, 215), false);
	}

	public static void drawMinimapChrome(
		final GuiGraphics graphics,
		final Font font,
		final int left,
		final int top,
		final int size,
		final ResourceKey<Level> dimension
	) {
		drawMapFrame(graphics, left, top, size, false);
		final int labelW = font.width(MapDimensions.shortLabel(dimension)) + 8;
		graphics.fill(left + 3, top + 3, left + 3 + labelW, top + 13, ARGB.color(200, 12, 16, 24));
		graphics.drawString(font, Component.literal(MapDimensions.shortLabel(dimension)), left + 7, top + 4, ACCENT, false);
		drawNorthIndicator(graphics, font, left, top, size, 10);
	}

	public static void drawNorthIndicator(
		final GuiGraphics graphics,
		final Font font,
		final int left,
		final int top,
		final int size,
		final int inset
	) {
		final int cx = left + size / 2;
		final int y = top + inset;
		graphics.drawString(font, Component.literal("N"), cx - font.width("N") / 2, y, ARGB.color(220, 240, 248, 255), true);
		graphics.fill(cx, y + 9, cx + 1, y + 13, ARGB.color(180, 240, 248, 255));
	}

	public static void drawCenterCrosshair(
		final GuiGraphics graphics,
		final int left,
		final int top,
		final int size,
		final int color
	) {
		final int cx = left + size / 2;
		final int cy = top + size / 2;
		graphics.fill(cx - 4, cy, cx + 5, cy + 1, color);
		graphics.fill(cx, cy - 4, cx + 1, cy + 5, color);
	}

	public static void drawInfoBar(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final int width,
		final Component line1,
		final Component line2,
		final Component line3
	) {
		final int barHeight = line3 == null ? (line2 == null ? 18 : 30) : 42;
		graphics.fill(x, y, x + width, y + barHeight, ARGB.color(210, 16, 20, 30));
		graphics.renderOutline(x, y, width, barHeight, ACCENT_DIM);
		graphics.drawString(font, line1, x + 8, y + 5, ARGB.color(255, 230, 236, 245), false);
		if (line2 != null) {
			graphics.drawString(font, line2, x + 8, y + 17, ARGB.color(255, 190, 200, 215), false);
		}
		if (line3 != null) {
			graphics.drawString(font, line3, x + 8, y + 29, ARGB.color(255, 170, 180, 195), false);
		}
	}

	public static void drawLayerTabHighlight(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final boolean selected
	) {
		if (selected) {
			graphics.fill(x + 2, y + height - 2, x + width - 2, y + height, ACCENT);
		}
	}

	public static int accentColor() {
		return ACCENT;
	}
}
