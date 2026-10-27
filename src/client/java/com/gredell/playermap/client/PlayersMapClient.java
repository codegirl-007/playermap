package com.gredell.playermap.client;

import com.gredell.playermap.client.keybindings.map.OpenMapKey;
import com.gredell.playermap.client.keybindings.minimap.ToggleMinimapKey;
import com.gredell.playermap.client.keybindings.waypoint.ClearWaypointKey;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class PlayersMapClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PlayerMapNetworking.registerPayloads();
		MinimapOverlay.register();
		WaypointHudOverlay.register();
		OpenMapKey.register();
		ToggleMinimapKey.register();
		ClearWaypointKey.register();

		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE, (payload, context) -> {
			ClientPlayerMapData.updateFromNetwork(payload.players());
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPlayerMapData.clear();
			PlayerWaypoint.clear();
			ChunkSurfaceColors.clear();
			MapScreen.releaseSharedRenderer(client);
		});

		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			// a column just arrived, call capture to update the colors
			ChunkSurfaceColors.capture(chunk);

			// tell the minimap that the terrain has changed and redraw minimap
			MinimapOverlay.notifyTerrainChanged();
		});
	}
}
