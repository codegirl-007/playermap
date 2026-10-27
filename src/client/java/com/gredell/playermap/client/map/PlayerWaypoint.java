package com.gredell.playermap.client.map;

import com.gredell.playermap.network.PlayerPosition;

import java.util.Optional;
import java.util.UUID;

public final class PlayerWaypoint {
	private static UUID targetUuid;
	private static String targetName;

	private PlayerWaypoint() {
	}

	public static void set(final PlayerPosition player) {
		targetUuid = player.uuid();
		targetName = player.name();
	}

	public static void clear() {
		targetUuid = null;
		targetName = null;
	}

	public static boolean isActive() {
		return targetUuid != null;
	}

	public static boolean targets(final UUID uuid) {
		return targetUuid != null && targetUuid.equals(uuid);
	}

	public static String cachedName() {
		return targetName;
	}

	public static Optional<PlayerPosition> resolve() {
		if (targetUuid == null) {
			return Optional.empty();
		}
		return ClientPlayerMapData.findByUuid(targetUuid);
	}
}
