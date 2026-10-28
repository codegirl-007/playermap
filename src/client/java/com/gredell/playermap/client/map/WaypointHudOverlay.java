package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;
import com.gredell.playermap.network.PlayerPosition;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class WaypointHudOverlay {
	private static final int ARROW_OFFSET_FROM_TOP = 60;
	private static final int HUD_LABEL_OFFSET = 28;
	private static final float HUD_ARROW_SCALE = 2.0F;
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

	private static void render(final GuiGraphics graphics, final DeltaTracker tickCounter) {
		final Minecraft client = Minecraft.getInstance();
		if (!PlayerWaypoint.isActive() || client.player == null || client.level == null || client.options.hideGui) {
			return;
		}
		if (client.screen instanceof MapScreen) {
			return;
		}

		final Optional<PlayerPosition> resolved = PlayerWaypoint.resolve();
		final int centerX = graphics.guiWidth() / 2;
		final int arrowY = ARROW_OFFSET_FROM_TOP;
		final int labelY = arrowY + HUD_LABEL_OFFSET;

		if (resolved.isEmpty()) {
			drawLabel(graphics, client, centerX, labelY, Component.translatable("gui.playersmap.waypoint.unavailable"));
			return;
		}

		final PlayerPosition target = resolved.get();
		final Level level = client.level;
		final String name = target.name();

		if (!target.dimension().equals(level.dimension())) {
			final Component dimensionName = MapDimensions.label(target.dimension());
			drawLabel(
				graphics,
				client,
				centerX,
				labelY,
				Component.translatable("gui.playersmap.waypoint.wrong_dimension", name, dimensionName)
			);
			return;
		}

		final double dx = target.x() - client.player.getX();
		final double dz = target.z() - client.player.getZ();
		final int distanceBlocks = (int) Math.round(Math.hypot(dx, dz));

		drawLabel(
			graphics,
			client,
			centerX,
			labelY,
			Component.translatable("gui.playersmap.waypoint.hud", name, distanceBlocks)
		);

		if (distanceBlocks >= MIN_ARROW_DISTANCE_BLOCKS) {
			final float worldYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			final float relativeYaw = 180.0F - (worldYaw - client.player.getYRot());
			MapPlayerMarkers.drawLocalPlayerArrow(graphics, centerX, arrowY, relativeYaw, HUD_ARROW_SCALE);
		}
	}

	private static void drawLabel(
		final GuiGraphics graphics,
		final Minecraft client,
		final int centerX,
		final int y,
		final Component text
	) {
		final int width = client.font.width(text);
		final int x = centerX - width / 2;
		graphics.drawString(client.font, text, x + 1, y + 1, 0x80000000, false);
		graphics.drawString(client.font, text, x, y, 0xFFFFFF, false);
	}
}
