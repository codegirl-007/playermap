package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;
import com.gredell.playermap.network.PlayerPosition;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class WaypointHudOverlay {
	private static final int ARROW_OFFSET_FROM_BOTTOM = 52;
	private static final int MIN_ARROW_DISTANCE_BLOCKS = 8;

	private WaypointHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			PlayersMap.id("waypoint"),
			WaypointHudOverlay::render
		);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (!PlayerWaypoint.isActive() || client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		if (client.screen instanceof MapScreen) {
			return;
		}

		var resolved = PlayerWaypoint.resolve();
		int centerX = graphics.guiWidth() / 2;
		int baseY = graphics.guiHeight() - ARROW_OFFSET_FROM_BOTTOM;

		if (resolved.isEmpty()) {
			drawLabel(graphics, client, centerX, baseY, Component.translatable("gui.playersmap.waypoint.unavailable"));
			return;
		}

		PlayerPosition target = resolved.get();
		Level level = client.level;
		String name = target.name();

		if (!target.dimension().equals(level.dimension())) {
			Component dimensionName = MapDimensions.label(target.dimension());
			drawLabel(
				graphics,
				client,
				centerX,
				baseY,
				Component.translatable("gui.playersmap.waypoint.wrong_dimension", name, dimensionName)
			);
			return;
		}

		double dx = target.x() - client.player.getX();
		double dz = target.z() - client.player.getZ();
		int distanceBlocks = (int) Math.round(Math.hypot(dx, dz));

		drawLabel(
			graphics,
			client,
			centerX,
			baseY + 14,
			Component.translatable("gui.playersmap.waypoint.hud", name, distanceBlocks)
		);

		if (distanceBlocks >= MIN_ARROW_DISTANCE_BLOCKS) {
			float worldYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float relativeYaw = worldYaw - client.player.getYRot();
			MapPlayerMarkers.drawLocalPlayerArrow(graphics, centerX, baseY - 12, relativeYaw);
		}
	}

	private static void drawLabel(GuiGraphics graphics, Minecraft client, int centerX, int y, Component text) {
		int width = client.font.width(text);
		int x = centerX - width / 2;
		graphics.drawString(client.font, text, x + 1, y + 1, 0x80000000, false);
		graphics.drawString(client.font, text, x, y, 0xFFFFFF, false);
	}
}
