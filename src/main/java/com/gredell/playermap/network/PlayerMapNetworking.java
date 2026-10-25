package com.gredell.playermap.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PlayerMapNetworking {
	private static boolean registered;

	private PlayerMapNetworking() {
	}

	public static void registerPayloads() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.playS2C().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.CODEC);
	}
}
