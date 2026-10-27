package com.gredell.playermap.client.map;

import com.gredell.playermap.client.keybindings.map.OpenMapKey;
import com.gredell.playermap.PlayersMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import com.gredell.playermap.network.PlayerPosition;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MapScreen extends Screen implements MapMouseActions.Host {
	private static final int MAX_MAP_TEXTURE = 512;
	private static final double DEFAULT_BLOCKS_PER_PIXEL = 2.0;
	private static final double MIN_BLOCKS_PER_PIXEL = 0.5;
	private static final double MAX_BLOCKS_PER_PIXEL = 16.0;
	private static final double ZOOM_IN_FACTOR = 0.85;
	private static final double ZOOM_OUT_FACTOR = 1.15;
	private static final int ZOOM_BUTTON_SIZE = 20;
	private static final int ZOOM_RESET_WIDTH = 44;
	private static final int ZOOM_BUTTON_GAP = 4;
	private static final int ZOOM_INSET = 6;
	private static final int TAB_WIDTH = 40;
	private static final int TAB_HEIGHT = 18;
	private static final int TAB_SPACING = 4;
	private static final int TAB_INSET = 6;
	private static final int WAYPOINT_MENU_WIDTH = 148;
	private static final int WAYPOINT_MENU_HEIGHT = 20;
	private static final int WAYPOINT_MENU_GAP = 2;

	private static final TerrainMapRenderer SHARED_TERRAIN_RENDERER =
		new TerrainMapRenderer(PlayersMap.id("terrain_map"), MAX_MAP_TEXTURE);

	private ResourceKey<Level> viewDimension;
	private double viewCenterX;
	private double viewCenterZ;
	private double blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
	private boolean followPlayer = true;
	private Button zoomInButton;
	private Button zoomOutButton;
	private final MapMouseActions mouseActions = new MapMouseActions(this);
	private final List<Button> waypointContextButtons = new ArrayList<>();

	protected MapScreen(final ResourceKey<Level> dimension, final double centerX, final double centerZ) {
		super(Component.literal("Players Map"));
		this.viewDimension = dimension;
		this.viewCenterX = centerX;
		this.viewCenterZ = centerZ;
	}

	public static void releaseSharedRenderer(final Minecraft client) {
		SHARED_TERRAIN_RENDERER.close(client);
	}

	public static MapScreen createForCurrentWorld() {
		final Minecraft client = Minecraft.getInstance();
		final LocalPlayer player = client.player;
		final ClientLevel level = client.level;
		if (player == null || level == null) {
			return new MapScreen(Level.OVERWORLD, 0, 0);
		}
		return new MapScreen(level.dimension(), player.getX(), player.getZ());
	}

	@Override
	protected void init() {
		waypointContextButtons.clear();
		ensureViewDimensionAvailable();
		addDimensionButtons();
		addZoomButtons();
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

		final double nextX = minecraft.player.getX();
		final double nextZ = minecraft.player.getZ();
		if (nextX != viewCenterX || nextZ != viewCenterZ) {
			viewCenterX = nextX;
			viewCenterZ = nextZ;
		}
	}

	@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
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

		final UUID localUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
		final float localYaw = minecraft.player != null ? minecraft.player.getYRot() : 0.0F;
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
		if (MapDimensions.availableLayers(minecraft).size() > 1) {
			renderLayerTabHighlights(graphics);
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		final boolean escapePressed = event.key() == GLFW.GLFW_KEY_ESCAPE;
		final boolean waypointMenuOpen = !waypointContextButtons.isEmpty();
		if (escapePressed && waypointMenuOpen) {
			dismissWaypointMenu();
			return true;
		}

		final KeyMapping openMapKey = OpenMapKey.getKey();
		if (openMapKey != null && openMapKey.matches(event)) {
			OpenMapKey.suppressOpen();
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

		for (final ResourceKey<Level> layer : MapDimensions.availableLayers(minecraft)) {
			final int hotkey = MapDimensions.hotkeyForDimension(layer);
			if (hotkey >= 1 && hotkey <= 3 && event.key() == GLFW.GLFW_KEY_1 + hotkey - 1) {
				setViewDimension(layer);
				return true;
			}
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		final UUID localUuid = minecraft.player != null ? minecraft.player.getUUID() : null;
		return mouseActions.mouseClicked(
			event,
			viewDimension,
			viewCenterX,
			viewCenterZ,
			blocksPerPixel,
			width,
			height,
			localUuid
		);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (mouseActions.mouseReleased(event)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY) {
		if (mouseActions.mouseDragged(event)) {
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
		if (mouseActions.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
		// Full-screen map replaces the default menu background.
	}

	private void addDimensionButtons() {
		final List<ResourceKey<Level>> layers = MapDimensions.availableLayers(minecraft);
		if (layers.size() <= 1) {
			return;
		}
		for (int i = 0; i < layers.size(); i++) {
			final ResourceKey<Level> layer = layers.get(i);
			final int hotkey = MapDimensions.hotkeyForDimension(layer);
			final int x = TAB_INSET + i * (TAB_WIDTH + TAB_SPACING);
			addRenderableWidget(
				Button.builder(MapDimensions.tabLabel(hotkey, layer), button -> setViewDimension(layer))
					.bounds(x, TAB_INSET, TAB_WIDTH, TAB_HEIGHT)
					.tooltip(Tooltip.create(MapDimensions.label(layer)))
					.build()
			);
		}
	}

	private void renderLayerTabHighlights(final GuiGraphics graphics) {
		final List<ResourceKey<Level>> layers = MapDimensions.availableLayers(minecraft);
		for (int i = 0; i < layers.size(); i++) {
			final ResourceKey<Level> layer = layers.get(i);
			final int x = TAB_INSET + i * (TAB_WIDTH + TAB_SPACING);
			MapUiStyles.drawLayerTabHighlight(graphics, x, TAB_INSET, TAB_WIDTH, TAB_HEIGHT, layer.equals(viewDimension));
		}
	}

	private void setViewDimension(final ResourceKey<Level> dimension) {
		dismissWaypointMenu();
		if (viewDimension.equals(dimension)) {
			return;
		}
		if (!MapDimensions.isMapAvailable(dimension, minecraft.level)) {
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

	private void ensureViewDimensionAvailable() {
		if (MapDimensions.isMapAvailable(viewDimension, minecraft.level)) {
			return;
		}
		if (minecraft.player != null && MapDimensions.isMapAvailable(minecraft.player.level().dimension(), minecraft.level)) {
			viewDimension = minecraft.player.level().dimension();
		} else {
			viewDimension = Level.OVERWORLD;
		}
		SHARED_TERRAIN_RENDERER.markDirty();
	}

	private void centerOnPlayer() {
		if (minecraft.player == null) {
			return;
		}
		viewCenterX = minecraft.player.getX();
		viewCenterZ = minecraft.player.getZ();
		SHARED_TERRAIN_RENDERER.markDirty();
	}

	private void addZoomButtons() {
		final int columnWidth = Math.max(ZOOM_BUTTON_SIZE, ZOOM_RESET_WIDTH);
		final int left = width - ZOOM_INSET - columnWidth;
		final int resetTop = height - ZOOM_INSET - ZOOM_BUTTON_SIZE;
		final int minusTop = resetTop - ZOOM_BUTTON_GAP - ZOOM_BUTTON_SIZE;
		final int plusTop = minusTop - ZOOM_BUTTON_GAP - ZOOM_BUTTON_SIZE;

		zoomInButton = Button.builder(Component.literal("+"), (final Button button) -> adjustZoom(ZOOM_IN_FACTOR))
			.bounds(left + (columnWidth - ZOOM_BUTTON_SIZE) / 2, plusTop, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE)
			.tooltip(Tooltip.create(Component.translatable("gui.playersmap.zoom.in")))
			.build();
		zoomOutButton = Button.builder(Component.literal("-"), (final Button button) -> adjustZoom(ZOOM_OUT_FACTOR))
			.bounds(left + (columnWidth - ZOOM_BUTTON_SIZE) / 2, minusTop, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE)
			.tooltip(Tooltip.create(Component.translatable("gui.playersmap.zoom.out")))
			.build();
		final Button resetButton = Button.builder(Component.translatable("gui.playersmap.zoom.reset"), (final Button button) -> resetZoom())
			.bounds(left + (columnWidth - ZOOM_RESET_WIDTH) / 2, resetTop, ZOOM_RESET_WIDTH, ZOOM_BUTTON_SIZE)
			.tooltip(Tooltip.create(Component.translatable("gui.playersmap.zoom.reset.tooltip")))
			.build();

		addRenderableWidget(zoomInButton);
		addRenderableWidget(zoomOutButton);
		addRenderableWidget(resetButton);
		updateZoomButtonStates();
	}

	@Override
	public boolean isMapInteraction(final double mouseX, final double mouseY) {
		final int tabCount = MapDimensions.availableLayers(minecraft).size();
		final int tabStripBottom = TAB_INSET + TAB_HEIGHT + 4;
		final int tabStripRight = TAB_INSET + (TAB_WIDTH + TAB_SPACING) * tabCount;
		final boolean belowTabs = mouseY > tabStripBottom;
		final boolean rightOfTabs = mouseX > tabStripRight;
		if (isOverZoomControls(mouseX, mouseY)) {
			return false;
		}
		if (tabCount <= 1) {
			return true;
		}
		return belowTabs || rightOfTabs;
	}

	@Override
	public void beginDragPan() {
		followPlayer = false;
	}

	@Override
	public void panByMouseDelta(
		final double lastMouseX,
		final double lastMouseY,
		final double mouseX,
		final double mouseY
	) {
		viewCenterX -= (mouseX - lastMouseX) * blocksPerPixel;
		viewCenterZ -= (mouseY - lastMouseY) * blocksPerPixel;
	}

	@Override
	public void zoomIn() {
		adjustZoom(ZOOM_IN_FACTOR);
	}

	@Override
	public void zoomOut() {
		adjustZoom(ZOOM_OUT_FACTOR);
	}

	private void adjustZoom(final double factor) {
		final double next = MapViewMath.clamp(blocksPerPixel * factor, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
		if (Double.compare(next, blocksPerPixel) == 0) {
			return;
		}
		blocksPerPixel = next;
		SHARED_TERRAIN_RENDERER.markDirty();
		updateZoomButtonStates();
	}

	private void resetZoom() {
		if (Double.compare(blocksPerPixel, DEFAULT_BLOCKS_PER_PIXEL) == 0) {
			return;
		}
		blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
		SHARED_TERRAIN_RENDERER.markDirty();
		updateZoomButtonStates();
	}

	private void updateZoomButtonStates() {
		if (zoomInButton != null) {
			zoomInButton.active = blocksPerPixel > MIN_BLOCKS_PER_PIXEL;
		}
		if (zoomOutButton != null) {
			zoomOutButton.active = blocksPerPixel < MAX_BLOCKS_PER_PIXEL;
		}
	}

	private boolean isOverZoomControls(final double mouseX, final double mouseY) {
		final int columnWidth = Math.max(ZOOM_BUTTON_SIZE, ZOOM_RESET_WIDTH);
		final int left = width - ZOOM_INSET - columnWidth;
		final int top = height - ZOOM_INSET - ZOOM_BUTTON_SIZE * 3 - ZOOM_BUTTON_GAP * 2;
		final int right = width - ZOOM_INSET;
		final int bottom = height - ZOOM_INSET;
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
	}

	@Override
	public void dismissWaypointMenu() {
		for (final Button button : waypointContextButtons) {
			removeWidget(button);
		}
		waypointContextButtons.clear();
	}

	@Override
	public void showWaypointMenu(final PlayerPosition player, final double mouseX, final double mouseY) {
		final int x = Mth.clamp((int) mouseX, 4, width - WAYPOINT_MENU_WIDTH - 4);
		final int y = Mth.clamp((int) mouseY, TAB_INSET + TAB_HEIGHT + 4, height - WAYPOINT_MENU_HEIGHT - 4);
		int row = 0;

		if (PlayerWaypoint.targets(player.uuid())) {
			addWaypointMenuButton(
				Component.translatable("gui.playersmap.waypoint.clear"),
				x,
				y,
				() -> PlayerWaypoint.clear()
			);
			return;
		}

		addWaypointMenuButton(
			Component.translatable("gui.playersmap.waypoint.set", player.name()),
			x,
			y + row * (WAYPOINT_MENU_HEIGHT + WAYPOINT_MENU_GAP),
			() -> PlayerWaypoint.set(player)
		);
		row++;

		if (PlayerWaypoint.isActive()) {
			addWaypointMenuButton(
				Component.translatable("gui.playersmap.waypoint.clear"),
				x,
				y + row * (WAYPOINT_MENU_HEIGHT + WAYPOINT_MENU_GAP),
				PlayerWaypoint::clear
			);
		}
	}

	private void addWaypointMenuButton(final Component label, final int x, final int y, final Runnable action) {
		final Button button = Button.builder(label, (final Button ignored) -> {
			action.run();
			dismissWaypointMenu();
		}).bounds(x, y, WAYPOINT_MENU_WIDTH, WAYPOINT_MENU_HEIGHT).build();
		addRenderableWidget(button);
		waypointContextButtons.add(button);
	}
}
