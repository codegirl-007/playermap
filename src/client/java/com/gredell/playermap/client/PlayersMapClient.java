package com.gredell.playermap.client;

import com.gredell.playermap.client.map.ChunkSurfaceColors;
import com.gredell.playermap.client.map.ClientPlayerMapData;
import com.gredell.playermap.client.map.MapScreen;
import com.gredell.playermap.client.map.MinimapOverlay;
import com.gredell.playermap.client.map.PlayerWaypoint;
import com.gredell.playermap.client.map.WaypointHudOverlay;
import com.gredell.playermap.network.PlayerMapNetworking;
import com.gredell.playermap.network.PlayerPositionsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class PlayersMapClient implements ClientModInitializer {
	private static KeyMapping openMapKey;
	private static KeyMapping toggleMinimapKey;
	private static KeyMapping clearWaypointKey;
	private static int suppressOpenMapTicks;

	public static KeyMapping getOpenMapKey() {
		return openMapKey;
	}

	public static void suppressOpenMapKey() {
		suppressOpenMapTicks = 2;
	}

	@Override
	public void onInitializeClient() {
		PlayerMapNetworking.registerPayloads();
		MinimapOverlay.register();
		WaypointHudOverlay.register();

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE, (payload, context) -> {
			ClientPlayerMapData.updateFromNetwork(payload.players());
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPlayerMapData.clear();
			PlayerWaypoint.clear();
			ChunkSurfaceColors.clear();
			MapScreen.releaseSharedRenderer(client);
		});

		// register the keybindings
		openMapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.open_map",
				GLFW.GLFW_KEY_M,
				KeyMapping.Category.MISC
			)
		);

		toggleMinimapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.toggle_minimap", // this is an i18n key, refer to en_us.json for strings
				GLFW.GLFW_KEY_COMMA,
				KeyMapping.Category.MISC
			)
		);

		clearWaypointKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.clear_waypoint",
				GLFW.GLFW_KEY_U,
				KeyMapping.Category.MISC
			)
		);

		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			// a column just arrived, call capture to update the colors
			ChunkSurfaceColors.capture(chunk);

			// tell the minimap that the terrain has changed and redraw minimap
			MinimapOverlay.notifyTerrainChanged();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (suppressOpenMapTicks > 0) {
				suppressOpenMapTicks--;
			}

			while (toggleMinimapKey.consumeClick()) {
				MinimapOverlay.toggleEnabled();
			}
			while (clearWaypointKey.consumeClick()) {
				PlayerWaypoint.clear();
			}
			// check if the open map key is pressed
			while (openMapKey.consumeClick()) {
				// if you're on the title screen or in a menu, don't open the map
				if (suppressOpenMapTicks > 0 || client.player == null || client.level == null) {
					continue;
				}
				// if nothing else is open, open the map
				if (client.screen == null) {
					client.setScreen(MapScreen.createForCurrentWorld());
				}
			}
		});
	}
}
