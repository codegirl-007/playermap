package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class MinimapOverlay {
	private static final int MINIMAP_SIZE = 104;
	private static final int MARGIN = 10;
	private static final double BLOCKS_PER_PIXEL = 5.0;
	private static final int IDLE_REFRESH_TICKS = 60;

	private static final TerrainMapRenderer renderer =
		new TerrainMapRenderer(PlayersMap.id("minimap"), MINIMAP_SIZE);

	private static boolean enabled = true;
	private static double lastRasterCenterX = Double.NaN;
	private static double lastRasterCenterZ = Double.NaN;
	private static ResourceKey<Level> lastRasterDimension;
	private static int ticksSinceRaster;

	private MinimapOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, PlayersMap.id("minimap"), MinimapOverlay::render);
	}

	public static void toggleEnabled() {
		enabled = !enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void notifyTerrainChanged() {
		renderer.markDirty();
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled || client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		if (client.screen instanceof MapScreen) {
			return;
		}

		int left = graphics.guiWidth() - MINIMAP_SIZE - MARGIN;
		int top = MARGIN;
		var dimension = client.level.dimension();
		double centerX = client.player.getX();
		double centerZ = client.player.getZ();

		ticksSinceRaster++;
		boolean firstRaster = Double.isNaN(lastRasterCenterX);
		boolean dimensionChanged = lastRasterDimension != null && !lastRasterDimension.equals(dimension);
		boolean moved = !firstRaster
			&& Math.hypot(centerX - lastRasterCenterX, centerZ - lastRasterCenterZ) >= 0.01;
		boolean idleRefresh = ticksSinceRaster >= IDLE_REFRESH_TICKS;

		if (firstRaster || dimensionChanged || idleRefresh) {
			renderer.markDirty();
		}
		if (moved) {
			ticksSinceRaster = 0;
		}
		if (firstRaster || dimensionChanged || moved || idleRefresh) {
			lastRasterCenterX = centerX;
			lastRasterCenterZ = centerZ;
			lastRasterDimension = dimension;
			if (idleRefresh) {
				ticksSinceRaster = 0;
			}
		}

		MapUiStyles.drawMinimapChrome(graphics, client.font, left, top, MINIMAP_SIZE, dimension);

		renderer.draw(graphics, dimension, centerX, centerZ, BLOCKS_PER_PIXEL, left, top, MINIMAP_SIZE, MINIMAP_SIZE);

		MapPlayerMarkers.render(
			graphics,
			client.font,
			dimension,
			centerX,
			centerZ,
			BLOCKS_PER_PIXEL,
			left,
			top,
			MINIMAP_SIZE,
			MINIMAP_SIZE,
			client.player.getUUID(),
			client.player.getYRot(),
			-1,
			-1,
			false
		);

		int frameInset = MapUiStyles.minimapFrameOuterInset();
		int frameLeft = left - frameInset;
		int frameWidth = MINIMAP_SIZE + frameInset * 2;
		int coordsTop = top + MINIMAP_SIZE + frameInset + 2;
		MapUiStyles.drawMinimapCoords(
			graphics,
			client.font,
			frameLeft,
			coordsTop,
			frameWidth,
			(int) Math.floor(client.player.getX()),
			(int) Math.floor(client.player.getY()),
			(int) Math.floor(client.player.getZ())
		);
	}
}
