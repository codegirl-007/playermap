package com.gredell.playermap.client.map;

import com.gredell.playermap.network.PlayerPosition;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public final class MapMouseActions {
	interface Host {
		boolean isMapInteraction(double mouseX, double mouseY);

		void dismissWaypointMenu();

		void showWaypointMenu(PlayerPosition player, double mouseX, double mouseY);

		void beginDragPan();

		void panByMouseDelta(double lastMouseX, double lastMouseY, double mouseX, double mouseY);

		void zoomIn();

		void zoomOut();
	}

	private final Host host;
	private boolean dragging;
	private double lastDragMouseX;
	private double lastDragMouseY;

	MapMouseActions(Host host) {
		this.host = host;
	}

	boolean mouseClicked(
		MouseButtonEvent event,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int mapWidth,
		int mapHeight,
		UUID localUuid
	) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return rightClick(event, dimension, viewCenterX, viewCenterZ, blocksPerPixel, mapWidth, mapHeight, localUuid);
		}
		if (event.button() == 0) {
			return leftClick(event);
		}
		return false;
	}

	boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && dragging) {
			dragging = false;
			return true;
		}
		return false;
	}

	boolean mouseDragged(MouseButtonEvent event) {
		if (!dragging) {
			return false;
		}
		host.panByMouseDelta(lastDragMouseX, lastDragMouseY, event.x(), event.y());
		lastDragMouseX = event.x();
		lastDragMouseY = event.y();
		return true;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return scroll(mouseX, mouseY, scrollY);
	}

	private boolean rightClick(
		MouseButtonEvent event,
		ResourceKey<Level> dimension,
		double viewCenterX,
		double viewCenterZ,
		double blocksPerPixel,
		int mapWidth,
		int mapHeight,
		UUID localUuid
	) {
		if (!host.isMapInteraction(event.x(), event.y())) {
			return false;
		}
		var hit = MapPlayerMarkers.findPlayerAt(
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

	private boolean leftClick(MouseButtonEvent event) {
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

	private boolean scroll(double mouseX, double mouseY, double scrollY) {
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
