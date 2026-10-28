package com.gredell.playermap.client.map;

import com.gredell.playermap.network.PlayerPosition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MapPlayerMarkers {
	private static final int ARROW_RADIUS = 11;
	static final int HOVER_RADIUS = 8;

	private MapPlayerMarkers() {
	}

	public static Optional<PlayerPosition> findPlayerAt(
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapLeft,
		final int mapTop,
		final int mapWidth,
		final int mapHeight,
		final UUID localUuid,
		final double mouseX,
		final double mouseY,
		final boolean includeLocal
	) {
		PlayerPosition closest = null;
		double closestDist = HOVER_RADIUS + 1;

		for (final PlayerPosition player : ClientPlayerMapData.playersInDimension(dimension)) {
			if (!includeLocal && localUuid != null && player.uuid().equals(localUuid)) {
				continue;
			}

			final int screenX = MapViewMath.worldToScreenX(player.x(), viewCenterX, blocksPerPixel, mapLeft, mapWidth);
			final int screenY = MapViewMath.worldToScreenZ(player.z(), viewCenterZ, blocksPerPixel, mapTop, mapHeight);
			if (screenX < mapLeft - 12 || screenX > mapLeft + mapWidth + 12 || screenY < mapTop - 12 || screenY > mapTop + mapHeight + 12) {
				continue;
			}

			final double dist = Math.hypot(mouseX - screenX, mouseY - screenY);
			if (dist <= HOVER_RADIUS && dist < closestDist) {
				closest = player;
				closestDist = dist;
			}
		}

		return Optional.ofNullable(closest);
	}

	public static void render(
		final GuiGraphics graphics,
		final Font font,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapLeft,
		final int mapTop,
		final int mapWidth,
		final int mapHeight,
		final UUID localUuid,
		final float localPlayerYaw,
		final int mouseX,
		final int mouseY,
		final boolean showTooltips
	) {
		final List<PlayerPosition> players = ClientPlayerMapData.playersInDimension(dimension);
		PlayerPosition hovered = null;

		for (final PlayerPosition player : players) {
			final int screenX = MapViewMath.worldToScreenX(player.x(), viewCenterX, blocksPerPixel, mapLeft, mapWidth);
			final int screenY = MapViewMath.worldToScreenZ(player.z(), viewCenterZ, blocksPerPixel, mapTop, mapHeight);
			if (screenX < mapLeft - 12 || screenX > mapLeft + mapWidth + 12 || screenY < mapTop - 12 || screenY > mapTop + mapHeight + 12) {
				continue;
			}

			final boolean isLocal = player.uuid().equals(localUuid);
			if (isLocal) {
				drawLocalPlayerArrow(graphics, screenX, screenY, localPlayerYaw, 1.0F);
			} else {
				drawOtherPlayerMarker(graphics, screenX, screenY);
			}

			if (PlayerWaypoint.targets(player.uuid())) {
				graphics.renderOutline(screenX - 6, screenY - 6, 13, 13, ARGB.color(255, 255, 210, 80));
			}

			if (showTooltips && mouseX >= 0 && mouseY >= 0 && Math.hypot(mouseX - screenX, mouseY - screenY) <= HOVER_RADIUS) {
				hovered = player;
			}
		}

		if (showTooltips && hovered != null) {
			final boolean isLocal = hovered.uuid().equals(localUuid);
			final Component name = Component.literal(hovered.name())
				.withStyle(style -> style.withColor(isLocal ? ChatFormatting.AQUA : ChatFormatting.WHITE));
			final Component nameLine = isLocal
				? name.copy().append(Component.literal(" (you)").withStyle(ChatFormatting.GRAY))
				: name;

			final int x = (int) Math.floor(hovered.x());
			final int y = (int) Math.floor(hovered.y());
			final int z = (int) Math.floor(hovered.z());
			final Component coordLine = Component.literal(String.format("x %d  ·  y %d  ·  z %d", x, y, z))
				.withStyle(ChatFormatting.GRAY);

			graphics.setComponentTooltipForNextFrame(font, List.of(nameLine, coordLine), mouseX, mouseY);
		}
	}

	public static void drawLocalPlayerArrow(
		final GuiGraphics graphics,
		final int centerX,
		final int centerY,
		final float yawDegrees,
		final float scale
	) {
		final double rad = Math.toRadians(yawDegrees);
		final double cos = Math.cos(rad);
		final double sin = Math.sin(rad);
		final int radius = (int) Math.ceil(ARROW_RADIUS * scale);
		final int shadow = ARGB.color(160, 0, 0, 0);
		final int fill = ARGB.color(255, 90, 220, 255);
		final int outline = ARGB.color(255, 255, 255, 255);

		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				final double localX = dx * cos + dy * sin;
				final double localY = -dx * sin + dy * cos;
				if (isInsideArrow(localX, localY, scale)) {
					graphics.fill(centerX + dx + 1, centerY + dy + 1, centerX + dx + 2, centerY + dy + 2, shadow);
				}
			}
		}

		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				final double localX = dx * cos + dy * sin;
				final double localY = -dx * sin + dy * cos;
				if (!isInsideArrow(localX, localY, scale)) {
					continue;
				}

				final boolean edge = !isInsideArrow(localX + 1, localY, scale)
					|| !isInsideArrow(localX - 1, localY, scale)
					|| !isInsideArrow(localX, localY + 1, scale)
					|| !isInsideArrow(localX, localY - 1, scale);
				graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, edge ? outline : fill);
			}
		}
	}

	private static boolean isInsideArrow(final double localX, final double localY, final float scale) {
		// Tip at +localY (map +Z / south at yaw 0); base opposite the heading.
		return pointInTriangle(localX / scale, localY / scale, 0, 9, -5, -5, 5, -5);
	}

	private static boolean pointInTriangle(
		final double px,
		final double py,
		final double x1,
		final double y1,
		final double x2,
		final double y2,
		final double x3,
		final double y3
	) {
		final double d1 = sign(px, py, x1, y1, x2, y2);
		final double d2 = sign(px, py, x2, y2, x3, y3);
		final double d3 = sign(px, py, x3, y3, x1, y1);
		final boolean hasNegative = d1 < 0 || d2 < 0 || d3 < 0;
		final boolean hasPositive = d1 > 0 || d2 > 0 || d3 > 0;
		return !(hasNegative && hasPositive);
	}

	private static double sign(
		final double px,
		final double py,
		final double ax,
		final double ay,
		final double bx,
		final double by
	) {
		return (px - bx) * (ay - by) - (ax - bx) * (py - by);
	}

	private static void drawOtherPlayerMarker(final GuiGraphics graphics, final int x, final int y) {
		graphics.fill(x - 4, y - 4, x + 5, y + 5, ARGB.color(60, 0, 0, 0));
		graphics.fill(x - 3, y - 3, x + 4, y + 4, ARGB.color(255, 255, 120, 110));
		graphics.renderOutline(x - 3, y - 3, 7, 7, ARGB.color(255, 255, 255, 230));
	}
}
