package com.gredell.playermap.client;

import com.gredell.playermap.client.map.ChunkSurfaceColors;
import com.gredell.playermap.client.map.ClientPlayerMapData;
import com.gredell.playermap.client.map.ExploredMapStorage;
import com.gredell.playermap.client.map.MapScreen;
import com.gredell.playermap.client.map.MinimapOverlay;
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
	private static int suppressOpenMapTicks;

	public static KeyMapping getOpenMapKey() {
		return openMapKey;
	}

	public static void suppressOpenMapKey() {
		suppressOpenMapTicks = 2;
	}

	@Override
	public void onInitializeClient() {
		ExploredMapStorage.load();
		PlayerMapNetworking.registerPayloads();
		MinimapOverlay.register();

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE, (payload, context) -> {
			ClientPlayerMapData.updateFromNetwork(payload.players());
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPlayerMapData.clear();
			ChunkSurfaceColors.clear();
			ExploredMapStorage.saveNow();
		});

		openMapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.open_map",
				GLFW.GLFW_KEY_M,
				KeyMapping.Category.MISC
			)
		);

		toggleMinimapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.toggle_minimap",
				GLFW.GLFW_KEY_COMMA,
				KeyMapping.Category.MISC
			)
		);

		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			ExploredMapStorage.markExplored(world.dimension(), chunk.getPos());
			ChunkSurfaceColors.capture(chunk);
			MinimapOverlay.notifyTerrainChanged();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (suppressOpenMapTicks > 0) {
				suppressOpenMapTicks--;
			}

			if (client.player != null && client.level != null) {
				ExploredMapStorage.markExplored(client.level.dimension(), client.player.chunkPosition());
			}

			ExploredMapStorage.saveIfNeeded();

			while (toggleMinimapKey.consumeClick()) {
				MinimapOverlay.toggleEnabled();
			}

			while (openMapKey.consumeClick()) {
				if (suppressOpenMapTicks > 0 || client.player == null || client.level == null) {
					continue;
				}
				if (client.screen == null) {
					client.setScreen(MapScreen.createForCurrentWorld());
				}
			}
		});
	}
}
