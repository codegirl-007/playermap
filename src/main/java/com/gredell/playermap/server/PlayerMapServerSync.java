package com.gredell.playermap.server;

import com.gredell.playermap.network.PlayerMapNetworking;
import com.gredell.playermap.network.PlayerPosition;
import com.gredell.playermap.network.PlayerPositionsPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class PlayerMapServerSync {
	private static final int SYNC_INTERVAL_TICKS = 10;

	private PlayerMapServerSync() {
	}

	public static void register() {
		PlayerMapNetworking.registerPayloads();
		ServerTickEvents.END_SERVER_TICK.register(PlayerMapServerSync::onEndServerTick);
	}

	private static void onEndServerTick(MinecraftServer server) {
		if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
			return;
		}

		List<PlayerPosition> positions = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			positions.add(
				new PlayerPosition(
					player.getUUID(),
					player.getGameProfile().name(),
					player.level().dimension(),
					player.getX(),
					player.getY(),
					player.getZ()
				)
			);
		}

		PlayerPositionsPayload payload = new PlayerPositionsPayload(positions);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
