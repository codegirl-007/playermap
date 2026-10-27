package com.gredell.playermap.client.map;

import com.gredell.playermap.network.PlayerPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientPlayerMapData {
	private static List<PlayerPosition> networkPlayers = List.of();

	private ClientPlayerMapData() {
	}

	public static void updateFromNetwork(final List<PlayerPosition> players) {
		networkPlayers = List.copyOf(players);
	}

	public static void clear() {
		networkPlayers = List.of();
	}

	public static Optional<PlayerPosition> findByUuid(final UUID uuid) {
		PlayerPosition fromNetwork = null;
		for (final PlayerPosition position : networkPlayers) {
			if (position.uuid().equals(uuid)) {
				fromNetwork = position;
			}
		}

		final ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			for (final AbstractClientPlayer player : level.players()) {
				if (player.getUUID().equals(uuid)) {
					return Optional.of(
						new PlayerPosition(
							player.getUUID(),
							player.getGameProfile().name(),
							level.dimension(),
							player.getX(),
							player.getY(),
							player.getZ()
						)
					);
				}
			}
		}

		return Optional.ofNullable(fromNetwork);
	}

	public static List<PlayerPosition> playersInDimension(final ResourceKey<Level> dimension) {
		final Map<UUID, PlayerPosition> merged = new LinkedHashMap<>();

		for (final PlayerPosition position : networkPlayers) {
			if (position.dimension().equals(dimension)) {
				merged.put(position.uuid(), position);
			}
		}

		final ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.dimension().equals(dimension)) {
			for (final AbstractClientPlayer player : level.players()) {
				merged.put(
					player.getUUID(),
					new PlayerPosition(
						player.getUUID(),
						player.getGameProfile().name(),
						dimension,
						player.getX(),
						player.getY(),
						player.getZ()
					)
				);
			}
		}

		return new ArrayList<>(merged.values());
	}
}
