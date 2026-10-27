package com.gredell.playermap.client.map;

import com.gredell.playermap.network.PlayerPosition;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;

public final class MapMouseActions {
	public interface Host {
		boolean isMapInteraction(final double mouseX, final double mouseY);

		void dismissWaypointMenu();

		void showWaypointMenu(final PlayerPosition player, final double mouseX, final double mouseY);

		void beginDragPan();

		void panByMouseDelta(final double lastMouseX, final double lastMouseY, final double mouseX, final double mouseY);

		void zoomIn();

		void zoomOut();
	}

	private final Host host;
	private boolean dragging;
	private double lastDragMouseX;
	private double lastDragMouseY;

	public MapMouseActions(final Host host) {
		this.host = host;
	}

	public boolean mouseClicked(
		final MouseButtonEvent event,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapWidth,
		final int mapHeight,
		final UUID localUuid
	) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return rightClick(event, dimension, viewCenterX, viewCenterZ, blocksPerPixel, mapWidth, mapHeight, localUuid);
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return leftClick(event);
		}
		return false;
	}

	public boolean mouseReleased(final MouseButtonEvent event) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
			dragging = false;
			return true;
		}
		return false;
	}

	public boolean mouseDragged(final MouseButtonEvent event) {
		if (!dragging) {
			return false;
		}
		host.panByMouseDelta(lastDragMouseX, lastDragMouseY, event.x(), event.y());
		lastDragMouseX = event.x();
		lastDragMouseY = event.y();
		return true;
	}

	public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
		return scroll(mouseX, mouseY, scrollY);
	}

	private boolean rightClick(
		final MouseButtonEvent event,
		final ResourceKey<Level> dimension,
		final double viewCenterX,
		final double viewCenterZ,
		final double blocksPerPixel,
		final int mapWidth,
		final int mapHeight,
		final UUID localUuid
	) {
		if (!host.isMapInteraction(event.x(), event.y())) {
			return false;
		}
		final Optional<PlayerPosition> hit = MapPlayerMarkers.findPlayerAt(
			dimension,
			viewCenterX,
			viewCenterZ,
			blocksPerPixel,
			0,
			0,
			mapWidth,
			mapHeight,
			localUuid,
			event.x(),
			event.y(),
			false
		);
		host.dismissWaypointMenu();
		hit.ifPresent(player -> host.showWaypointMenu(player, event.x(), event.y()));
		return true;
	}

	private boolean leftClick(final MouseButtonEvent event) {
		host.dismissWaypointMenu();
		if (!host.isMapInteraction(event.x(), event.y())) {
			return false;
		}
		dragging = true;
		host.beginDragPan();
		lastDragMouseX = event.x();
		lastDragMouseY = event.y();
		return true;
	}

	private boolean scroll(final double mouseX, final double mouseY, final double scrollY) {
		if (!host.isMapInteraction(mouseX, mouseY)) {
			return false;
		}
		if (scrollY > 0) {
			host.zoomIn();
		} else {
			host.zoomOut();
		}
		return true;
	}
}
