package com.gredell.playermap.client.map;

import com.gredell.playermap.client.PlayersMapClient;
import com.gredell.playermap.PlayersMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class MapScreen extends Screen {
	private static final int MAX_MAP_TEXTURE = 512;
	private static final double MIN_BLOCKS_PER_PIXEL = 0.5;
	private static final double MAX_BLOCKS_PER_PIXEL = 16.0;
	private static final int TAB_WIDTH = 40;
	private static final int TAB_HEIGHT = 18;
	private static final int TAB_SPACING = 4;
	private static final int TAB_INSET = 6;

	private static final TerrainMapRenderer SHARED_TERRAIN_RENDERER =
		new TerrainMapRenderer(PlayersMap.id("terrain_map"), MAX_MAP_TEXTURE);

	private ResourceKey<Level> viewDimension;
	private double viewCenterX;
	private double viewCenterZ;
	private double blocksPerPixel = 2.0;
	private boolean followPlayer = true;
	private boolean dragging;
	private double lastDragMouseX;
	private double lastDragMouseY;

	protected MapScreen(ResourceKey<Level> dimension, double centerX, double centerZ) {
		super(Component.literal("Players Map"));
		this.viewDimension = dimension;
		this.viewCenterX = centerX;
		this.viewCenterZ = centerZ;
	}

	public static void releaseSharedRenderer(net.minecraft.client.Minecraft client) {
		SHARED_TERRAIN_RENDERER.close(client);
	}

	public static MapScreen createForCurrentWorld() {
		var client = net.minecraft.client.Minecraft.getInstance();
		var player = client.player;
		var level = client.level;
		if (player == null || level == null) {
			return new MapScreen(Level.OVERWORLD, 0, 0);
		}
		return new MapScreen(level.dimension(), player.getX(), player.getZ());
	}

	@Override
	protected void init() {
		SHARED_TERRAIN_RENDERER.markDirty();
		addDimensionButtons();
	}

	@Override
	public void removed() {
		// Keep the shared terrain texture so pressing M again shows the last map immediately.
	}

	@Override
	public void tick() {
		if (!followPlayer || minecraft.player == null) {
			return;
		}
		if (!minecraft.player.level().dimension().equals(viewDimension)) {
			return;
		}

		double nextX = minecraft.player.getX();
		double nextZ = minecraft.player.getZ();
		if (nextX != viewCenterX || nextZ != viewCenterZ) {
			viewCenterX = nextX;
			viewCenterZ = nextZ;
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		SHARED_TERRAIN_RENDERER.draw(
			graphics,
			viewDimension,
			viewCenterX,
			viewCenterZ,
			blocksPerPixel,
			0,
			0,
			width,
			height
		);

		MapUiStyles.drawNorthIndicator(graphics, font, 0, 0, width, 8);

		UUID localUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
		float localYaw = minecraft.player != null ? minecraft.player.getYRot() : 0.0F;
		MapPlayerMarkers.render(
			graphics,
			font,
			viewDimension,
			viewCenterX,
			viewCenterZ,
			blocksPerPixel,
			0,
			0,
			width,
			height,
			localUuid,
			localYaw,
			mouseX,
			mouseY,
			true
		);

		super.render(graphics, mouseX, mouseY, delta);
		renderLayerTabHighlights(graphics);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		KeyMapping openMapKey = PlayersMapClient.getOpenMapKey();
		if (openMapKey != null && openMapKey.matches(event)) {
			PlayersMapClient.suppressOpenMapKey();
			minecraft.setScreen(null);
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_F) {
			followPlayer = !followPlayer;
			if (followPlayer) {
				centerOnPlayer();
			}
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_1) {
			setViewDimension(Level.OVERWORLD);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_2) {
			setViewDimension(Level.NETHER);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_3) {
			setViewDimension(Level.END);
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && isMapInteraction(event.x(), event.y())) {
			dragging = true;
			followPlayer = false;
			lastDragMouseX = event.x();
			lastDragMouseY = event.y();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && dragging) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging) {
			viewCenterX -= (event.x() - lastDragMouseX) * blocksPerPixel;
			viewCenterZ -= (event.y() - lastDragMouseY) * blocksPerPixel;
			lastDragMouseX = event.x();
			lastDragMouseY = event.y();
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isMapInteraction(mouseX, mouseY)) {
			double factor = scrollY > 0 ? 0.85 : 1.15;
			blocksPerPixel = MapViewMath.clamp(blocksPerPixel * factor, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
			SHARED_TERRAIN_RENDERER.markDirty();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// Full-screen map replaces the default menu background.
	}

	private void addDimensionButtons() {
		for (int i = 0; i < MapDimensions.LAYERS.size(); i++) {
			ResourceKey<Level> layer = MapDimensions.LAYERS.get(i);
			int hotkey = i + 1;
			int x = TAB_INSET + i * (TAB_WIDTH + TAB_SPACING);
			addRenderableWidget(
				Button.builder(MapDimensions.tabLabel(hotkey, layer), button -> setViewDimension(layer))
					.bounds(x, TAB_INSET, TAB_WIDTH, TAB_HEIGHT)
					.tooltip(Tooltip.create(MapDimensions.label(layer)))
					.build()
			);
		}
	}

	private void renderLayerTabHighlights(GuiGraphics graphics) {
		for (int i = 0; i < MapDimensions.LAYERS.size(); i++) {
			ResourceKey<Level> layer = MapDimensions.LAYERS.get(i);
			int x = TAB_INSET + i * (TAB_WIDTH + TAB_SPACING);
			MapUiStyles.drawLayerTabHighlight(graphics, x, TAB_INSET, TAB_WIDTH, TAB_HEIGHT, layer.equals(viewDimension));
		}
	}

	private void setViewDimension(ResourceKey<Level> dimension) {
		if (viewDimension.equals(dimension)) {
			return;
		}
		viewDimension = dimension;
		SHARED_TERRAIN_RENDERER.markDirty();
		if (minecraft.player != null && minecraft.player.level().dimension().equals(dimension)) {
			centerOnPlayer();
			followPlayer = true;
		} else {
			viewCenterX = 0;
			viewCenterZ = 0;
			followPlayer = false;
		}
	}

	private void centerOnPlayer() {
		if (minecraft.player == null) {
			return;
		}
		viewCenterX = minecraft.player.getX();
		viewCenterZ = minecraft.player.getZ();
		SHARED_TERRAIN_RENDERER.markDirty();
	}

	private boolean isMapInteraction(double mouseX, double mouseY) {
		return mouseY > TAB_INSET + TAB_HEIGHT + 4 || mouseX > TAB_INSET + (TAB_WIDTH + TAB_SPACING) * MapDimensions.LAYERS.size();
	}
}
