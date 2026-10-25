package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class MinimapOverlay {
	private static final int MINIMAP_SIZE = 104;
	private static final int MARGIN = 10;
	private static final double BLOCKS_PER_PIXEL = 5.0;
	private static final int UPDATE_INTERVAL_TICKS = 4;

	private static final TerrainMapRenderer renderer =
		new TerrainMapRenderer(PlayersMap.id("minimap"), MINIMAP_SIZE);

	private static boolean enabled = true;
	private static int ticksUntilUpdate;

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

		if (ticksUntilUpdate <= 0) {
			renderer.markDirty();
			ticksUntilUpdate = UPDATE_INTERVAL_TICKS;
		} else {
			ticksUntilUpdate--;
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
	}
}
