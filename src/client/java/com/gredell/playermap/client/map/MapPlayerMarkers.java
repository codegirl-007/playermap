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
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int mapLeft,
		int mapTop,
		int mapWidth,
		int mapHeight,
		UUID localUuid,
		double mouseX,
		double mouseY,
		boolean includeLocal
	) {
		PlayerPosition closest = null;
		double closestDist = HOVER_RADIUS + 1;

		for (PlayerPosition player : ClientPlayerMapData.playersInDimension(dimension)) {
			if (!includeLocal && localUuid != null && player.uuid().equals(localUuid)) {
				continue;
			}

			int screenX = MapViewMath.worldToScreenX(player.x(), viewCenterX, blocksPerPixel, mapLeft, mapWidth);
			int screenY = MapViewMath.worldToScreenZ(player.z(), viewCenterZ, blocksPerPixel, mapTop, mapHeight);
			if (screenX < mapLeft - 12 || screenX > mapLeft + mapWidth + 12 || screenY < mapTop - 12 || screenY > mapTop + mapHeight + 12) {
				continue;
			}

			double dist = Math.hypot(mouseX - screenX, mouseY - screenY);
			if (dist <= HOVER_RADIUS && dist < closestDist) {
				closest = player;
				closestDist = dist;
			}
		}

		return Optional.ofNullable(closest);
	}

	public static void render(
		GuiGraphics graphics,
		Font font,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int mapLeft,
		int mapTop,
		int mapWidth,
		int mapHeight,
		UUID localUuid,
		float localPlayerYaw,
		int mouseX,
		int mouseY,
		boolean showTooltips
	) {
		List<PlayerPosition> players = ClientPlayerMapData.playersInDimension(dimension);
		PlayerPosition hovered = null;

		for (PlayerPosition player : players) {
			int screenX = MapViewMath.worldToScreenX(player.x(), viewCenterX, blocksPerPixel, mapLeft, mapWidth);
			int screenY = MapViewMath.worldToScreenZ(player.z(), viewCenterZ, blocksPerPixel, mapTop, mapHeight);
			if (screenX < mapLeft - 12 || screenX > mapLeft + mapWidth + 12 || screenY < mapTop - 12 || screenY > mapTop + mapHeight + 12) {
				continue;
			}

			boolean isLocal = player.uuid().equals(localUuid);
			if (isLocal) {
				drawLocalPlayerArrow(graphics, screenX, screenY, localPlayerYaw);
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
			boolean isLocal = hovered.uuid().equals(localUuid);
			Component nameLine = Component.literal(hovered.name())
				.withStyle(style -> style.withColor(isLocal ? ChatFormatting.AQUA : ChatFormatting.WHITE));
			if (isLocal) {
				nameLine = nameLine.copy().append(Component.literal(" (you)").withStyle(ChatFormatting.GRAY));
			}

			int x = (int) Math.floor(hovered.x());
			int y = (int) Math.floor(hovered.y());
			int z = (int) Math.floor(hovered.z());
			Component coordLine = Component.literal(String.format("x %d  ·  y %d  ·  z %d", x, y, z))
				.withStyle(ChatFormatting.GRAY);

			graphics.setComponentTooltipForNextFrame(font, List.of(nameLine, coordLine), mouseX, mouseY);
		}
	}

	public static void drawLocalPlayerArrow(GuiGraphics graphics, int centerX, int centerY, float yawDegrees) {
		double rad = Math.toRadians(yawDegrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		int shadow = ARGB.color(160, 0, 0, 0);
		int fill = ARGB.color(255, 90, 220, 255);
		int outline = ARGB.color(255, 255, 255, 255);

		for (int dy = -ARROW_RADIUS; dy <= ARROW_RADIUS; dy++) {
			for (int dx = -ARROW_RADIUS; dx <= ARROW_RADIUS; dx++) {
				double localX = dx * cos + dy * sin;
				double localY = -dx * sin + dy * cos;
				if (isInsideArrow(localX, localY)) {
					graphics.fill(centerX + dx + 1, centerY + dy + 1, centerX + dx + 2, centerY + dy + 2, shadow);
				}
			}
		}

		for (int dy = -ARROW_RADIUS; dy <= ARROW_RADIUS; dy++) {
			for (int dx = -ARROW_RADIUS; dx <= ARROW_RADIUS; dx++) {
				double localX = dx * cos + dy * sin;
				double localY = -dx * sin + dy * cos;
				if (!isInsideArrow(localX, localY)) {
					continue;
				}

				boolean edge = !isInsideArrow(localX + 1, localY)
					|| !isInsideArrow(localX - 1, localY)
					|| !isInsideArrow(localX, localY + 1)
					|| !isInsideArrow(localX, localY - 1);
				graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, edge ? outline : fill);
			}
		}
	}

	private static boolean isInsideArrow(double localX, double localY) {
		// Tip at +localY (map +Z / south at yaw 0); base opposite the heading.
		return pointInTriangle(localX, localY, 0, 9, -5, -5, 5, -5);
	}

	private static boolean pointInTriangle(
		double px,
		double py,
		double x1,
		double y1,
		double x2,
		double y2,
		double x3,
		double y3
	) {
		double d1 = sign(px, py, x1, y1, x2, y2);
		double d2 = sign(px, py, x2, y2, x3, y3);
		double d3 = sign(px, py, x3, y3, x1, y1);
		boolean hasNegative = d1 < 0 || d2 < 0 || d3 < 0;
		boolean hasPositive = d1 > 0 || d2 > 0 || d3 > 0;
		return !(hasNegative && hasPositive);
	}

	private static double sign(double px, double py, double ax, double ay, double bx, double by) {
		return (px - bx) * (ay - by) - (ax - bx) * (py - by);
	}

	private static void drawOtherPlayerMarker(GuiGraphics graphics, int x, int y) {
		graphics.fill(x - 4, y - 4, x + 5, y + 5, ARGB.color(60, 0, 0, 0));
		graphics.fill(x - 3, y - 3, x + 4, y + 4, ARGB.color(255, 255, 120, 110));
		graphics.renderOutline(x - 3, y - 3, 7, 7, ARGB.color(255, 255, 255, 230));
	}
}
